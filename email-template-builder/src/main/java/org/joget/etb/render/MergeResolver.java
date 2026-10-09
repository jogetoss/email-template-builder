package org.joget.etb.render;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.workflow.model.WorkflowAssignment;

/** Resolves Joget hash variables (merge fields) in author text. */
public interface MergeResolver {

    /**
     * @param escapeFormat a StringUtil.TYPE_* format applied to each substituted value, or null for none
     */
    String resolve(String text, String escapeFormat);

    /**
     * For the Custom HTML block: values are not escaped, but a field without a format still goes through Joget's
     * HTML sanitiser (scripts and event handlers removed). Never gets a format of its own (see {@link #withOwnFormat}).
     */
    default String resolveTrusted(String text) {
        return resolve(text, null);
    }

    /** Leaves merge fields as-is (builder canvas). */
    static MergeResolver none() {
        return (text, escapeFormat) -> text;
    }

    /** Joget's AppUtil.processHashVariable, with the process context when there is one (see {@link #withOwnFormat}). */
    static MergeResolver joget(WorkflowAssignment assignment, AppDefinition appDef) {
        MergeResolver joget = (text, escapeFormat) -> AppUtil.processHashVariable(text, assignment, escapeFormat, null, appDef);
        return new MergeResolver() {
            public String resolve(String text, String escapeFormat) {
                return withOwnFormat(joget, text, escapeFormat);
            }

            public String resolveTrusted(String text) {
                return joget.resolve(text, null);
            }
        };
    }

    /** A merge field: #prefix.key#, the key may hold spaces (#date.EEE, d MMM yyyy#) and a ?format. */
    java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("#([a-zA-Z]+\\.[^#\\r\\n]+?)#");

    /**
     * Resolves each merge field through {@code joget} with a format of its own when the author gave none: ?html when
     * the value goes into HTML, ?noescape when it goes into plain text (subject, text part, datalist filter values).
     * Without a format Joget runs its HTML sanitiser on the value and THEN applies the caller's escaping, so
     * "Price < 100" came out as "Price &amp;lt; 100" and "<script>x</script>" vanished instead of showing as text.
     * With its own format, the value is escaped once (or kept as is for plain text), never sanitised twice. A field the
     * author gave a format (#form.x?url#, #x?noescape#) is resolved exactly as before. An unresolved field stays as
     * written, without the added format.
     */
    static String withOwnFormat(MergeResolver joget, String text, String escapeFormat) {
        if (text == null || text.indexOf('#') < 0) {
            return text == null ? "" : text;
        }
        String own = escapeFormat == null ? "noescape" : org.joget.commons.util.StringUtil.TYPE_HTML.equals(escapeFormat) ? "html" : null;
        java.util.regex.Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            String token = m.group();
            if (own == null || m.group(1).contains("?")) {
                out.append(joget.resolve(token, escapeFormat));
            } else {
                String formatted = "#" + m.group(1) + "?" + own + "#";
                String v = joget.resolve(formatted, null);
                out.append(v == null || v.equals(formatted) ? token : v);
            }
            last = m.end();
        }
        return out.append(text, last, text.length()).toString();
    }
}
