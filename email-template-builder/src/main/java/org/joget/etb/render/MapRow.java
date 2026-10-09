package org.joget.etb.render;

import java.util.Map;
import org.joget.commons.util.StringUtil;
import org.joget.etb.api.EmailRow;

/** A plain map as {row.NAME} fields, e.g. the row an Iterator Process Tool passes to the tool it runs ("data"). */
public final class MapRow implements EmailRow {

    private final Map<?, ?> values;

    public MapRow(Map<?, ?> values) {
        this.values = values;
    }

    public String text(String name) {
        return raw(name);
    }

    public String html(String name) {
        String v = raw(name);
        return v == null ? null : StringUtil.escapeString(v, StringUtil.TYPE_HTML, null);
    }

    public String raw(String name) {
        if (!values.containsKey(name)) {
            return null;
        }
        Object v = values.get(name);
        return v == null ? "" : v.toString();
    }
}
