package org.joget.etb;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.joget.apps.app.model.BuilderDefinition;
import org.joget.apps.app.model.CustomBuilderAbstract;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryManager;
import org.joget.etb.api.EmailBlock;
import org.joget.etb.lib.SendTemplatedEmailTool;
import org.joget.etb.lib.RepeatBlock;
import org.joget.etb.render.BlockFactory;
import org.joget.etb.render.EmailRenderResult;
import org.joget.etb.render.EmailTemplateRenderer;
import org.joget.etb.render.RecordResolver;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The Email Template builder, on the DX 9 CustomBuilder.Builder canvas (the one behind the Form and
 * Userview builders). Every block on the canvas is compiled by the same mjml4j renderer as the sent
 * email; Text blocks use the framework's built-in inline editor.
 */
public class EmailTemplateBuilder extends CustomBuilderAbstract implements PluginWebSupport {

    public static final String OBJECT_NAME = "emailtemplate";

    /**
     * Cache-buster for the builder JS. Joget passes an empty buildNumber to getBuilderJS, so the URL would
     * never change between plugin uploads and browsers keep running the old script. This changes whenever
     * the bundle is (re)installed, because the class is loaded again.
     */
    static final String BUILD_STAMP = Long.toString(System.currentTimeMillis(), 36);

    public String getName() {
        return "Email Template Builder";
    }

    public String getVersion() {
        return Activator.VERSION;
    }

    public String getDescription() {
        return "Design reusable email templates. Blocks compile to Outlook-safe, responsive email HTML.";
    }

    public String getLabel() {
        return "Email Template";
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getObjectName() {
        return OBJECT_NAME;
    }

    public String getObjectLabel() {
        return "Email Template";
    }

    public String getIdPrefix() {
        return "ET-";
    }

    public String getIcon() {
        return "far fa-envelope";
    }

    public String getColor() {
        return "#6e9f4b";
    }

    /** The email's look (colours, card radius and shadow, font, width): edited on the canvas by selecting the email itself. */
    public String getThemeOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/emailTemplateTheme.json", null, true, Activator.MESSAGE_PATH);
    }

    /**
     * Preview's bars: warnings (yellow), then the data blocks that need attention (blue: found nothing, or hit the
     * limit; one that simply works says nothing). Empty when there is nothing to say. Every line is HTML-escaped.
     */
    static String previewBars(List<String> warnings, List<EmailRenderResult.DataNote> notes) {
        StringBuilder out = new StringBuilder();
        if (!warnings.isEmpty()) {
            out.append("<div style=\"background:#fff3cd;color:#664d03;font:13px Arial,sans-serif;padding:8px 16px;\">");
            for (String w : warnings) {
                out.append(StringUtil.escapeString(w, StringUtil.TYPE_HTML, null)).append("<br>");
            }
            out.append("</div>");
        }
        StringBuilder blue = new StringBuilder();
        for (EmailRenderResult.DataNote n : notes) {
            if (n.needsAttention()) {
                blue.append(StringUtil.escapeString(n.sentence(), StringUtil.TYPE_HTML, null)).append("<br>");
            }
        }
        if (blue.length() > 0) {
            out.append("<div style=\"background:#e8f1fb;color:#1e3a5f;font:13px Arial,sans-serif;padding:8px 16px;\">").append(blue).append("</div>");
        }
        return out.toString();
    }

    /** {@code insert} right after the opening body tag (plain string insertion: no regex replacement syntax to escape). */
    static String afterBody(String html, String insert) {
        if (insert.isEmpty()) {
            return html;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<body[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? html.substring(0, m.end()) + insert + html.substring(m.end()) : insert + html;
    }

    /** Template settings (name, description, subject, preheader), edited with the builder's Settings button. */
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/emailTemplateBuilder.json", null, true, Activator.MESSAGE_PATH);
    }

    public String getBuilderConfig() {
        return AppUtil.readPluginResource(getClassName(), "/properties/emailTemplateBuilderConfig.json", null, false, Activator.MESSAGE_PATH);
    }

    public String getResourceBundlePath() {
        return Activator.MESSAGE_PATH;
    }

    public String getBuilderJS(String contextPath, String buildNumber) {
        return "<script type=\"text/javascript\" src=\"" + contextPath + "/plugin/" + getClassName()
                + "/emailtemplatebuilder.js?build=" + getVersion() + "-" + BUILD_STAMP + (buildNumber == null ? "" : buildNumber) + "\"></script>";
    }

    public String getBuilderCSS(String contextPath, String buildNumber) {
        return "";
    }

    public String getBuilderHTML(BuilderDefinition def, String json, HttpServletRequest request, HttpServletResponse response) {
        return "";
    }

    @Override
    public String createNewJSON(String id, String name, String description, BuilderDefinition copyDef) {
        if (copyDef != null) {
            JSONObject obj = new JSONObject(copyDef.getJson());
            obj.getJSONObject("properties").put("id", id).put("name", name).put("description", description);
            return obj.toString();
        }
        // new templates start blank; designs are chosen (and changed) in the builder's Templates gallery
        JSONObject obj = new JSONObject(StarterCatalog.starters().load(StarterCatalog.DEFAULT_ID));
        obj.getJSONObject("properties").put("id", id).put("name", name).put("description", description == null ? "" : description);
        return obj.toString();
    }

    /** The builder's Preview tab: the real email, merge fields resolved for the current user. */
    public void builderPreview(String json, HttpServletRequest request, HttpServletResponse response) {
        try {
            response.setContentType("text/html; charset=utf-8");
            EmailRenderResult result = EmailTemplateService.renderForTest(json, AppUtil.getCurrentAppDefinition(), testRecordId(request), request.getContextPath(), testVariables(request));
            // previewViewInit sets iframe.contentWindow.UI.base; this page is builder-only, never a sent email
            String html = result.getHtml().replaceFirst("<head>", "<head><script>window.UI = window.UI || {};</script>");
            List<String> warnings = new ArrayList<>();
            for (String w : result.getWarnings()) {
                // the address the builder is open at is usually the right one
                warnings.add(w.contains("Public server address") ? w + " You are using " + serverAddress(request) + "." : w);
            }
            html = afterBody(html, previewBars(warnings, result.getNotes()));
            response.getWriter().write(html);
        } catch (Exception ex) {
            LogUtil.error(getClassName(), ex, "Email template preview failed");
            response.setContentType("text/html; charset=utf-8");
            try {
                response.getWriter().write("<div style=\"color:#c00;font:14px Arial,sans-serif;padding:16px;\">Preview failed: "
                        + StringUtil.escapeString(ex.getMessage(), StringUtil.TYPE_HTML, null) + "</div>");
            } catch (IOException ignored) {
                // nothing more to report
            }
        }
    }

    static String serverAddress(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80) || ("https".equals(request.getScheme()) && port == 443);
        return request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port) + request.getContextPath();
    }

    /** @return an {@link EmailRenderResult}; config: "workflowAssignment" (process merge fields), "variables" (template variables by name) */
    public Object getBuilderResult(String json, Map<String, Object> config) {
        WorkflowAssignment assignment = config == null ? null : (WorkflowAssignment) config.get("workflowAssignment");
        @SuppressWarnings("unchecked")
        Map<String, String> variables = config == null ? null : (Map<String, String>) config.get("variables");
        return EmailTemplateService.renderJsonForSending(json, AppUtil.getCurrentAppDefinition(), assignment, variables);
    }

    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        WorkflowUserManager wum = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        if (!wum.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String action = request.getParameter("action");
        if ("palette".equals(action)) {
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(new JSONObject().put("blocks", palette()).put("themeOptions", getThemeOptions())
                    .put("userEmail", currentUserEmail(wum.getCurrentUsername())).toString());
        } else if ("starters".equals(action)) {
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(StarterCatalog.starters().indexJson().toString());
        } else if ("starter".equals(action)) {
            String json = StarterCatalog.starters().load(request.getParameter("id"));
            if (json == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(json);
        } else if ("starterPreview".equals(action)) {
            StarterCatalog catalog = StarterCatalog.starters();
            String json = catalog.load(request.getParameter("id"));
            if (json == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType("text/html; charset=utf-8");
            response.setHeader("Cache-Control", "private, max-age=300");
            response.getWriter().write(new EmailTemplateRenderer(BlockFactory.plugins(), catalog.sampleResolver(), false, null, null)
                    .withTestVariables(catalog.variableSamples()).render(json).getHtml());
        } else if ("components".equals(action)) {
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(StarterCatalog.components().indexJson().toString());
        } else if ("component".equals(action)) {
            String json = StarterCatalog.components().load(request.getParameter("id"));
            if (json == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(json);
        } else if ("componentPreview".equals(action)) {
            StarterCatalog catalog = StarterCatalog.components();
            String json = catalog.componentPreviewJson(request.getParameter("id"));
            if (json == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType("text/html; charset=utf-8");
            response.setHeader("Cache-Control", "private, max-age=300");
            response.getWriter().write(new EmailTemplateRenderer(BlockFactory.plugins(), catalog.sampleResolver(), false, null, null)
                    .withTestVariables(catalog.variableSamples()).render(json).getHtml());
        } else if ("render".equals(action) && "POST".equalsIgnoreCase(request.getMethod())) {
            response.setContentType("text/html; charset=utf-8");
            try {
                String html = EmailTemplateService.canvasRenderer(AppUtil.getCurrentAppDefinition(), request.getContextPath())
                        .renderBlockPreview(request.getParameter("json"), request.getParameter("templateProperties"));
                response.getWriter().write(html);
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "Block preview failed");
                response.getWriter().write("<div style=\"color:#c00;padding:8px 32px;font:13px Arial,sans-serif;\">Preview failed: "
                        + StringUtil.escapeString(e.getMessage(), StringUtil.TYPE_HTML, null) + "</div>");
            }
        } else if ("repeatRows".equals(action) && "POST".equalsIgnoreCase(request.getMethod())) {
            // the canvas strip under a Repeat: its first rows with real data (Test data applied), read-only
            response.setContentType("application/json; charset=utf-8");
            JSONObject out = new JSONObject();
            try {
                JSONObject element = new JSONObject(request.getParameter("json"));
                JSONObject props = element.optJSONObject("properties");
                if (props == null || !RepeatBlock.class.getName().equals(element.optString("className"))) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                // fetch as the email would (so the note counts the real rows), draw only the first few
                props.put(RepeatBlock.CANVAS_ROWS, String.valueOf(REPEAT_PREVIEW_ROWS));
                EmailRenderResult r = EmailTemplateService.renderBlockForTest(element.toString(), request.getParameter("templateProperties"),
                        AppUtil.getCurrentAppDefinition(), testRecordId(request), request.getContextPath(), testVariables(request));
                JSONArray notes = new JSONArray();
                for (EmailRenderResult.DataNote n : r.getNotes()) {
                    notes.put(new JSONObject().put("block", n.block).put("list", n.list).put("found", n.found).put("max", n.max).put("values", new JSONArray(n.values)));
                }
                out.put("html", r.getHtml()).put("warnings", new JSONArray(r.getWarnings())).put("notes", notes);
            } catch (Exception e) {
                LogUtil.debug(getClassName(), "Repeat rows failed: " + e.getMessage());
                out.put("error", String.valueOf(e.getMessage()));
            }
            response.getWriter().write(out.toString());
        } else if ("size".equals(action) && "POST".equalsIgnoreCase(request.getMethod())) {
            // the canvas size meter: the real email's HTML size, merge fields filled as in Preview
            response.setContentType("application/json; charset=utf-8");
            JSONObject out = new JSONObject().put("warn", EmailTemplateRenderer.SIZE_WARN_BYTES).put("clip", EmailTemplateRenderer.GMAIL_CLIP_BYTES);
            try {
                out.put("bytes", EmailTemplateService.renderForTest(request.getParameter("json"), AppUtil.getCurrentAppDefinition(), testRecordId(request), null, testVariables(request)).getSizeBytes());
            } catch (Exception e) {
                LogUtil.debug(getClassName(), "Size check failed: " + e.getMessage());
                out.put("bytes", -1);
            }
            response.getWriter().write(out.toString());
        } else if ("templateVariables".equals(action)) {
            // Send Templated Email's mapping grid: the chosen template's variables as options
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(templateVariableOptions(request.getParameter("templateId")).toString());
        } else if ("rowFields".equals(action)) {
            // the Repeat block's field picker: the datalist's columns as {row.NAME} fields
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(rowFields(request.getParameter("listId")).toString());
        } else if ("sendTest".equals(action) && "POST".equalsIgnoreCase(request.getMethod())) {
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(sendTest(request.getParameter("json"), request.getParameter("to"), testRecordId(request), testVariables(request)).toString());
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    /** The template's variables as {value, label} options (label: name and description), after an empty option. */
    static JSONArray templateVariableOptions(String templateId) {
        JSONArray out = new JSONArray().put(new JSONObject().put("value", "").put("label", ""));
        String json = EmailTemplateService.load(templateId, AppUtil.getCurrentAppDefinition());
        if (json == null) {
            return out;
        }
        JSONObject props = new JSONObject(json).optJSONObject("properties");
        JSONArray rows = props == null ? null : props.optJSONArray("variables");
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject v = rows.optJSONObject(i);
            String name = v == null ? "" : v.optString("name", "").trim();
            if (EmailTemplateRenderer.VARIABLE_NAME.matcher(name).matches()) {
                String desc = v.optString("description", "").trim();
                out.put(new JSONObject().put("value", name).put("label", desc.isEmpty() ? name : name + " (" + desc + ")"));
            }
        }
        return out;
    }

    /** The datalist's columns (name and label), with the record id first; [] if there is no such datalist. */
    static JSONArray rowFields(String listId) {
        JSONArray out = new JSONArray();
        org.joget.apps.app.model.AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        if (listId == null || listId.isEmpty() || appDef == null) {
            return out;
        }
        org.joget.apps.app.dao.DatalistDefinitionDao dao = (org.joget.apps.app.dao.DatalistDefinitionDao) AppUtil.getApplicationContext().getBean("datalistDefinitionDao");
        org.joget.apps.app.model.DatalistDefinition def = dao.loadById(listId, appDef);
        if (def == null || def.getJson() == null) {
            return out;
        }
        String json = def.getJson().trim();
        if (json.startsWith("(") && json.endsWith(")")) {
            json = json.substring(1, json.length() - 1);
        }
        out.put(new JSONObject().put("name", "id").put("label", "Record ID"));
        JSONArray columns = new JSONObject(json).optJSONArray("columns");
        for (int i = 0; columns != null && i < columns.length(); i++) {
            JSONObject c = columns.getJSONObject(i);
            String name = c.optString("name", "");
            if (!name.isEmpty() && !"id".equals(name)) {
                out.put(new JSONObject().put("name", name).put("label", c.optString("label", name)));
            }
        }
        return out;
    }

    /** Builder "Send test": the unsaved design, merge fields filled as in Preview, to the addresses typed in the prompt. */
    JSONObject sendTest(String json, String toParam, String recordId, Map<String, String> testVariables) {
        JSONObject out = new JSONObject();
        List<String> to = testRecipients(toParam);
        String problem = checkTestRecipients(to);
        if (problem != null) {
            return out.put("ok", false).put("error", problem);
        }
        try {
            EmailRenderResult result = EmailTemplateService.renderForTest(json, AppUtil.getCurrentAppDefinition(), recordId, null, testVariables);
            SendTemplatedEmailTool.sendTest(result, AppUtil.getCurrentAppDefinition(), to);
            return out.put("ok", true).put("to", String.join(", ", to)).put("warnings", new JSONArray(result.getWarnings()));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Send test failed");
            String reason = e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                reason += " (" + e.getCause().getMessage() + ")";
            }
            return out.put("ok", false).put("error", reason);
        }
    }

    /** The builder's Test data values for the template variables (etbVariables, a JSON object of name to value). */
    static Map<String, String> testVariables(HttpServletRequest request) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        String json = request.getParameter("etbVariables");
        if (json == null || json.trim().isEmpty()) {
            return out;
        }
        try {
            JSONObject o = new JSONObject(json);
            for (String name : o.keySet()) {
                if (EmailTemplateRenderer.VARIABLE_NAME.matcher(name).matches()) {
                    out.put(name, o.optString(name, ""));
                }
            }
        } catch (RuntimeException e) {
            // not JSON: no test values
        }
        return out;
    }

    /** The builder's "Test data" record ID (etbRecordId), or null when none or not a valid ID. */
    static String testRecordId(HttpServletRequest request) {
        String id = request.getParameter("etbRecordId");
        return RecordResolver.isValidRecordId(id) ? id : null;
    }

    /** Rows the canvas shows under a Repeat: enough to see the card repeat, few enough to stay quick. */
    static final int REPEAT_PREVIEW_ROWS = 3;

    static final int MAX_TEST_RECIPIENTS = 5;
    static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("[^\\s@<>,;\"()]+@[^\\s@<>,;\"()]+\\.[^\\s@<>,;\"()]+");

    /** @return why these test recipients can't be used, or null if they're fine */
    static String checkTestRecipients(List<String> to) {
        if (to.isEmpty()) {
            return "Enter an email address.";
        }
        if (to.size() > MAX_TEST_RECIPIENTS) {
            return "A test goes to at most " + MAX_TEST_RECIPIENTS + " addresses.";
        }
        for (String a : to) {
            if (!EMAIL.matcher(a).matches()) {
                return "\"" + a + "\" is not an email address.";
            }
        }
        return null;
    }

    /** The prompt's default: the signed-in user's own address ("" if the account has none). */
    static String currentUserEmail(String username) {
        DirectoryManager dm = (DirectoryManager) AppUtil.getApplicationContext().getBean("directoryManager");
        User user = username == null ? null : dm.getUserByUsername(username);
        return user == null || user.getEmail() == null ? "" : user.getEmail();
    }

    /** Addresses separated by ";" or "," (as in a Joget user's email field). */
    static List<String> testRecipients(String email) {
        List<String> to = new ArrayList<>();
        if (email != null) {
            for (String a : email.split("[;,]")) {
                if (!a.trim().isEmpty()) {
                    to.add(a.trim());
                }
            }
        }
        return to;
    }

    /**
     * Installed EmailBlock plugins for the canvas palette, most-used first.
     */
    /** Every installed block (bundled and third-party), in palette order. Shared by the palette and the AI Composer. */
    public static List<EmailBlock> installedBlocks() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        List<EmailBlock> blocks = new ArrayList<>();
        for (Plugin plugin : pluginManager.list(EmailBlock.class)) {
            if (plugin instanceof EmailBlock) {
                blocks.add((EmailBlock) plugin);
            }
        }
        return paletteOrder(blocks);
    }

    static JSONArray palette() {
        JSONArray list = new JSONArray();
        for (EmailBlock b : installedBlocks()) {
            try {
                list.put(new JSONObject()
                        .put("className", b.getClassName())
                        .put("label", b.getLabel())
                        .put("icon", b.getIcon())
                        .put("category", b.getCategory())
                        .put("containerType", b.getContainerType() == null ? "" : b.getContainerType())
                        .put("propertyOptions", b.getPropertyOptions())
                        .put("defaultProperties", b.getDefaultPropertyValues())
                        .put("template", new JSONObject(b.getBuilderTemplate())));
            } catch (RuntimeException e) {
                // e.g. a third-party block whose builder template is not valid JSON: leave it out, keep the others
                LogUtil.error(EmailTemplateBuilder.class.getName(), e, "Email block " + b.getClassName() + " was left out of the palette");
            }
        }
        return list;
    }

    /** Bundled blocks first in a fixed, most-used-first order; any other (third-party) blocks after, by label. */
    static final List<String> ORDER = List.of("TextBlock", "ButtonBlock", "ImageBlock", "NavbarBlock", "HtmlBlock", "DividerBlock", "SpacerBlock", "RowBlock", "ColumnBlock", "HeroBlock", "OutsideBlock", "DatalistTableBlock", "RepeatBlock", "StepsBlock", "ProgressBarsBlock", "FactsBlock", "TimelineBlock", "StatTilesBlock", "BadgeBlock", "CalloutBlock");

    static List<EmailBlock> paletteOrder(List<EmailBlock> blocks) {
        List<EmailBlock> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt((EmailBlock b) -> {
            int i = b.getClassName().startsWith("org.joget.etb.lib.") ? ORDER.indexOf(b.getClass().getSimpleName()) : -1;
            return i < 0 ? ORDER.size() : i;
        }).thenComparing(EmailBlock::getLabel));
        return sorted;
    }
}
