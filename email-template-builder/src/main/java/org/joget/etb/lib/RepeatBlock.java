package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joget.etb.api.EmailRenderContext;
import org.joget.etb.api.EmailRow;

/**
 * Repeat: the blocks inside it, once per row of an app datalist, each copy optionally drawn as a card
 * (a wrapper with background, border and corner radius). The card holds content blocks and Rows (columns). The blocks use
 * {@code {row.NAME}}, {@code {row.NAME?html}} and {@code {row.NAME?raw}} fields. A Datalist Table inside,
 * filtered by {@code {row.id}}, gives each card its own list (e.g. a task's changes).
 *
 * <p>Top level only: every row is its own section. Filters, the row cap and the process-user rule are the
 * Datalist Table's ({@link DatalistSource}).</p>
 */
public class RepeatBlock extends AbstractBlock {

    /** Set only by the builder's real-rows strip: draw this many of the fetched rows. Ignored when sending. */
    public static final String CANVAS_ROWS = "canvasRows";

    private DatalistSource.Table table;

    public String getName() {
        return "Email Repeat Block";
    }

    public String getLabel() {
        return "Repeat";
    }

    public String getIcon() {
        return "<i class=\"fas fa-layer-group\"></i>";
    }

    @Override
    public String getCategory() {
        return CATEGORY_DATA;
    }

    @Override
    public boolean isContainer() {
        return true;
    }

    @Override
    public String getContainerType() {
        return "repeat";
    }

    /** Each row is a section, so a Repeat can't sit inside a column. */
    @Override
    public String getAllowedParent() {
        return PARENT_TEMPLATE;
    }

    @Override
    public boolean isSectionLevel() {
        return true;
    }

    protected String getPropertyResource() {
        return "RepeatBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"maxRows\":\"20\",\"emptyText\":\"Nothing to show.\",\"card\":\"true\",\"cardBackground\":\"#f8fafc\","
                + "\"borderColor\":\"#e5e7eb\",\"cardRadius\":\"6px\",\"cardGap\":\"12px\",\"cardPadding\":\"6px 0px\",\"padding\":\"12px 24px\"}";
    }

    /** Dropped only into the email itself (data-cbuilder-toplevel); content blocks and Rows drop inside it. */
    @Override
    public String getBuilderTemplate() {
        String text = "{\"className\":\"" + TextBlock.class.getName() + "\",\"properties\":{\"html\":\"<p><strong>{row.id}</strong></p>\",\"padding\":\"8px 16px\"}}";
        return new org.json.JSONObject(super.getBuilderTemplate())
                .put("parentContainerAttr", "toplevel")
                .put("childsContainerAttr", "elements")
                .put("childsDataHolder", "elements")
                .put("elements", new org.json.JSONArray("[" + text + "]"))
                .toString();
    }

    public String toMjml(EmailRenderContext ctx) {
        resolve(ctx);
        String[] side = HeroBlock.sides(prop("padding", PADDING, "12px 24px"));
        if (ctx.isPreview()) {
            // the design once, fields as written (the builder canvas draws its own card around the dropped blocks)
            return card(side, side[0], "0px", ctx.childSections(null));
        }
        String gap = prop("cardGap", PX, "12px");
        if (table.records.isEmpty()) {
            if (getPropertyString("listId").isEmpty()) {
                return "";
            }
            return inset(side, side[0], side[2]) + "<mj-section padding=\"0px\"><mj-column><mj-text color=\"#7b8794\" padding=\"8px 0px\">"
                    + ctx.html(getPropertyString("emptyText")) + "</mj-text></mj-column></mj-section></mj-wrapper>";
        }
        StringBuilder m = new StringBuilder();
        List<EmailRow> rows = shown(ctx);
        for (int i = 0; i < rows.size(); i++) {
            // the top padding above the first card, the gap between cards, the bottom padding after the last
            m.append(card(side, i == 0 ? side[0] : "0px", i < rows.size() - 1 ? gap : side[2], ctx.childSections(rows.get(i))));
        }
        return m.toString();
    }

    /**
     * One card: an inset wrapper (the block's side padding and the gap below) around the card wrapper
     * (background, border, radius) around the row's sections. MJML discourages a wrapper inside a wrapper
     * (the email's own card is one), but mjml4j renders it as nested tables, Outlook's included.
     */
    private String card(String[] side, String top, String bottom, String sections) {
        String inner = "true".equals(getPropertyString("card"))
                ? "<mj-wrapper background-color=\"" + prop("cardBackground", HEX_COLOR, "#f8fafc") + "\" border=\"1px solid "
                        + prop("borderColor", HEX_COLOR, "#e5e7eb") + "\" border-radius=\"" + prop("cardRadius", PX, "6px") + "\" padding=\"" + prop("cardPadding", PADDING, "6px 0px") + "\">"
                : "<mj-wrapper padding=\"0px\">";
        return inset(side, top, bottom) + inner + sections + "</mj-wrapper></mj-wrapper>";
    }

    private static String inset(String[] side, String top, String bottom) {
        return "<mj-wrapper padding=\"" + top + " " + side[1] + " " + bottom + " " + side[3] + "\">";
    }

    public String toText(EmailRenderContext ctx) {
        resolve(ctx);
        if (ctx.isPreview()) {
            return "";
        }
        if (table.records.isEmpty()) {
            return getPropertyString("listId").isEmpty() ? "" : ctx.text(ctx.html(getPropertyString("emptyText")));
        }
        List<String> out = new ArrayList<>();
        for (EmailRow r : shown(ctx)) {
            out.add(ctx.childrenText(r));
        }
        return String.join("\n\n", out);
    }

    /** The rows to draw: all of them, or in the builder's real-rows strip the first few. */
    private List<EmailRow> shown(EmailRenderContext ctx) {
        String n = getPropertyString(CANVAS_ROWS);
        if (ctx.isBuilder() && n != null && n.matches("\\d{1,2}")) {
            int k = Math.max(1, Integer.parseInt(n));
            return table.records.size() > k ? table.records.subList(0, k) : table.records;
        }
        return table.records;
    }

    private void resolve(EmailRenderContext ctx) {
        if (table != null) {
            return;
        }
        String listId = getPropertyString("listId");
        if (ctx.isPreview()) {
            table = new DatalistSource.Table();
            return;
        }
        if (listId.isEmpty()) {
            table = ctx.isBuilder() ? DatalistSource.sample(getPropertyString("sampleRows")) : new DatalistSource.Table();
            if (!table.records.isEmpty()) {
                // a starter's sample rows: the builder only, until a datalist is chosen
                ctx.warn("Repeat shows sample rows until you choose a datalist; a real send leaves it out.");
            } else {
                ctx.warn("Repeat has no datalist chosen; it was left out.");
            }
            return;
        }
        Map<String, String> filters = new LinkedHashMap<>();
        Object rows = getProperty("filters");
        if (rows instanceof Object[]) {
            for (Object row : (Object[]) rows) {
                if (row instanceof Map) {
                    Object field = ((Map<?, ?>) row).get("field");
                    Object value = ((Map<?, ?>) row).get("value");
                    String f = field == null ? "" : field.toString().trim();
                    if (f.isEmpty()) {
                        continue;
                    }
                    if (!DatalistSource.FIELD.matcher(f).matches()) {
                        ctx.warn("Repeat filter \"" + f + "\" is not a field name; it was ignored.");
                        continue;
                    }
                    filters.put(f, ctx.plain(value == null ? "" : value.toString()));
                }
            }
        }
        int max = 20;
        try {
            max = Math.max(1, Math.min(DatalistTableBlock.MAX_ROWS, Integer.parseInt(getPropertyString("maxRows").trim())));
        } catch (NumberFormatException e) {
            // keep the default
        }
        Map<String, java.util.List<String>> params = DatalistSource.params(getProperty("filterParams"), ctx, "Repeat");
        try {
            table = DatalistSource.instance.read(ctx.getAppDef(), listId, new ArrayList<String>(), filters, params, getPropertyString("sortBy"),
                    "true".equals(getPropertyString("sortDesc")), max, DatalistSource.CellMode.TEXT, true);
        } catch (RuntimeException e) {
            table = new DatalistSource.Table();
            table.problem = "Datalist \"" + listId + "\" failed: " + e.getMessage();
        }
        if (table.problem != null) {
            ctx.warn(table.problem);
        }
        // also when rows came back with a problem (some dropped because the data source ignores filters)
        if (ctx.isBuilder() && (table.problem == null || !table.records.isEmpty())) {
            DatalistSource.note(ctx, "Repeat", listId, table, filters, params, max);
        }
    }
}
