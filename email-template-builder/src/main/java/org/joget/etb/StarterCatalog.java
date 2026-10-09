package org.joget.etb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joget.commons.util.StringUtil;
import org.joget.etb.render.MergeResolver;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A bundled gallery: the starter templates (Templates button, whole
 * template definitions under /templates/starters/) or the components (Components button, one element each under
 * /templates/components/), plus the sample values that fill merge fields in the thumbnails.
 */
public final class StarterCatalog {

    public static final String DEFAULT_ID = "blank";
    static final String BASE = "/templates/starters/";
    static final String COMPONENTS_BASE = "/templates/components/";
    static final Pattern ID = Pattern.compile("[a-z0-9-]{1,40}");
    /** Any Joget hash variable, including formats with spaces such as #date.dd MMM yyyy#. */
    static final Pattern HASH = Pattern.compile("#([a-zA-Z]+\\.[^#\\r\\n]+?)#");

    public static final class Starter {
        public final String id;
        public final String label;
        public final String category;
        public final String description;

        Starter(JSONObject o) {
            id = o.getString("id");
            label = o.getString("label");
            category = o.getString("category");
            description = o.optString("description", "");
        }
    }

    private final String base;
    private final String listKey;
    private final List<Starter> entries = new ArrayList<>();
    private final Map<String, String> samples = new HashMap<>();
    private final Map<String, String> variableSamples = new HashMap<>();

    /** The starter templates. */
    public StarterCatalog() {
        this(BASE, "starters");
    }

    /** Read once per bundle: the index is bundled and never changes. A race only reads it twice. */
    private static volatile StarterCatalog starters;
    private static volatile StarterCatalog components;

    /** The starter templates, shared. */
    public static StarterCatalog starters() {
        StarterCatalog c = starters;
        if (c == null) {
            starters = c = new StarterCatalog();
        }
        return c;
    }

    /** The components gallery, shared: each entry's JSON is {"element": one block (with its children), "theme": thumbnail theme}. */
    public static StarterCatalog components() {
        StarterCatalog c = components;
        if (c == null) {
            components = c = new StarterCatalog(COMPONENTS_BASE, "components");
        }
        return c;
    }

    StarterCatalog(String base, String listKey) {
        this.base = base;
        this.listKey = listKey;
        JSONObject index = new JSONObject(read("index.json"));
        JSONArray list = index.getJSONArray(listKey);
        for (int i = 0; i < list.length(); i++) {
            entries.add(new Starter(list.getJSONObject(i)));
        }
        JSONObject s = index.optJSONObject("samples");
        if (s != null) {
            for (String key : s.keySet()) {
                samples.put(key, s.getString(key));
            }
        }
        JSONObject v = index.optJSONObject("variables");
        if (v != null) {
            for (String key : v.keySet()) {
                variableSamples.put(key, v.getString(key));
            }
        }
    }

    public List<Starter> getStarters() {
        return java.util.Collections.unmodifiableList(entries);
    }

    /** Categories in first-seen order. */
    public Set<String> getCategories() {
        Set<String> categories = new LinkedHashSet<>();
        for (Starter s : entries) {
            categories.add(s.category);
        }
        return categories;
    }

    /** The gallery listing: id, label, category and description of each starter, in order. */
    public JSONObject indexJson() {
        JSONArray list = new JSONArray();
        for (Starter s : entries) {
            list.put(new JSONObject().put("id", s.id).put("label", s.label).put("category", s.category).put("description", s.description));
        }
        return new JSONObject().put(listKey, list).put("variables", new JSONObject(variableSamples));
    }

    /** Sample values for the starters' template variables ({var.NAME}): gallery thumbnails, and a picked starter's Test data. */
    public Map<String, String> variableSamples() {
        return java.util.Collections.unmodifiableMap(variableSamples);
    }

    /** @return the entry's JSON (a starter's definition, or a component), or null if the id is not bundled */
    public String load(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            return null;
        }
        for (Starter s : entries) {
            if (s.id.equals(id)) {
                return read(id + ".json");
            }
        }
        return null;
    }

    /** Fills merge fields with the gallery's sample values (escaped with the requested format); others stay as-is. */
    public MergeResolver sampleResolver() {
        return (text, format) -> {
            if (text == null) {
                return "";
            }
            Matcher m = HASH.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String v = samples.get(m.group(1));
                String out = v == null ? m.group(0) : (format == null ? v : StringUtil.escapeString(v, format, null));
                m.appendReplacement(sb, Matcher.quoteReplacement(out));
            }
            m.appendTail(sb);
            return sb.toString();
        };
    }

    String read(String name) {
        try (InputStream in = StarterCatalog.class.getResourceAsStream(base + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing gallery resource " + base + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read gallery resource " + base + name, e);
        }
    }

    /**
     * A component shown on its own, for its gallery thumbnail: a template with the component's thumbnail theme
     * and the component as its only element. Null if the id is not a bundled component.
     */
    public String componentPreviewJson(String id) {
        String json = load(id);
        if (json == null) {
            return null;
        }
        JSONObject c = new JSONObject(json);
        JSONObject props = new JSONObject().put("backgroundColor", "#eef2f6").put("contentBackground", "#ffffff").put("width", "600px")
                .put("contentBorderRadius", "8px").put("fontFamily", "Arial, Helvetica, sans-serif").put("linkColor", "#2563eb").put("textColor", "#1f2937");
        JSONObject theme = c.optJSONObject("theme");
        if (theme != null) {
            for (String key : theme.keySet()) {
                props.put(key, theme.get(key));
            }
        }
        return new JSONObject().put("className", EmailTemplateBuilder.class.getName()).put("properties", props)
                .put("elements", new JSONArray().put(c.getJSONObject("element"))).toString();
    }
}
