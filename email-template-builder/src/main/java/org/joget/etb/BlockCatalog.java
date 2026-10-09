package org.joget.etb;

import java.util.Arrays;
import java.util.List;
import org.joget.etb.api.EmailBlock;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The blocks as an AI (or any other client) needs to know them: a short list of every block type and where it may
 * go, and on request each type's properties. Built from the installed blocks themselves (their property pages and
 * defaults), so third-party blocks are described too.
 */
public final class BlockCatalog {

    private BlockCatalog() {
    }

    /** Every block: className, label, category, where it may go ("template" = the email itself) and what it holds. */
    public static JSONArray summary(List<EmailBlock> blocks) {
        JSONArray out = new JSONArray();
        for (EmailBlock b : blocks) {
            JSONObject o = new JSONObject().put("className", b.getClassName()).put("label", b.getLabel()).put("category", b.getCategory())
                    .put("allowedIn", new JSONArray(Arrays.asList(b.getAllowedParent().split(","))));
            if (b.getContainerType() != null) {
                o.put("holdsBlocksAs", b.getContainerType());
            }
            out.put(o);
        }
        return out;
    }

    /** The named blocks' properties (name, label, type, allowed values, default, short description) and defaults. */
    public static JSONArray details(List<EmailBlock> blocks, List<String> classNames) {
        JSONArray out = new JSONArray();
        for (EmailBlock b : blocks) {
            if (!classNames.contains(b.getClassName())) {
                continue;
            }
            JSONObject defaults = new JSONObject();
            try {
                defaults = new JSONObject(b.getDefaultPropertyValues());
            } catch (RuntimeException e) {
                // a block with no or broken defaults: its properties are still listed
            }
            out.put(new JSONObject().put("className", b.getClassName()).put("label", b.getLabel())
                    .put("properties", properties(b.getPropertyOptions(), defaults)).put("defaults", defaults));
        }
        return out;
    }

    /** A block's property pages (Joget's property-editor JSON, lenient syntax) as a flat list. */
    static JSONArray properties(String propertyOptions, JSONObject defaults) {
        JSONArray out = new JSONArray();
        if (propertyOptions == null || propertyOptions.trim().isEmpty()) {
            return out;
        }
        JSONArray pages;
        try {
            pages = new JSONArray(propertyOptions);
        } catch (RuntimeException e) {
            return out;
        }
        for (int i = 0; i < pages.length(); i++) {
            JSONObject page = pages.optJSONObject(i);
            JSONArray props = page == null ? null : page.optJSONArray("properties");
            for (int j = 0; props != null && j < props.length(); j++) {
                JSONObject p = props.optJSONObject(j);
                if (p == null || p.optString("name").isEmpty() || "hidden".equals(p.optString("type"))) {
                    continue;
                }
                String name = p.getString("name");
                JSONObject o = new JSONObject().put("name", name).put("type", p.optString("type")).put("label", p.optString("label"));
                JSONArray options = p.optJSONArray("options");
                if (options != null && !"checkbox".equals(p.optString("type"))) {
                    JSONArray values = new JSONArray();
                    for (int k = 0; k < options.length(); k++) {
                        JSONObject opt = options.optJSONObject(k);
                        if (opt != null) {
                            values.put(opt.opt("value"));
                        }
                    }
                    o.put("values", values);
                }
                if ("checkbox".equals(p.optString("type"))) {
                    o.put("values", new JSONArray().put("true").put(""));
                }
                JSONArray columns = p.optJSONArray("columns");
                if (columns != null) {
                    JSONArray keys = new JSONArray();
                    for (int k = 0; k < columns.length(); k++) {
                        JSONObject c = columns.optJSONObject(k);
                        if (c != null) {
                            keys.put(c.optString("key"));
                        }
                    }
                    o.put("rowKeys", keys);
                }
                Object def = defaults.has(name) ? defaults.get(name) : p.opt("value");
                if (def != null && !"".equals(def)) {
                    o.put("default", def);
                }
                String desc = p.optString("description", "");
                if (!desc.isEmpty()) {
                    o.put("description", desc.length() > 200 ? desc.substring(0, 200) + "..." : desc);
                }
                out.put(o);
            }
        }
        return out;
    }
}
