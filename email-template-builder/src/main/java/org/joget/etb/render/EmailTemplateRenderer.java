package org.joget.etb.render;

import ch.digitalfondue.mjml4j.Mjml4j;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joget.apps.app.model.AppDefinition;
import org.joget.commons.util.StringUtil;
import org.joget.etb.api.DefaultEmailBlock;
import org.joget.etb.api.EmailBlock;
import org.joget.etb.api.EmailRenderContext;
import org.joget.etb.api.EmailRow;
import org.joget.workflow.model.WorkflowAssignment;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Template JSON to email: walk the block tree, collect MJML and plain text per block, compile with
 * mjml4j. The same renderer produces the canvas previews (one block at a time, preview mode), so the
 * canvas cannot drift from the sent email.
 */
public class EmailTemplateRenderer {

    /** Gmail clips messages at about 102 KB; warn at 80 KB to leave room for the relay's own additions. */
    public static final int SIZE_WARN_BYTES = 80 * 1024;
    /** Where Gmail cuts the message off behind a "View entire message" link. */
    public static final int GMAIL_CLIP_BYTES = 102 * 1024;

    static final String TOP = "template";
    static final Pattern FONT = Pattern.compile("[A-Za-z0-9 ,'\\-]{1,120}");
    static final Pattern LANG = Pattern.compile("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?");

    private final BlockFactory factory;
    private final MergeResolver resolver;
    private final boolean preview;
    private final WorkflowAssignment assignment;
    private final AppDefinition appDef;
    /** What the data blocks found in the current render ({@link EmailRenderContext#dataNote}). */
    private List<EmailRenderResult.DataNote> notes = new ArrayList<>();
    /** The template's theme while rendering, for {@link EmailRenderContext#themeColor(String)}. */
    private Theme theme;
    private ResourceImages images;
    /** the values given for the template's variables ({var.NAME}): a sender's, or the builder's test values */
    private Map<String, String> givenVariables;
    private boolean testValues;
    /** a builder render (Preview, Send test, size meter, thumbnails): see EmailRenderContext.isBuilder */
    private boolean builder;
    /** {var.NAME} for the render in progress */
    private EmailRow vars;

    /** A template variable's name: a letter, then letters, digits and _ */
    public static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    /** the template's public server address (merge fields resolved), for linked App Resource images */
    private String publicUrl = "";
    /** cid to file, for the App Resource images of the render in progress */
    private Map<String, java.io.File> inline = new java.util.LinkedHashMap<>();

    /** {row.NAME} (a Repeat's row) and {var.NAME} (a template variable), each also ?html and ?raw */
    static final java.util.regex.Pattern ROW_FIELD = java.util.regex.Pattern.compile("\\{(row|var)\\.([A-Za-z0-9_.]{1,64})(\\?(html|raw))?\\}");

    enum RowMode { HTML, PLAIN, URL }

    /** fill()'s marker for the Custom HTML block's merge fields: see {@link MergeResolver#resolveTrusted}. Compared by identity. */
    static final String TRUSTED = new String("trusted");

    /** A paragraph (the editor's plain <p>, maybe with a style) whose only content is a {row.x?html} or {var.x?html} field */
    static final Pattern HTML_FIELD_PARAGRAPH = Pattern.compile("<p(?:\\s[^>]*)?>\\s*(\\{(?:row|var)\\.[A-Za-z0-9_.]{1,64}\\?html\\})\\s*</p>");

    /** Stat tiles one under another on phones; the canvas has the same rule for its mobile view. */
    static final String STACK_TILES = "@media only screen and (max-width:480px){td.etb-tile,td.etb-tile-gap{display:block !important;width:auto !important;}}";

    /** The container type of "Outside the card" blocks: rendered on the page background, splitting the card. */
    public static final String OUTSIDE = "outside";

    /** #appResource.NAME# as a whole image value; the name as Joget stores it (letters, digits, . _ - ( ) and spaces) */
    static final java.util.regex.Pattern APP_RESOURCE = java.util.regex.Pattern.compile("#appResource\\.([\\p{L}\\p{N}._()' -]{1,200})#");

    public EmailTemplateRenderer(BlockFactory factory, MergeResolver resolver, boolean preview,
            WorkflowAssignment assignment, AppDefinition appDef) {
        this.factory = factory;
        this.resolver = preview ? MergeResolver.none() : resolver;
        this.preview = preview;
        this.assignment = assignment;
        this.appDef = appDef;
    }

    /**
     * The values a sender gives the template's variables (Settings), by name, e.g. from Send Templated Email's
     * mapping. A declared variable with no value is empty (with a warning); an undeclared name is reported.
     */
    public EmailTemplateRenderer withVariables(Map<String, String> values) {
        this.givenVariables = values;
        this.testValues = false;
        return this;
    }

    /**
     * The builder's test values for the template's variables (Test data, kept in the browser). A declared
     * variable with no test value is empty (with a warning); stale names from the browser are ignored.
     */
    public EmailTemplateRenderer withTestVariables(Map<String, String> values) {
        this.givenVariables = values == null ? java.util.Collections.<String, String>emptyMap() : values;
        this.testValues = true;
        this.builder = true;
        return this;
    }

    /** A builder render that isn't the canvas (e.g. a gallery thumbnail): builder-only aids such as sample rows apply. */
    public EmailTemplateRenderer forBuilder() {
        this.builder = true;
        return this;
    }

    /** The template's declared variables (name to description), in order; invalid names skipped. */
    public static Map<String, String> declaredVariables(JSONObject templateProperties) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        JSONArray rows = templateProperties.optJSONArray("variables");
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject v = rows.optJSONObject(i);
            String name = v == null ? "" : v.optString("name", "").trim();
            if (VARIABLE_NAME.matcher(name).matches()) {
                out.put(name, v.optString("description", ""));
            }
        }
        return out;
    }

    /** {var.NAME} values for this render: the sender's values, the builder's test values, or (neither) empty. */
    private EmailRow variables(JSONObject p, List<String> warnings) {
        Map<String, String> declared = declaredVariables(p);
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (String name : declared.keySet()) {
            String v = givenVariables == null ? "" : givenVariables.get(name);
            if (v == null) {
                warnings.add(testValues ? "Template variable \"" + name + "\" has no test value (Test data); it was left empty."
                        : "Template variable \"" + name + "\" was given no value by the sender; it was left empty.");
                v = "";
            }
            values.put(name, v);
        }
        if (givenVariables != null && !testValues) {
            for (String name : givenVariables.keySet()) {
                if (!declared.containsKey(name)) {
                    warnings.add("\"" + name + "\" is not a variable of this template (Settings); its value was ignored.");
                }
            }
        }
        return new MapRow(values);
    }

    /** App Resource images: linked (builder) or embedded as cid: (sending). Without this, they are left out with a warning. */
    public EmailTemplateRenderer withImages(ResourceImages images) {
        this.images = images;
        return this;
    }

    /** Full email from a template definition. */
    public EmailRenderResult render(String json) {
        List<String> warnings = new ArrayList<>();
        notes = new ArrayList<>();
        inline = new java.util.LinkedHashMap<>();
        JSONObject root = new JSONObject(json);
        JSONObject p = props(root);
        Theme theme = new Theme(p);
        this.theme = theme;

        publicUrl = stripLineBreaks(resolver.resolve(p.optString("publicUrl", ""), null)).replaceAll("/+$", "");
        vars = variables(p, warnings);
        Ctx top = new Ctx(null, null, warnings, null);
        String subject = stripLineBreaks(top.plain(p.optString("subject", "")));
        String preheader = top.html(p.optString("preheader", ""));

        // the email is a series of segments: runs of blocks in a card, and "outside the card" blocks on the page
        StringBuilder body = new StringBuilder();
        StringBuilder run = new StringBuilder();
        boolean hasRun = false;
        List<String> text = new ArrayList<>();
        JSONArray elements = root.optJSONArray("elements");
        if (elements != null) {
            for (int i = 0; i < elements.length(); i++) {
                Rendered r = renderElement(elements.getJSONObject(i), TOP, warnings);
                if (r == null) {
                    continue;
                }
                text.add(r.text);
                if (OUTSIDE.equals(r.block.getContainerType())) {
                    if (hasRun) {
                        body.append(theme.card(run.toString(), body.length() == 0, false));
                        run.setLength(0);
                        hasRun = false;
                    }
                    body.append(r.mjml);
                } else {
                    run.append(r.topLevelMjml());
                    hasRun = true;
                }
            }
        }
        if (hasRun || body.length() == 0) {
            // the last card (or the only one: an email with no outside blocks renders exactly as before)
            body.append(theme.card(run.toString(), body.length() == 0, true));
        }

        String mjml = theme.document(subject, preheader, body.toString(), false);
        String html = compile(mjml, theme.lang);
        int size = html.getBytes(StandardCharsets.UTF_8).length;
        if (size > SIZE_WARN_BYTES) {
            warnings.add("Email is " + (size / 1024) + " KB; Gmail clips messages over about 102 KB.");
        }
        return new EmailRenderResult(subject, html, HtmlToText.normalize(String.join("\n\n", text)), mjml, size, warnings, inline).withNotes(notes);
    }

    /**
     * Canvas preview of one element: the compiled HTML body fragment.
     *
     * @param templateProperties the template's properties (theme), may be null
     */
    public String renderBlockPreview(String elementJson, String templateProperties) {
        return renderBlock(elementJson, templateProperties).getHtml();
    }

    /**
     * One element as a body fragment, like {@link #renderBlockPreview}, with its warnings and notes. With a builder
     * (non-canvas) renderer it shows real data, e.g. a Repeat's first rows on the canvas.
     */
    public EmailRenderResult renderBlock(String elementJson, String templateProperties) {
        List<String> warnings = new ArrayList<>();
        notes = new ArrayList<>();
        JSONObject p = templateProperties == null || templateProperties.isEmpty() ? new JSONObject() : new JSONObject(templateProperties);
        Theme theme = new Theme(p);
        this.theme = theme;
        if (!preview) {
            publicUrl = stripLineBreaks(resolver.resolve(p.optString("publicUrl", ""), null)).replaceAll("/+$", "");
            vars = variables(p, warnings);
        }
        Rendered r = renderElement(new JSONObject(elementJson), TOP, warnings);
        String body = r == null ? unknown(warnings) : r.topLevelMjml();
        String html = bodyFragment(compile(theme.document("", "", body, true), theme.lang));
        return new EmailRenderResult("", html, r == null ? "" : r.text, body, html.length(), warnings).withNotes(notes);
    }

    Rendered renderElement(JSONObject el, String parentType, List<String> warnings) {
        return renderElement(el, parentType, warnings, null);
    }

    Rendered renderElement(JSONObject el, String parentType, List<String> warnings, EmailRow row) {
        String className = el.optString("className", "");
        EmailBlock block = factory.create(className);
        if (block == null) {
            warnings.add("Unknown block " + className + " was skipped.");
            return null;
        }
        if (!Arrays.asList(block.getAllowedParent().split(",")).contains(parentType)) {
            warnings.add(block.getLabel() + " cannot be placed in a " + parentType + "; it was skipped.");
            return null;
        }
        block.setProperties(toMap(props(el)));
        Ctx ctx = new Ctx(el.optJSONArray("elements"), block.getContainerType(), warnings, row);
        try {
            return new Rendered(block, block.toMjml(ctx), block.toText(ctx));
        } catch (RuntimeException e) {
            throw new EmailRenderException("Block " + block.getLabel() + " (" + className + ") failed to render: " + e.getMessage(), e);
        }
    }

    String unknown(List<String> warnings) {
        return "<mj-section><mj-column><mj-text color=\"#cc0000\">" + DefaultEmailBlock.attr(String.join(" ", warnings)) + "</mj-text></mj-column></mj-section>";
    }

    static String compile(String mjml, String lang) {
        try {
            return Mjml4j.render(explicitTbody(mjml), new Mjml4j.Configuration(lang));
        } catch (RuntimeException e) {
            throw new EmailRenderException("MJML compile failed: " + e.getMessage(), e);
        }
    }

    static final Pattern TABLE_TAG = Pattern.compile("<table\\b[^>]*>|</table\\s*>", Pattern.CASE_INSENSITIVE);
    static final Pattern FIRST_ROW = Pattern.compile("\\s*<tr\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Wraps the rows of every table that has them directly (no {@code <tbody>}) in an explicit tbody. mjml4j's parser
     * adds the implied tbody of a table nested in another table's cell but then loses track of the enclosing tags:
     * everything after that mj-text came out as literal MJML. Authors (and pasted HTML) routinely leave tbody out.
     */
    static String explicitTbody(String html) {
        if (html == null || html.indexOf("<table") < 0 && html.indexOf("<TABLE") < 0) {
            return html;
        }
        Matcher m = TABLE_TAG.matcher(html);
        StringBuilder out = new StringBuilder(html.length() + 64);
        java.util.ArrayDeque<Boolean> open = new java.util.ArrayDeque<>();
        int last = 0;
        while (m.find()) {
            out.append(html, last, m.start());
            if (m.group().startsWith("</")) {
                if (!open.isEmpty() && open.pop()) {
                    out.append("</tbody>");
                }
                out.append(m.group());
            } else {
                boolean rows = FIRST_ROW.matcher(html).region(m.end(), html.length()).lookingAt();
                open.push(rows);
                out.append(m.group()).append(rows ? "<tbody>" : "");
            }
            last = m.end();
        }
        return out.append(html, last, html.length()).toString();
    }

    static JSONObject props(JSONObject el) {
        JSONObject p = el.optJSONObject("properties");
        return p == null ? new JSONObject() : p;
    }

    static String stripLineBreaks(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
    }

    static String bodyFragment(String html) {
        int open = html.indexOf("<body");
        int start = open < 0 ? -1 : html.indexOf('>', open);
        int end = html.lastIndexOf("</body>");
        return (start < 0 || end < 0) ? html : html.substring(start + 1, end);
    }

    /** JSON properties to the Map shape Joget plugins expect (arrays as Object[] of Maps). */
    static Map<String, Object> toMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, toValue(json.get(key)));
        }
        return map;
    }

    static Object toValue(Object v) {
        if (v instanceof JSONObject) {
            return toMap((JSONObject) v);
        } else if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            Object[] out = new Object[a.length()];
            for (int i = 0; i < a.length(); i++) {
                out[i] = toValue(a.get(i));
            }
            return out;
        } else if (v == JSONObject.NULL) {
            return "";
        }
        return v.toString();
    }

    final class Rendered {
        final EmailBlock block;
        final String mjml;
        final String text;

        Rendered(EmailBlock block, String mjml, String text) {
            this.block = block;
            this.mjml = mjml == null ? "" : mjml;
            this.text = text == null ? "" : text;
        }

        /** Rows and heroes are sections already; anything else at the top level is wrapped in a full-width section. */
        String topLevelMjml() {
            return block.isSectionLevel() ? mjml : "<mj-section><mj-column>" + mjml + "</mj-column></mj-section>";
        }
    }

    final class Ctx implements EmailRenderContext {
        private final JSONArray childElements;
        private final String containerType;
        private final List<String> warnings;
        private final EmailRow row;
        private String childMjml;
        private String childText;

        Ctx(JSONArray childElements, String containerType, List<String> warnings, EmailRow row) {
            this.childElements = childElements;
            this.containerType = containerType;
            this.warnings = warnings;
            this.row = row;
        }

        public String html(String authorText) {
            return fill(authorText, StringUtil.TYPE_HTML, RowMode.HTML);
        }

        public String url(String authorUrl) {
            String author = authorUrl == null ? "" : authorUrl.trim();
            java.util.regex.Matcher whole = ROW_FIELD.matcher(author);
            String url;
            if (whole.matches() && source(whole.group(1)) != null) {
                // a link that is just the field: the stored value is the whole URL
                String v = value(source(whole.group(1)), whole.group(1), whole.group(2), "raw");
                url = v == null ? "" : StringUtil.escapeString(v.trim(), StringUtil.TYPE_HTML, null);
            } else {
                url = fill(author, StringUtil.TYPE_HTML, RowMode.URL).trim();
            }
            // canvas: an unresolved merge field such as #form.req.link# (or a {row.link} field) is allowed; javascript: never is
            boolean unresolvedMergeField = preview && (url.startsWith("#") || url.startsWith("{row.") || url.startsWith("{var."));
            if (!url.isEmpty() && !unresolvedMergeField && !UrlPolicy.isAllowed(url)) {
                warnings.add("Blocked link \"" + url + "\": only http, https, mailto, tel and cid links are allowed.");
                url = "#";
            }
            return url.replace("\"", "&quot;");
        }

        public String image(String authorValue) {
            java.util.regex.Matcher m = APP_RESOURCE.matcher(authorValue == null ? "" : authorValue.trim());
            if (!m.matches()) {
                return url(authorValue);
            }
            String name = m.group(1);
            java.io.File file = (images == null || name.contains("..")) ? null : images.file(name);
            if (file == null) {
                warnings.add(images == null ? "Image \"" + name + "\" is an App Resource, which only the Send Templated Email tool can embed; it was left out."
                        : "Image \"" + name + "\" is not in this app's App Resources; it was left out.");
                return "";
            }
            String link = images.link(name);
            if (link != null) {
                return link.replace("\"", "&quot;");
            }
            for (Map.Entry<String, java.io.File> e : inline.entrySet()) {
                if (e.getValue().equals(file)) {
                    return "cid:" + e.getKey();
                }
            }
            String cid = "etb" + (inline.size() + 1) + "-" + name.replaceAll("[^A-Za-z0-9._-]", "_");
            inline.put(cid, file);
            return "cid:" + cid;
        }

        public String publicImage(String authorValue) {
            java.util.regex.Matcher m = APP_RESOURCE.matcher(authorValue == null ? "" : authorValue.trim());
            if (!m.matches()) {
                return url(authorValue);
            }
            String name = m.group(1);
            java.io.File file = (images == null || name.contains("..")) ? null : images.file(name);
            if (file == null) {
                warnings.add(images == null ? "Background \"" + name + "\" is an App Resource, which needs the Send Templated Email tool; it was left out."
                        : "Background \"" + name + "\" is not in this app's App Resources; it was left out.");
                return "";
            }
            boolean usable = true;
            if (!images.isPublic(name)) {
                warnings.add("Background \"" + name + "\" is not a public App Resource, so recipients would see the background colour instead."
                        + " Make it public in App Resources.");
                usable = false;
            }
            if (!UrlPolicy.isAllowed(publicUrl) || !publicUrl.matches("(?i)https?://.+")) {
                warnings.add("Background \"" + name + "\" needs the template's Public server address (Settings), e.g. https://apps.example.com/jw;"
                        + " recipients would see the background colour instead.");
                usable = false;
            }
            String link = images.link(name);
            if (link != null) {
                // builder: show it anyway, the warnings say what recipients would get
                return link.replace("\"", "&quot;");
            }
            return usable ? (publicUrl + images.path(name)).replace("\"", "&quot;") : "";
        }

        public String trustedHtml(String authorText) {
            return fill(authorText, TRUSTED, RowMode.HTML);
        }

        public String plain(String authorText) {
            return fill(authorText, null, RowMode.PLAIN);
        }

        /**
         * The author's text with merge fields and {row.NAME} / {var.NAME} fields (each also as ?html and ?raw) filled
         * in ONE pass: the text is split on the row/var fields, merge fields are resolved only in the author's own text
         * between them, and each field is filled once. So a row value is never resolved as a merge field, and a merge
         * value (a form value such as "{var.boardId}") is never read as a field: data never chooses what is printed.
         * A {row.…} outside a Repeat (and every field on the canvas) is left as written.
         *
         * @param escapeFormat the merge resolver's escaping (TYPE_HTML, or null for none)
         */
        String fill(String authorText, String escapeFormat, RowMode mode) {
            if (authorText == null) {
                return "";
            }
            if (!authorText.contains("{row.") && !authorText.contains("{var.")) {
                return merge(authorText, escapeFormat);
            }
            String text = authorText;
            if (mode == RowMode.HTML) {
                // a paragraph holding only an HTML field: the field brings its own blocks (a <div> can't sit in a <p>,
                // so email apps would split it into two empty, 13px-margin paragraphs around it)
                text = HTML_FIELD_PARAGRAPH.matcher(text).replaceAll("$1");
            }
            java.util.regex.Matcher m = ROW_FIELD.matcher(text);
            StringBuilder out = new StringBuilder();
            int last = 0;
            while (m.find()) {
                out.append(merge(text.substring(last, m.start()), escapeFormat)).append(field(m, mode));
                last = m.end();
            }
            return out.append(merge(text.substring(last), escapeFormat)).toString();
        }

        /** Merge fields in the author's text: escaped (TYPE_HTML), plain (null), or {@link #TRUSTED} (Joget's sanitiser, no escaping). */
        private String merge(String text, String escapeFormat) {
            return escapeFormat == TRUSTED ? resolver.resolveTrusted(text) : resolver.resolve(text, escapeFormat);
        }

        /** One {row.…}/{var.…} field's value for its place (escaped HTML, sanitised HTML, plain text or URL-encoded). */
        private String field(java.util.regex.Matcher m, RowMode mode) {
            EmailRow src = source(m.group(1));
            if (src == null) {
                return m.group();
            }
            String format = m.group(4) == null ? "" : m.group(4);
            // URL parts use the stored value; plain text (subject, text part, filter and block values) never takes ?html's markup
            String v = value(src, m.group(1), m.group(2), mode == RowMode.URL ? "raw" : mode == RowMode.PLAIN && "html".equals(format) ? "" : format);
            if (v == null) {
                return "";
            } else if (mode == RowMode.URL) {
                // part of a URL, e.g. https://x.com/task?id={row.id}
                return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
            } else if (mode == RowMode.PLAIN || "html".equals(format)) {
                return v;
            }
            return StringUtil.escapeString(v, StringUtil.TYPE_HTML, null);
        }

        /** The values for a prefix ("row": the Repeat's row, "var": the template variables), or null (with one warning). */
        private EmailRow source(String prefix) {
            EmailRow src = "var".equals(prefix) ? vars : row;
            if (src == null && !preview && "row".equals(prefix)) {
                String w = "{row.…} fields are only filled inside a Repeat block; they were left as written.";
                if (!warnings.contains(w)) {
                    warnings.add(w);
                }
            }
            return src;
        }

        /** A field's value (html: sanitised HTML; raw: stored value; else formatted text), or null with a warning. */
        private String value(EmailRow src, String prefix, String name, String format) {
            String v = "html".equals(format) ? src.html(name) : "raw".equals(format) ? src.raw(name) : src.text(name);
            if (v == null) {
                String w = "var".equals(prefix)
                        ? "{var." + name + "} is not a variable of this template; add it in Settings (Template variables). It was left empty."
                        : "{row." + name + "} is not a field of the repeated data; it was left empty.";
                if (!warnings.contains(w)) {
                    warnings.add(w);
                }
            }
            return v;
        }

        public String text(String html) {
            return HtmlToText.convert(html);
        }

        public String children() {
            renderChildren();
            return childMjml;
        }

        /** Fresh block instances for every row: blocks cache what they resolve. */
        public String children(EmailRow r) {
            return renderChildren(r)[0];
        }

        public String childrenText(EmailRow r) {
            return renderChildren(r)[1];
        }

        public String childSections(EmailRow r) {
            StringBuilder out = new StringBuilder();
            StringBuilder run = new StringBuilder();
            if (childElements != null && containerType != null) {
                for (int i = 0; i < childElements.length(); i++) {
                    Rendered rendered = renderElement(childElements.getJSONObject(i), containerType, warnings, r);
                    if (rendered == null) {
                        continue;
                    }
                    if (rendered.block.isSectionLevel()) {
                        flush(out, run);
                        out.append(rendered.mjml);
                    } else {
                        run.append(rendered.mjml);
                    }
                }
            }
            flush(out, run);
            return out.toString();
        }

        /** A run of content blocks as one full-width section. */
        private void flush(StringBuilder out, StringBuilder run) {
            if (run.length() > 0) {
                out.append("<mj-section padding=\"0px\"><mj-column>").append(run).append("</mj-column></mj-section>");
                run.setLength(0);
            }
        }

        private String[] renderChildren(EmailRow r) {
            StringBuilder mjml = new StringBuilder();
            List<String> text = new ArrayList<>();
            if (childElements != null && containerType != null) {
                for (int i = 0; i < childElements.length(); i++) {
                    Rendered rendered = renderElement(childElements.getJSONObject(i), containerType, warnings, r);
                    if (rendered != null) {
                        mjml.append(rendered.mjml);
                        text.add(rendered.text);
                    }
                }
            }
            return new String[]{mjml.toString(), String.join("\n\n", text)};
        }

        public String childrenText() {
            renderChildren();
            return childText;
        }

        private void renderChildren() {
            if (childMjml != null) {
                return;
            }
            StringBuilder mjml = new StringBuilder();
            List<String> text = new ArrayList<>();
            if (childElements != null && containerType != null) {
                for (int i = 0; i < childElements.length(); i++) {
                    Rendered r = renderElement(childElements.getJSONObject(i), containerType, warnings, row);
                    if (r != null) {
                        mjml.append(r.mjml);
                        text.add(r.text);
                    }
                }
            }
            childMjml = mjml.toString();
            childText = String.join("\n\n", text);
        }

        public String themeColor(String name) {
            if (theme == null) {
                return null;
            }
            switch (name == null ? "" : name) {
                case "link":
                    return theme.linkColor;
                case "text":
                    return theme.textColor;
                case "content":
                    return theme.contentBackground;
                default:
                    return null;
            }
        }

        public boolean isPreview() {
            return preview;
        }

        public boolean isBuilder() {
            return preview || builder;
        }

        public WorkflowAssignment getAssignment() {
            return assignment;
        }

        public AppDefinition getAppDef() {
            return appDef;
        }

        public void warn(String message) {
            warnings.add(message);
        }

        public void dataNote(String blockLabel, String listName, int found, int max, List<String> values) {
            notes.add(new EmailRenderResult.DataNote(blockLabel, listName, found, max, values));
        }
    }

    /** Template-level theme, every value validated before it reaches MJML. */
    static final class Theme {
        final String background;
        final String contentBackground;
        final String font;
        final String textColor;
        final String linkColor;
        final String width;
        final String lang;
        final String radius;
        final boolean shadow;

        Theme(JSONObject p) {
            background = pick(p, "backgroundColor", DefaultEmailBlock.HEX_COLOR, "#f4f4f4");
            contentBackground = pick(p, "contentBackground", DefaultEmailBlock.HEX_COLOR, "#ffffff");
            font = pick(p, "fontFamily", FONT, "Arial, Helvetica, sans-serif");
            textColor = pick(p, "textColor", DefaultEmailBlock.HEX_COLOR, "#333333");
            linkColor = pick(p, "linkColor", DefaultEmailBlock.HEX_COLOR, "#1a73e8");
            String w = pick(p, "width", DefaultEmailBlock.PX, "600px");
            int px = Integer.parseInt(w.replace("px", ""));
            width = Math.max(320, Math.min(800, px)) + "px";
            lang = pick(p, "language", LANG, "en");
            radius = pick(p, "contentBorderRadius", DefaultEmailBlock.PX, "0px");
            shadow = "true".equals(p.optString("contentShadow", ""));
        }

        /**
         * The content card: one wrapper carrying the content background, corner radius (mjml4j adds
         * overflow:hidden, so coloured bands are clipped to the corners) and an optional shadow class.
         * Transparent spacer sections give the outer page margin.
         */
        String card(String body) {
            return card(body, true, true);
        }

        /**
         * @param top    page margin above: only when the card starts the email (else an outside block is above it)
         * @param bottom page margin below: only when the card ends the email
         */
        String card(String body, boolean top, boolean bottom) {
            String spacer = "<mj-section padding=\"0\"><mj-column><mj-spacer height=\"24px\"></mj-spacer></mj-column></mj-section>";
            return (top ? spacer : "") + "<mj-wrapper css-class=\"etb-card\" background-color=\"" + contentBackground + "\" border-radius=\"" + radius
                    + "\" padding=\"0\">" + body + "</mj-wrapper>" + (bottom ? spacer : "");
        }

        /**
         * @param preview canvas fragment: no page or section background, so a block shows the colour of
         *                whatever it sits in (the canvas card, or a column)
         */
        String document(String subject, String preheader, String body, boolean preview) {
            StringBuilder sb = new StringBuilder();
            sb.append("<mjml><mj-head>");
            if (!subject.isEmpty()) {
                sb.append("<mj-title>").append(StringUtil.escapeString(subject, StringUtil.TYPE_HTML, null)).append("</mj-title>");
            }
            if (!preheader.isEmpty()) {
                sb.append("<mj-preview>").append(preheader).append("</mj-preview>");
            }
            sb.append("<mj-attributes>")
              .append("<mj-all font-family=\"").append(font).append("\"></mj-all>")
              .append("<mj-text color=\"").append(textColor).append("\" line-height=\"1.5\"></mj-text>")
              .append("<mj-section padding=\"0\"></mj-section>")
              .append("</mj-attributes>")
              .append("<mj-style>a{color:").append(linkColor).append(";}")
              .append(shadow ? ".etb-card{box-shadow:0 2px 10px rgba(0,0,0,0.12);}" : "")
              // Stat tiles stack on phones (MJML's own breakpoint); where media queries are ignored they stay side by side
              .append(body.contains("class=\"etb-tile\"") ? STACK_TILES : "")
              .append("</mj-style>")
              .append("</mj-head><mj-body").append(preview ? "" : " background-color=\"" + background + "\"").append(" width=\"").append(width).append("\">")
              .append(body)
              .append("</mj-body></mjml>");
            return sb.toString();
        }

        static String pick(JSONObject p, String key, Pattern allowed, String fallback) {
            String v = DefaultEmailBlock.withUnit(p.optString(key, ""), allowed);
            return allowed.matcher(v).matches() ? v : fallback;
        }
    }
}
