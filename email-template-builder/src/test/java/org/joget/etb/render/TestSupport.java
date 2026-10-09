package org.joget.etb.render;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joget.commons.util.StringUtil;

/** Test doubles: a merge resolver that mimics AppUtil's per-value escaping, without Spring. */
public final class TestSupport {

    static final Pattern HASH = Pattern.compile("#([a-zA-Z]+\\.[^#\\s]+)#");

    private TestSupport() {
    }

    public static MergeResolver resolver(Map<String, String> values) {
        return (text, format) -> {
            if (text == null) {
                return "";
            }
            Matcher m = HASH.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String v = values.get(m.group(1));
                String out = v == null ? m.group(0) : (format == null ? v : StringUtil.escapeString(v, format, null));
                m.appendReplacement(sb, Matcher.quoteReplacement(out));
            }
            m.appendTail(sb);
            return sb.toString();
        };
    }

    public static EmailTemplateRenderer renderer(Map<String, String> values) {
        return new EmailTemplateRenderer(BlockFactory.reflective(), resolver(values), false, null, null);
    }

    public static EmailTemplateRenderer previewRenderer() {
        return new EmailTemplateRenderer(BlockFactory.reflective(), MergeResolver.none(), true, null, null);
    }
}
