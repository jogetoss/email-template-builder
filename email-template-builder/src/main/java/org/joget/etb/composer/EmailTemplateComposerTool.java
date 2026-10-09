package org.joget.etb.composer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.joget.ai.agent.core.AgentEventBus;
import org.joget.ai.agent.model.AgentException;
import org.joget.ai.agent.model.AgentLLM;
import org.joget.ai.agent.model.AgentToolAbstract;
import org.joget.ai.agent.model.AgentTraceEvent;
import org.joget.ai.agent.model.AgentTraceStatus;
import org.joget.ai.agent.model.Function;
import org.joget.ai.agent.model.Functions;
import org.joget.ai.agent.model.ToolExecution;
import org.joget.ai.agent.model.LLMConfig;
import org.joget.apps.app.model.AppDefinition;
import org.joget.commons.util.LogUtil;
import org.joget.etb.Activator;
import org.joget.etb.BlockCatalog;
import org.joget.etb.EmailTemplateBuilder;
import org.joget.etb.StarterCatalog;
import org.joget.etb.api.EmailBlock;
import org.joget.etb.render.BlockFactory;
import org.joget.etb.render.EmailRenderResult;
import org.joget.etb.render.EmailTemplateRenderer;
import org.joget.etb.render.MergeResolver;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The AI Composer's hands on an email template: read it, learn the blocks, and change a working copy one step at a
 * time (add, update, move, remove a block; use a starter or a ready-made component; set the subject and theme), then
 * check it with the email builder's own renderer. The chat receives the working copy after every successful step
 * and at the end.
 *
 * <p>State is per run, on the run's thread ({@link #isParallelSafe()} is false). Blocks and JSON go in as JSON
 * strings: a free-form object has no strict schema, and every provider can pass a string.</p>
 */
public class EmailTemplateComposerTool extends AgentToolAbstract {

    /** The template being edited (the chat's JSON), changed in place. */
    static final ThreadLocal<JSONObject> CURRENT = new ThreadLocal<>();
    /** Set once the template has changed: the chat previews it after each step. */
    static final ThreadLocal<JSONObject> RESULT = new ThreadLocal<>();
    /** How many changes this run has made: the chat previews only after a step that changed something. */
    static final ThreadLocal<int[]> CHANGES = new ThreadLocal<>();
    /** The app of the template (App Resources, datalists for the check). */
    static final ThreadLocal<AppDefinition> APP = new ThreadLocal<>();
    /** This thread's run, for aborting. */
    static final ThreadLocal<String> RUN = new ThreadLocal<>();
    /**
     * Sample values for the template variables (the model's, and a used starter's): kept out of the template and sent
     * beside it as {@link #TEST_VALUES}, which the chat's bridge puts into the builder's Test data.
     */
    static final ThreadLocal<Map<String, String>> SAMPLES = new ThreadLocal<>();
    /** The key beside the template, in what the chat receives, that carries {@link #SAMPLES}. */
    static final String TEST_VALUES = "etbTestValues";
    /** Runs the user stopped: the next tool call ends them. */
    static final Set<String> ABORTED = ConcurrentHashMap.newKeySet();

    /** Where block classes come from: Joget's plugins (tests use plain reflection). */
    static BlockFactory blocks = BlockFactory.plugins();

    static final String ROOT = "org.joget.etb.EmailTemplateBuilder";
    static final String LIB = "org.joget.etb.lib.";

    public String getName() {
        return "EmailTemplateComposerTool";
    }

    public String getVersion() {
        return Activator.VERSION;
    }

    public String getLabel() {
        return "Email Template Composer";
    }

    public String getDescription() {
        return "Creates and changes Email Template Builder templates.";
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return "[]";
    }

    public String getIcon() {
        return "<i class=\"fas fa-envelope-open-text\"></i>";
    }

    public String getInfoTemplate() {
        return "<div><dl><dt>Name</dt><dd>EmailTemplateComposerTool</dd><dt>Description</dt><dd>" + getDescription() + "</dd></dl></div>";
    }

    /** Only the email composer uses it: not offered in agent-builder's palette. */
    @Override
    public Boolean isHidden() {
        return true;
    }

    @Override
    public boolean isParallelSafe() {
        return false;
    }

    public Functions getFunctions() {
        Functions f = new Functions();
        f.add(fn("get_template", "The template being edited: its properties (subject, preheader, theme, variables) and its blocks, each with an id."));
        f.add(fn("get_block_catalog", "Every block type: class name, label, category, where it may be placed, and whether it holds other blocks."));
        Function details = fn("get_block_details", "The properties of some block types: name, label, type, allowed values and default.");
        details.addParameter("classNames", Function.ParameterType.STRING, "Comma-separated block class names or short names, e.g. \"TextBlock, ButtonBlock\".");
        f.add(details);
        f.add(fn("list_starters", "The ready-made starter templates: id, label, category, description."));
        Function starter = fn("use_starter", "Replace the whole design with a starter template (the template's id and name are kept): only for a new email. Then replace its placeholder content and adapt it.");
        starter.addParameter("id", Function.ParameterType.STRING, "The starter's id from list_starters.");
        f.add(starter);
        f.add(fn("list_components", "The ready-made components (steps, progress bars, facts lists, timelines, stat tiles, callouts, headers, footers...)."));
        Function component = fn("insert_component", "Insert a ready-made component. Without parentId it goes at the end of the card (a logo at the top, footers at the end).");
        component.addParameter("id", Function.ParameterType.STRING, "The component's id from list_components.");
        component.addParameter("parentId", Function.ParameterType.STRING, "Optional: the id of a Column, Repeat or Outside block to put it in.", false);
        component.addParameter("index", Function.ParameterType.INTEGER, "Optional: its position among the parent's blocks (0 = first). Default: last.", false);
        f.add(component);
        Function props = fn("set_template_properties", "Merge into the template's own properties: subject, preheader, theme (backgroundColor, contentBackground, textColor, linkColor, fontFamily, width, contentBorderRadius, contentShadow) and variables ([{\"name\",\"description\",\"sample\"}], replacing the list).");
        props.addParameter("properties", Function.ParameterType.STRING, "A JSON object, e.g. {\"subject\":\"Your order #form.order.id#\",\"linkColor\":\"#2563eb\"}.");
        f.add(props);
        Function add = fn("add_block", "Add a block (with its child blocks, if any). Without parentId it goes on the email itself.");
        add.addParameter("block", Function.ParameterType.STRING, "A JSON object {\"className\":\"org.joget.etb.lib.TextBlock\",\"properties\":{...},\"elements\":[...]}. Short class names are accepted.");
        add.addParameter("parentId", Function.ParameterType.STRING, "Optional: the id of the Row (for a Column), Column, Repeat or Outside block to put it in.", false);
        add.addParameter("index", Function.ParameterType.INTEGER, "Optional: its position among the parent's blocks (0 = first). Default: last.", false);
        f.add(add);
        Function update = fn("update_block", "Merge properties into one block; a property set to null is removed.");
        update.addParameter("id", Function.ParameterType.STRING, "The block's id.");
        update.addParameter("properties", Function.ParameterType.STRING, "A JSON object of the properties to change.");
        f.add(update);
        Function remove = fn("remove_block", "Remove one block and everything in it.");
        remove.addParameter("id", Function.ParameterType.STRING, "The block's id.");
        f.add(remove);
        Function move = fn("move_block", "Move a block to another place.");
        move.addParameter("id", Function.ParameterType.STRING, "The block's id.");
        move.addParameter("parentId", Function.ParameterType.STRING, "Optional: the new parent's id; empty for the email itself.", false);
        move.addParameter("index", Function.ParameterType.INTEGER, "Optional: its new position (0 = first). Default: last.", false);
        f.add(move);
        Function body = fn("set_blocks", "Replace all of the email's blocks at once, only to lay out a whole new email when no starter fits. The template's properties are kept.");
        body.addParameter("blocks", Function.ParameterType.STRING, "A JSON array of blocks, each {\"className\",\"properties\",\"elements\"}.");
        f.add(body);
        f.add(fn("check_email", "Render the template with the email builder and report problems: blocks in the wrong place, unknown blocks, blocked links, buttons without an address, unfilled {var.} fields, size, and layouts that won't fit a phone. Call it before you finish."));
        return f;
    }

    private static Function fn(String name, String description) {
        return new Function(name, description, false);
    }

    /**
     * A step of the run, as the chat shows it: agent-builder traces its own tools by compile-time weaving, which never
     * reaches this plugin's classes, so this tool reports its steps itself (running, then done or failed). A finished
     * change is also what makes the chat preview the working copy on the canvas.
     */
    public String execute(AgentLLM llm, LLMConfig config, ToolExecution call) throws AgentException {
        String run = RUN.get();
        if (run != null && ABORTED.contains(run)) {
            throw new AgentException("Stopped by the user.");
        }
        JSONObject args = call.getArguments() == null ? new JSONObject() : call.getArguments();
        AgentTraceEvent step = step(run, call.getName(), args);
        String result = run(call, args);
        if (step != null) {
            AgentTraceEvent done = new AgentTraceEvent(step);
            done.setStatus(new JSONObject(result).optBoolean("success") ? AgentTraceStatus.SUCCESS : AgentTraceStatus.FAILED);
            done.setResponse(traceResponse(result));
            publish(done);
        }
        return result;
    }

    /** Longest result a step's trace carries whole. */
    static final int TRACE_MAX = 2000;

    /**
     * A result as the step's trace carries it. The trace embeds it as JSON (AgentTraceEvent writes it unquoted), so a
     * long one is replaced by a short JSON object with the start of it as text, never cut mid-object: a cut one is not
     * JSON, and the chat would lose the step and its preview.
     */
    static String traceResponse(String result) {
        if (result == null || result.length() <= TRACE_MAX) {
            return result;
        }
        boolean ok;
        try {
            ok = new JSONObject(result).optBoolean("success");
        } catch (org.json.JSONException e) {
            ok = false;
        }
        return new JSONObject().put("success", ok).put("truncated", true).put("length", result.length())
                .put("preview", result.substring(0, TRACE_MAX)).toString();
    }

    /** The changes this thread's run has made so far. */
    static int changes() {
        int[] n = CHANGES.get();
        return n == null ? 0 : n[0];
    }

    private AgentTraceEvent step(String run, String function, JSONObject args) {
        if (run == null) {
            return null;
        }
        AgentTraceEvent e = new AgentTraceEvent();
        e.setRunId(run);
        e.setNodeId(UUID.randomUUID().toString());
        e.setType("Tool");
        e.setLabel(stepLabel(function, args));
        e.setPersonalizedName(stepLabel(function, args));
        e.setStatus(AgentTraceStatus.RUNNING);
        e.setRequest(args.toString());
        e.setCreatedAt(new java.util.Date());
        publish(e);
        return e;
    }

    private void publish(AgentTraceEvent e) {
        try {
            AgentEventBus.publish(e.getRunId(), e.toString());
        } catch (RuntimeException ex) {
            LogUtil.debug(getClassName(), "Step not reported: " + ex.getMessage());
        }
    }

    static String stepLabel(String function, JSONObject args) {
        switch (function) {
            case "get_template": return "Reading the template";
            case "get_block_catalog": return "Looking up the blocks";
            case "get_block_details": return "Reading block properties";
            case "list_starters": return "Browsing the starter templates";
            case "use_starter": return "Starting from the \"" + args.optString("id") + "\" starter";
            case "list_components": return "Browsing the components";
            case "insert_component": return "Adding the \"" + args.optString("id") + "\" component";
            case "set_template_properties": return "Setting the subject and theme";
            case "add_block": return "Adding a block";
            case "update_block": return "Updating a block";
            case "remove_block": return "Removing a block";
            case "move_block": return "Moving a block";
            case "set_blocks": return "Laying out the email";
            case "check_email": return "Checking the email";
            default: return function;
        }
    }

    private String run(ToolExecution call, JSONObject args) {
        try {
            JSONObject template = CURRENT.get();
            if (template == null) {
                return error("No template is open.");
            }
            switch (call.getName()) {
                case "get_template":
                    JSONObject read = ok().put("template", template);
                    if (!samples().isEmpty()) {
                        read.put("variableSamples", new JSONObject(samples()));
                    }
                    return read.toString();
                case "get_block_catalog":
                    return ok().put("blocks", BlockCatalog.summary(EmailTemplateBuilder.installedBlocks())).toString();
                case "get_block_details":
                    return ok().put("blocks", BlockCatalog.details(EmailTemplateBuilder.installedBlocks(), names(args.optString("classNames")))).toString();
                case "list_starters":
                    return ok().put("starters", StarterCatalog.starters().indexJson().getJSONArray("starters")).toString();
                case "use_starter":
                    return useStarter(template, args.optString("id"));
                case "list_components":
                    return ok().put("components", StarterCatalog.components().indexJson().getJSONArray("components")).toString();
                case "insert_component":
                    return insertComponent(template, args);
                case "set_template_properties":
                    return setProperties(template, new JSONObject(args.optString("properties", "{}")));
                case "add_block":
                    return add(template, block(new JSONObject(args.optString("block", "{}"))), args.optString("parentId", ""), index(args));
                case "update_block":
                    return update(template, args.optString("id"), new JSONObject(args.optString("properties", "{}")));
                case "remove_block":
                    return remove(template, args.optString("id"));
                case "move_block":
                    return move(template, args.optString("id"), args.optString("parentId", ""), index(args));
                case "set_blocks":
                    return setBlocks(template, new JSONArray(args.optString("blocks", "[]")));
                case "check_email":
                    return check(template);
                default:
                    return error("Unknown function " + call.getName() + ".");
            }
        } catch (org.json.JSONException e) {
            return error("That is not valid JSON: " + e.getMessage() + ". Pass blocks and properties as JSON strings.");
        } catch (RuntimeException e) {
            LogUtil.error(getClassName(), e, "Email composer tool " + call.getName() + " failed");
            return error(e.getMessage());
        }
    }

    // ---------- changes ----------

    private String useStarter(JSONObject template, String id) {
        String json = StarterCatalog.starters().load(id);
        if (json == null) {
            return error("No starter \"" + id + "\". Call list_starters.");
        }
        JSONObject starter = new JSONObject(json);
        JSONObject props = template.optJSONObject("properties");
        JSONObject starterProps = starter.optJSONObject("properties") == null ? new JSONObject() : starter.getJSONObject("properties");
        for (String keep : new String[] {"id", "name", "description"}) {
            if (props != null && props.has(keep)) {
                starterProps.put(keep, props.get(keep));
            }
        }
        template.put("properties", starterProps);
        template.put("elements", ids(starter.optJSONArray("elements") == null ? new JSONArray() : starter.getJSONArray("elements")));
        // as picking it in the gallery does: its variables come with the gallery's sample values
        Map<String, String> gallery = StarterCatalog.starters().variableSamples();
        for (String name : EmailTemplateRenderer.declaredVariables(starterProps).keySet()) {
            if (gallery.containsKey(name)) {
                samples().put(name, gallery.get(name));
            }
        }
        return changed("Using the \"" + id + "\" starter.", template);
    }

    private String insertComponent(JSONObject template, JSONObject args) {
        String json = StarterCatalog.components().load(args.optString("id"));
        if (json == null) {
            return error("No component \"" + args.optString("id") + "\". Call list_components.");
        }
        JSONObject component = new JSONObject(json);
        JSONObject element = component.getJSONObject("element");
        String parentId = args.optString("parentId", "");
        if (parentId.isEmpty() && args.opt("index") == null) {
            JSONArray elements = elements(template);
            String place = component.optString("place", "");
            int at = elements.length();
            if ("first".equals(place)) {
                at = 0;
            } else if (!"last".equals(place)) {
                while (at > 0 && elements.getJSONObject(at - 1).optString("className").endsWith("OutsideBlock")) {
                    at--;
                }
            }
            return add(template, element, "", at);
        }
        return add(template, element, parentId, index(args));
    }

    private String setProperties(JSONObject template, JSONObject changes) {
        JSONObject props = template.optJSONObject("properties");
        if (props == null) {
            props = new JSONObject();
            template.put("properties", props);
        }
        for (String key : changes.keySet()) {
            if ("id".equals(key)) {
                continue;
            }
            if (changes.isNull(key)) {
                props.remove(key);
            } else {
                props.put(key, "variables".equals(key) && changes.get(key) instanceof JSONArray ? withoutSamples(changes.getJSONArray(key)) : changes.get(key));
            }
        }
        return changed("Template properties updated.", template);
    }

    /** The variables as the template keeps them (name, description); their samples go to {@link #SAMPLES}. */
    private static JSONArray withoutSamples(JSONArray variables) {
        for (int i = 0; i < variables.length(); i++) {
            JSONObject v = variables.optJSONObject(i);
            if (v == null) {
                continue;
            }
            Object sample = v.remove("sample");
            v.remove("previewValue");
            String name = v.optString("name", "").trim();
            if (sample != null && !name.isEmpty() && !sample.toString().trim().isEmpty()) {
                samples().put(name, sample.toString());
            }
        }
        return variables;
    }

    String add(JSONObject template, JSONObject block, String parentId, int index) {
        if (!parentId.isEmpty() && find(template, parentId) == null) {
            return error("No block with id \"" + parentId + "\" to put it in. Call get_template for the ids.");
        }
        String problem = placement(block, parentId.isEmpty() ? null : find(template, parentId));
        if (problem != null) {
            return error(problem);
        }
        JSONArray into = parentId.isEmpty() ? elements(template) : elements(find(template, parentId));
        ids(new JSONArray().put(block));
        insert(into, block, index);
        return changed("Added " + label(block) + " with id " + block.getJSONObject("properties").getString("id") + ".", template);
    }

    private String update(JSONObject template, String id, JSONObject changes) {
        JSONObject block = find(template, id);
        if (block == null) {
            return error("No block with id \"" + id + "\". Call get_template for the ids.");
        }
        JSONObject props = block.optJSONObject("properties");
        if (props == null) {
            props = new JSONObject();
            block.put("properties", props);
        }
        for (String key : changes.keySet()) {
            if ("id".equals(key)) {
                continue;
            }
            if (changes.isNull(key)) {
                props.remove(key);
            } else {
                props.put(key, changes.get(key));
            }
        }
        return changed("Updated " + label(block) + ".", template);
    }

    private String remove(JSONObject template, String id) {
        JSONArray siblings = parentList(template, id);
        if (siblings == null) {
            return error("No block with id \"" + id + "\".");
        }
        for (int i = 0; i < siblings.length(); i++) {
            if (id.equals(idOf(siblings.getJSONObject(i)))) {
                String l = label(siblings.getJSONObject(i));
                siblings.remove(i);
                return changed("Removed " + l + ".", template);
            }
        }
        return error("No block with id \"" + id + "\".");
    }

    private String move(JSONObject template, String id, String parentId, int index) {
        JSONObject block = find(template, id);
        if (block == null) {
            return error("No block with id \"" + id + "\".");
        }
        if (!parentId.isEmpty() && (id.equals(parentId) || find(block, parentId) != null)) {
            return error("A block can't go inside itself.");
        }
        if (!parentId.isEmpty() && find(template, parentId) == null) {
            return error("No block with id \"" + parentId + "\" to move it into.");
        }
        String problem = placement(block, parentId.isEmpty() ? null : find(template, parentId));
        if (problem != null) {
            return error(problem);
        }
        remove(template, id);
        insert(parentId.isEmpty() ? elements(template) : elements(find(template, parentId)), block, index);
        return changed("Moved " + label(block) + ".", template);
    }

    private String setBlocks(JSONObject template, JSONArray blocks) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject b = block(blocks.getJSONObject(i));
            String problem = placement(b, null);
            if (problem != null) {
                return error("Block " + (i + 1) + ": " + problem);
            }
            out.put(b);
        }
        template.put("elements", ids(out));
        return changed("The email now has " + out.length() + " top-level blocks.", template);
    }

    /** Render with the email builder (builder mode: sample rows show, merge fields left as written) and list problems. */
    private String check(JSONObject template) {
        JSONObject props = template.optJSONObject("properties") == null ? new JSONObject() : template.getJSONObject("properties");
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        for (String name : EmailTemplateRenderer.declaredVariables(props).keySet()) {
            // its sample, else URL-shaped: a variable is often a link ({var.trackingUrl}); a plain word would be reported as a blocked link
            vars.put(name, samples().getOrDefault(name, "https://example.com/" + name));
        }
        try {
            EmailRenderResult r = new EmailTemplateRenderer(blocks, MergeResolver.none(), false, null, APP.get())
                    .withTestVariables(vars).render(template.toString());
            List<String> problems = new ArrayList<>();
            for (String w : r.getWarnings()) {
                // not problems of the design: sample data, and images the builder can't reach from here
                // nor what the builder shows for a field with no value here (Steps, Progress bars): fine in a send
                if (!w.contains("sample rows") && !w.contains("App Resource") && !w.contains("the builder shows")) {
                    problems.add(w);
                }
            }
            if (props.optString("subject").trim().isEmpty()) {
                problems.add("The template has no subject (set_template_properties).");
            }
            problems.addAll(phoneProblems(template));
            return ok().put("problems", new JSONArray(problems)).put("sizeKb", r.getSizeBytes() / 1024)
                    .put("summary", problems.isEmpty() ? "No problems found." : problems.size() + " problem(s) to fix.").toString();
        } catch (RuntimeException e) {
            return error("The email does not render: " + e.getMessage());
        }
    }

    // ---------- phones ----------

    /** Text width on a small phone (360px) inside 32px sides; a block's own side padding replaces the 32px. */
    static final int PHONE = 360;
    static final java.util.regex.Pattern LETTER_SPACING = java.util.regex.Pattern.compile("letter-spacing:\\s*(-?\\d{1,2})px");

    /**
     * Layouts that won't fit a phone, the ones the renderer can't see: a big heading whose longest word is wider than
     * the phone, several columns of text kept side by side, and Stat tiles with long numbers kept side by side.
     * Estimates, kept conservative: a false alarm would make the AI change a good design.
     */
    static List<String> phoneProblems(JSONObject template) {
        List<String> out = new ArrayList<>();
        phone(template.optJSONArray("elements"), true, out);
        return out;
    }

    private static void phone(JSONArray blocks, boolean fullWidth, List<String> out) {
        for (int i = 0; blocks != null && i < blocks.length(); i++) {
            JSONObject b = blocks.optJSONObject(i);
            if (b == null) {
                continue;
            }
            String cls = b.optString("className");
            JSONObject p = b.optJSONObject("properties") == null ? new JSONObject() : b.getJSONObject("properties");
            boolean kept = "true".equals(p.optString("keepSideBySide"));
            if (cls.endsWith(".TextBlock") && fullWidth) {
                heading(p.optString("html"), px(p.optString("fontSize"), 16), p.optString("padding"), "A Text block", out);
            } else if (cls.endsWith(".HeroBlock")) {
                heading(p.optString("headline"), px(p.optString("headlineSize"), 32), p.optString("padding"), "The Hero headline", out);
            } else if (cls.endsWith(".RowBlock") && kept) {
                JSONArray cols = b.optJSONArray("elements");
                int n = cols == null ? 0 : cols.length();
                if (n >= 3 && longestText(cols) > 24) {
                    out.add("On phones: a Row keeps " + n + " columns of text side by side, each about " + (PHONE - 64) / n
                            + "px wide. Let it stack (keepSideBySide off), or keep only short content side by side.");
                }
            } else if (cls.endsWith(".StatTilesBlock") && kept) {
                JSONArray tiles = p.optJSONArray("tiles");
                for (int t = 0; tiles != null && t < tiles.length(); t++) {
                    String number = plain(tiles.optJSONObject(t) == null ? "" : tiles.getJSONObject(t).optString("number"));
                    if (tiles.length() >= 3 && number.length() > 4 && !number.contains("{") && !number.contains("#")) {
                        out.add("On phones: Stat tiles keep " + tiles.length() + " tiles side by side, and \"" + number
                                + "\" won't fit. Let them stack (keepSideBySide off).");
                        break;
                    }
                }
            }
            // a Row's columns are full width on a phone once they stack
            boolean childFull = cls.endsWith(".RowBlock") ? !kept : cls.endsWith(".ColumnBlock") ? fullWidth : fullWidth && !cls.endsWith(".RepeatBlock");
            phone(b.optJSONArray("elements"), childFull, out);
        }
    }

    /** Text with its own size: an element whose style sets font-size (not nested), e.g. a drop cap or a big number. */
    static final java.util.regex.Pattern SIZED = java.util.regex.Pattern.compile("(?is)<(\\w+)\\b[^>]*?font-size:\\s*(\\d{1,3})px[^>]*>(.*?)</\\1>");

    /** A heading whose longest word, at its size, is wider than a phone's text column. */
    private static void heading(String html, int blockSize, String padding, String what, List<String> out) {
        int[] sides = sides(padding);
        int room = PHONE - sides[0] - sides[1];
        java.util.regex.Matcher m = SIZED.matcher(html);
        StringBuilder rest = new StringBuilder();
        int last = 0;
        while (m.find()) {
            if (fits(m.group(3), Integer.parseInt(m.group(2)), spacing(m.group()), room, what, out)) {
                rest.append(html, last, m.start()).append(' ');
                last = m.end();
            } else {
                return;
            }
        }
        rest.append(html.substring(last));
        fits(rest.toString(), blockSize, spacing(html), room, what, out);
    }

    private static int spacing(String html) {
        java.util.regex.Matcher ls = LETTER_SPACING.matcher(html);
        return ls.find() ? Integer.parseInt(ls.group(1)) : 0;
    }

    /** False (with a problem) when the longest word at this size is wider than the room. */
    private static boolean fits(String html, int size, int spacing, int room, String what, List<String> out) {
        if (size < 30) {
            return true;
        }
        String word = "";
        for (String w : plain(html).split("\\s+")) {
            if (w.length() > word.length() && !w.contains("{") && !w.contains("#")) {
                word = w;
            }
        }
        // bold sans-serif: capitals about 0.68em wide, other characters about 0.56em
        double width = 0;
        for (char c : word.toCharArray()) {
            width += size * (Character.isUpperCase(c) ? 0.68 : 0.56) + spacing;
        }
        if (width <= room) {
            return true;
        }
        out.add("On phones: " + what + " at " + size + "px won't fit \"" + word + "\" on one line (about " + Math.round(width) + "px, the phone has "
                + room + "px), so the word breaks. Use a smaller size, about " + (int) Math.floor(size * room / width) + "px.");
        return false;
    }

    private static int longestText(JSONArray columns) {
        int longest = 0;
        for (int i = 0; i < columns.length(); i++) {
            JSONArray inside = columns.optJSONObject(i) == null ? null : columns.getJSONObject(i).optJSONArray("elements");
            for (int j = 0; inside != null && j < inside.length(); j++) {
                JSONObject b = inside.optJSONObject(j);
                if (b != null && b.optString("className").endsWith(".TextBlock") && b.optJSONObject("properties") != null) {
                    longest = Math.max(longest, plain(b.getJSONObject("properties").optString("html")).length());
                }
            }
        }
        return longest;
    }

    /** Visible text: tags and line breaks become spaces, an entity counts as one character. */
    static String plain(String html) {
        return html.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replaceAll("&[#a-zA-Z0-9]{1,10};", "x")
                .replaceAll("\\s+", " ").trim();
    }

    private static int px(String value, int fallback) {
        String v = value == null ? "" : value.trim().replace("px", "");
        return v.matches("\\d{1,3}") ? Integer.parseInt(v) : fallback;
    }

    /** Left and right padding of a CSS-like padding value (default 32px sides). */
    private static int[] sides(String padding) {
        String[] v = (padding == null ? "" : padding.trim().replace("px", "")).split("\\s+");
        try {
            switch (v.length) {
                case 1: return new int[] {Integer.parseInt(v[0]), Integer.parseInt(v[0])};
                case 2: case 3: return new int[] {Integer.parseInt(v[1]), Integer.parseInt(v[1])};
                case 4: return new int[] {Integer.parseInt(v[3]), Integer.parseInt(v[1])};
                default: return new int[] {32, 32};
            }
        } catch (NumberFormatException e) {
            return new int[] {32, 32};
        }
    }

    // ---------- the tree ----------

    /** Short class names become full ones; missing properties and elements are added. */
    static JSONObject block(JSONObject b) {
        String cls = b.optString("className", "");
        if (!cls.isEmpty() && !cls.contains(".")) {
            b.put("className", LIB + cls);
        }
        if (b.optJSONObject("properties") == null) {
            b.put("properties", new JSONObject());
        }
        JSONArray children = b.optJSONArray("elements");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                block(children.getJSONObject(i));
            }
        }
        return b;
    }

    /** Why {@code block} can't go into {@code parent} (null: on the email), or null when it can. */
    static String placement(JSONObject block, JSONObject parent) {
        EmailBlock b = blocks.create(block.optString("className"));
        if (b == null) {
            return "Unknown block \"" + block.optString("className") + "\". Call get_block_catalog.";
        }
        String where = "template";
        if (parent != null) {
            EmailBlock p = blocks.create(parent.optString("className"));
            where = p == null || p.getContainerType() == null ? null : p.getContainerType();
            if (where == null) {
                return label(parent) + " can't hold other blocks.";
            }
        }
        if (!Arrays.asList(b.getAllowedParent().split(",")).contains(where)) {
            return b.getLabel() + " can't go " + ("template".equals(where) ? "on the email itself" : "in a " + where)
                    + "; it may go in: " + b.getAllowedParent().replace("template", "the email itself") + ".";
        }
        JSONArray children = block.optJSONArray("elements");
        for (int i = 0; children != null && i < children.length(); i++) {
            String problem = placement(block(children.getJSONObject(i)), block);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    /** Every block gets an id (the chat finds the selected block by it), recursively. */
    static JSONArray ids(JSONArray blocks) {
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject b = blocks.getJSONObject(i);
            JSONObject props = b.optJSONObject("properties");
            if (props == null) {
                props = new JSONObject();
                b.put("properties", props);
            }
            if (props.optString("id").isEmpty()) {
                props.put("id", UUID.randomUUID().toString().replace("-", "").toUpperCase());
            }
            if (b.optJSONArray("elements") != null) {
                ids(b.getJSONArray("elements"));
            }
        }
        return blocks;
    }

    static JSONArray elements(JSONObject node) {
        JSONArray a = node.optJSONArray("elements");
        if (a == null) {
            a = new JSONArray();
            node.put("elements", a);
        }
        return a;
    }

    static String idOf(JSONObject block) {
        JSONObject p = block.optJSONObject("properties");
        return p == null ? "" : p.optString("id", "");
    }

    /** The block with this id under {@code node}, or null. */
    static JSONObject find(JSONObject node, String id) {
        JSONArray children = node.optJSONArray("elements");
        for (int i = 0; children != null && i < children.length(); i++) {
            JSONObject c = children.getJSONObject(i);
            if (id.equals(idOf(c))) {
                return c;
            }
            JSONObject deeper = find(c, id);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    /** The list that holds the block with this id, or null. */
    static JSONArray parentList(JSONObject node, String id) {
        JSONArray children = node.optJSONArray("elements");
        for (int i = 0; children != null && i < children.length(); i++) {
            JSONObject c = children.getJSONObject(i);
            if (id.equals(idOf(c))) {
                return children;
            }
            JSONArray deeper = parentList(c, id);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    static void insert(JSONArray list, JSONObject block, int index) {
        int at = index < 0 || index > list.length() ? list.length() : index;
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            items.add(list.get(i));
        }
        items.add(at, block);
        for (int i = list.length() - 1; i >= 0; i--) {
            list.remove(i);
        }
        items.forEach(list::put);
    }

    static String label(JSONObject block) {
        String cls = block.optString("className", "block");
        return cls.substring(cls.lastIndexOf('.') + 1).replace("Block", "");
    }

    private static int index(JSONObject args) {
        return args.opt("index") == null || args.isNull("index") ? -1 : args.optInt("index", -1);
    }

    private static List<String> names(String csv) {
        List<String> out = new ArrayList<>();
        for (String n : csv.split(",")) {
            String t = n.trim();
            if (!t.isEmpty()) {
                out.add(t.contains(".") ? t : LIB + t);
            }
        }
        return out;
    }

    /** This run's sample values (a map that outlives no run: the service sets and removes it). */
    static Map<String, String> samples() {
        Map<String, String> m = SAMPLES.get();
        if (m == null) {
            m = new LinkedHashMap<>();
            SAMPLES.set(m);
        }
        return m;
    }

    /** What the chat loads into the builder: the template, and beside it the sample values for Test data, if any. */
    static JSONObject forBuilder(JSONObject template) {
        if (samples().isEmpty()) {
            return template;
        }
        JSONObject out = new JSONObject(template.toString());
        out.put(TEST_VALUES, new JSONObject(samples()));
        return out;
    }

    private static String changed(String message, JSONObject template) {
        RESULT.set(template);
        int[] n = CHANGES.get();
        if (n != null) {
            n[0]++;
        }
        return ok().put("message", message).toString();
    }

    private static JSONObject ok() {
        return new JSONObject().put("success", true);
    }

    private static String error(String message) {
        return new JSONObject().put("success", false).put("error", message).toString();
    }
}
