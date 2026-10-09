package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.joget.apps.app.service.AppUtil;
import org.joget.etb.Activator;
import org.joget.etb.api.DefaultEmailBlock;
import org.joget.etb.api.EmailRenderContext;

/** Shared plumbing for the bundled blocks: version, and property options read from /properties/blocks/. */
public abstract class AbstractBlock extends DefaultEmailBlock {

    public String getVersion() {
        return Activator.VERSION;
    }

    public String getDescription() {
        return getLabel() + " block for the Email Template Builder";
    }

    /** Resource name under /properties/blocks/, without ".json". */
    protected abstract String getPropertyResource();

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/blocks/" + getPropertyResource() + ".json", null, true, Activator.MESSAGE_PATH);
    }

    /** A merge field left unresolved, as on the canvas: a Joget hash variable, {row.x} or {var.x}. */
    static final Pattern UNRESOLVED_FIELD = Pattern.compile("#[a-zA-Z]+\\.[^#\\r\\n]+?#|\\{(row|var)\\.[A-Za-z0-9_.]{1,64}(\\?[a-z]+)?\\}");

    /** The rows of a grid property, each as a column-key map; non-map rows are skipped. */
    protected List<Map<?, ?>> gridRows(String name) {
        List<Map<?, ?>> out = new ArrayList<>();
        Object rows = getProperty(name);
        if (rows instanceof Object[]) {
            for (Object row : (Object[]) rows) {
                if (row instanceof Map) {
                    out.add((Map<?, ?>) row);
                }
            }
        }
        return out;
    }

    /** A grid cell as trimmed text ("" when missing). */
    protected static String cell(Map<?, ?> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : v.toString().trim();
    }

    /** A colour property; left empty, the template's theme colour ({@link EmailRenderContext#themeColor}), else the fallback. */
    protected String color(EmailRenderContext ctx, String name, String themeColor, String fallback) {
        String own = prop(name, HEX_COLOR, "");
        if (!own.isEmpty()) {
            return own;
        }
        String theme = themeColor == null ? null : ctx.themeColor(themeColor);
        return theme != null && HEX_COLOR.matcher(theme).matches() ? theme : fallback;
    }
}
