package org.joget.etb.api;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.joget.plugin.base.ExtDefaultPlugin;

/**
 * Base class for blocks: a content block that may sit at the top level or in a column.
 *
 * <p>Style properties are never written into MJML raw. Use {@link #prop(String, Pattern, String)} with
 * one of the patterns below so a crafted value cannot break out of an attribute.</p>
 */
public abstract class DefaultEmailBlock extends ExtDefaultPlugin implements EmailBlock {

    public static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?");
    public static final Pattern PX = Pattern.compile("\\d{1,4}px");
    public static final Pattern PERCENT = Pattern.compile("\\d{1,3}%");
    public static final Pattern PADDING = Pattern.compile("\\d{1,3}px( \\d{1,3}px){0,3}");
    public static final Pattern ALIGN = Pattern.compile("left|center|right");
    public static final Pattern VALIGN = Pattern.compile("top|middle|bottom");

    public String getCategory() {
        return CATEGORY_CONTENT;
    }

    public boolean isContainer() {
        return false;
    }

    public String getAllowedParent() {
        return PARENT_TEMPLATE_OR_COLUMN;
    }

    public String getContainerType() {
        return null;
    }

    public String getDefaultPropertyValues() {
        return "{}";
    }

    /** Default: a labelled drag placeholder. Server-rendered blocks must provide dragHtml or the canvas has no drop zone. */
    public String getBuilderTemplate() {
        return "{\"supportStyle\":false,\"dragHtml\":\"" + json("<div class=\"etb-block etb-drag-placeholder\">" + attr(getLabel()) + "</div>") + "\"}";
    }

    /** Escape for a double-quoted JSON string (a label with a backslash or a control character stays valid JSON). */
    public static String json(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : (value == null ? "" : value).toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public String toText(EmailRenderContext ctx) {
        return "";
    }

    /**
     * Stores properties as a plain map. ExtDefaultPlugin wraps them in DX 9's HashVariableSupportedMap,
     * which resolves hash variables on every get() with no escaping and the thread's "current"
     * assignment. That would bypass EmailRenderContext's escaping and process context, so blocks keep the
     * raw author text and resolve it through the context. Pass a plain Map: copying a
     * HashVariableSupportedMap would itself trigger the resolution.
     */
    @Override
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties == null ? new HashMap<String, Object>() : new HashMap<String, Object>(properties);
    }

    public String getClassName() {
        return getClass().getName();
    }

    /** The property value if it matches {@code allowed} (a bare number taking the unit, see {@link #withUnit}), else {@code fallback}. */
    protected String prop(String name, Pattern allowed, String fallback) {
        String value = withUnit(getPropertyString(name), allowed);
        return (value != null && allowed.matcher(value).matches()) ? value : fallback;
    }

    /**
     * A bare number in a {@link #PX}, {@link #PADDING} or {@link #PERCENT} value means that unit: "14" is 14px,
     * "8 32" is 8px 32px, and "35" is 35%. Anything else is returned trimmed, as it is.
     */
    public static String withUnit(String value, Pattern allowed) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (allowed == PX && v.matches("\\d{1,4}")) {
            return v + "px";
        }
        if (allowed == PERCENT && v.matches("\\d{1,3}")) {
            return v + "%";
        }
        if (allowed == PADDING && v.matches("\\d{1,3}(px)?(\\s+\\d{1,3}(px)?){0,3}")) {
            return v.replaceAll("(\\d+)(px)?", "$1px").replaceAll("\\s+", " ");
        }
        return v;
    }

    /** Escape for a double-quoted MJML attribute. */
    public static String attr(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
