package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joget.etb.api.EmailRenderContext;
import org.joget.etb.render.UrlPolicy;

/**
 * Datalist Table: the rows of an app datalist, e.g. an order's line items (filter
 * {@code order_id = #form.orders.id#}) or a daily digest (no filter), as a table (mj-table) or as a stack
 * of cards (title, link, subtitle, "label: value" details and a body). Filters are bound as query
 * parameters. The datalist is read as the process's user, so its permission rules apply. The canvas shows
 * the columns with placeholder rows; Preview, Send test and real sends run the query.
 *
 * <p>Row values are data, never author text: they are escaped by {@link DatalistSource} and are never
 * run through merge-field resolution, so a record containing {@code #envVariable.x#} prints as text.</p>
 */
public class DatalistTableBlock extends AbstractBlock {

    /** Hard cap: every row adds to the HTML, and Gmail clips emails over about 102 KB. */
    static final int MAX_ROWS = 100;

    private DatalistSource.Table table;
    private String emptyText;
    private boolean cards;
    private List<String> details;
    private boolean sampleMode;

    public String getName() {
        return "Email Datalist Table Block";
    }

    public String getLabel() {
        return "Datalist Table";
    }

    public String getIcon() {
        return "<i class=\"fas fa-table\"></i>";
    }

    @Override
    public String getCategory() {
        return CATEGORY_DATA;
    }

    protected String getPropertyResource() {
        return "DatalistTableBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"layout\":\"table\",\"maxRows\":\"20\",\"cellMode\":\"text\",\"emptyText\":\"Nothing to show.\",\"headerBackground\":\"#f1f5f9\","
                + "\"headerTextColor\":\"#1f2937\",\"borderColor\":\"#e5e7eb\",\"striped\":\"true\",\"fontSize\":\"14px\",\"padding\":\"8px 32px\","
                + "\"cardBackground\":\"#f8fafc\",\"cardRadius\":\"6px\",\"cardGap\":\"12px\",\"titleColor\":\"#0b5cad\"}";
    }

    public String toMjml(EmailRenderContext ctx) {
        resolve(ctx);
        String padding = prop("padding", PADDING, "8px 32px");
        if (table.headers.isEmpty()) {
            if (!ctx.isPreview()) {
                return "";
            }
            String what = getPropertyString("listId").isEmpty() ? "choose a datalist in its properties"
                    : table.problem != null ? attr(table.problem) : "no columns to show";
            return "<mj-text align=\"center\" color=\"#7b8794\" font-size=\"13px\" padding=\"" + padding + "\">Datalist Table: " + what + "</mj-text>";
        }
        List<List<String>> rows = table.rows;
        List<List<String>> raw = table.raw;
        if (ctx.isPreview() && !sampleMode) {
            rows = new ArrayList<>();
            raw = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                List<String> r = new ArrayList<>();
                List<String> v = new ArrayList<>();
                for (int c = 0; c < table.headers.size(); c++) {
                    r.add("<span style=\"color:#9aa5b1;\">" + table.headers.get(c) + "</span>");
                    v.add("");
                }
                rows.add(r);
                raw.add(v);
            }
        }
        return cards ? cardsMjml(rows, raw, padding) : tableMjml(rows, padding);
    }

    private String tableMjml(List<List<String>> rows, String padding) {
        String border = prop("borderColor", HEX_COLOR, "#e5e7eb");
        String cellStyle = "padding:8px 10px;border-bottom:1px solid " + border + ";text-align:left;vertical-align:top;";
        StringBuilder m = new StringBuilder("<mj-table cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" font-size=\"")
                .append(prop("fontSize", PX, "14px")).append("\" line-height=\"1.4\" padding=\"").append(padding).append("\">");
        m.append("<tr style=\"background-color:").append(prop("headerBackground", HEX_COLOR, "#f1f5f9"))
                .append(";color:").append(prop("headerTextColor", HEX_COLOR, "#1f2937")).append(";\">");
        for (String h : table.headers) {
            m.append("<th style=\"").append(cellStyle).append("font-weight:bold;\">").append(h).append("</th>");
        }
        m.append("</tr>");
        if (rows.isEmpty()) {
            m.append("<tr><td colspan=\"").append(table.headers.size()).append("\" style=\"").append(cellStyle).append("color:#7b8794;\">")
                    .append(emptyText).append("</td></tr>");
        }
        boolean striped = "true".equals(getPropertyString("striped"));
        for (int i = 0; i < rows.size(); i++) {
            m.append(striped && i % 2 == 1 ? "<tr style=\"background-color:#f8fafc;\">" : "<tr>");
            for (String cell : rows.get(i)) {
                m.append("<td style=\"").append(cellStyle).append("\">").append(cell).append("</td>");
            }
            m.append("</tr>");
        }
        return m.append("</mj-table>").toString();
    }

    /**
     * One card per row: a table cell (padding holds in classic Outlook) with the title (optionally linked),
     * a rule, the subtitle, "label: value" details and the body.
     */
    private String cardsMjml(List<List<String>> rows, List<List<String>> raw, String padding) {
        if (rows.isEmpty()) {
            return "<mj-text color=\"#7b8794\" font-size=\"" + prop("fontSize", PX, "14px") + "\" padding=\"" + padding + "\">" + emptyText + "</mj-text>";
        }
        String border = prop("borderColor", HEX_COLOR, "#e5e7eb");
        String gap = prop("cardGap", PX, "12px");
        String box = "width:100%;border-collapse:separate;background-color:" + prop("cardBackground", HEX_COLOR, "#f8fafc")
                + ";border:1px solid " + border + ";border-radius:" + prop("cardRadius", PX, "6px") + ";";
        int title = table.column(getPropertyString("cardTitle"));
        int link = table.column(getPropertyString("cardLink"));
        int subtitle = table.column(getPropertyString("cardSubtitle"));
        int body = table.column(getPropertyString("cardBody"));
        StringBuilder m = new StringBuilder("<mj-text font-size=\"").append(prop("fontSize", PX, "14px"))
                .append("\" line-height=\"1.5\" padding=\"").append(padding).append("\">");
        for (int i = 0; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            m.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"").append(box)
                    .append(i < rows.size() - 1 ? "margin-bottom:" + gap + ";" : "").append("\"><tr><td style=\"padding:14px 16px;\">");
            if (title >= 0) {
                String href = link >= 0 ? safeLink(raw.get(i).get(link)) : "";
                String t = r.get(title);
                m.append("<div style=\"font-weight:bold;font-size:16px;padding-bottom:8px;margin-bottom:10px;border-bottom:1px solid ").append(border)
                        .append(";color:").append(prop("titleColor", HEX_COLOR, "#0b5cad")).append(";\">")
                        .append(href.isEmpty() ? t : "<a href=\"" + href + "\" style=\"color:inherit;text-decoration:none;\">" + t + "</a>")
                        .append("</div>");
            }
            if (subtitle >= 0 && !r.get(subtitle).isEmpty()) {
                m.append("<div style=\"font-weight:bold;margin-bottom:6px;\">").append(r.get(subtitle)).append("</div>");
            }
            for (String d : details) {
                int c = table.column(d);
                if (c >= 0 && c != title && c != link && c != subtitle && c != body) {
                    m.append("<div><span style=\"color:#6b7280;\">").append(table.headers.get(c)).append(":</span> ").append(r.get(c)).append("</div>");
                }
            }
            if (body >= 0) {
                m.append("<div style=\"margin-top:6px;\">").append(r.get(body)).append("</div>");
            }
            m.append("</td></tr></table>");
        }
        return m.append("</mj-text>").toString();
    }

    /** A row value used as a link: checked against the scheme allowlist (never merge-resolved), else no link. */
    static String safeLink(String value) {
        String v = value == null ? "" : value.trim();
        return UrlPolicy.isAllowed(v) ? attr(v) : "";
    }

    public String toText(EmailRenderContext ctx) {
        resolve(ctx);
        if (table.headers.isEmpty() || (ctx.isPreview() && !sampleMode)) {
            return "";
        }
        if (cards) {
            int title = table.column(getPropertyString("cardTitle"));
            int link = table.column(getPropertyString("cardLink"));
            List<String> blocks = new ArrayList<>();
            for (int i = 0; i < table.rows.size(); i++) {
                List<String> r = table.rows.get(i);
                List<String> card = new ArrayList<>();
                for (int c = 0; c < r.size(); c++) {
                    if (c == link) {
                        continue;
                    }
                    String href = c == title && link >= 0 ? safeLink(table.raw.get(i).get(link)) : "";
                    String value = href.isEmpty() ? r.get(c) : "<a href=\"" + href + "\">" + r.get(c) + "</a>";
                    boolean labelled = !isCardField(table.names.get(c));
                    card.add(ctx.text(labelled ? table.headers.get(c) + ": " + value : value));
                }
                blocks.add(String.join("\n", card));
            }
            return blocks.isEmpty() ? ctx.text(emptyText) : String.join("\n\n", blocks);
        }
        List<String> lines = new ArrayList<>();
        lines.add(ctx.text(String.join(" | ", table.headers)));
        if (table.rows.isEmpty()) {
            lines.add(ctx.text(emptyText));
        }
        for (List<String> r : table.rows) {
            lines.add(ctx.text(String.join(" | ", r)));
        }
        return String.join("\n", lines);
    }

    private boolean isCardField(String name) {
        return name.equals(getPropertyString("cardTitle")) || name.equals(getPropertyString("cardLink"))
                || name.equals(getPropertyString("cardSubtitle")) || name.equals(getPropertyString("cardBody"));
    }

    private void resolve(EmailRenderContext ctx) {
        if (table != null) {
            return;
        }
        emptyText = ctx.html(getPropertyString("emptyText"));
        cards = "cards".equals(getPropertyString("layout"));
        details = new ArrayList<>();
        for (String c : getPropertyString("columns").split(";")) {
            if (!c.trim().isEmpty()) {
                details.add(c.trim());
            }
        }
        String listId = getPropertyString("listId");
        if (listId.isEmpty()) {
            DatalistSource.Table sample = ctx.isBuilder() ? DatalistSource.sample(getPropertyString("sampleRows")) : new DatalistSource.Table();
            if (!sample.headers.isEmpty()) {
                // a starter's sample rows: the builder only, until a datalist is chosen
                table = sample;
                sampleMode = true;
                if (cards && details.isEmpty()) {
                    details = new ArrayList<>(table.names);
                }
                if (!ctx.isPreview()) {
                    ctx.warn("Datalist Table shows sample rows until you choose a datalist; a real send leaves it out.");
                }
                return;
            }
            table = new DatalistSource.Table();
            if (!ctx.isPreview()) {
                ctx.warn("Datalist Table has no datalist chosen; it was left out.");
            }
            return;
        }
        // cards: the card fields first, then the detail columns; nothing chosen means every column as a detail
        List<String> columns = details;
        if (cards) {
            Set<String> wanted = new LinkedHashSet<>();
            for (String f : new String[]{"cardTitle", "cardLink", "cardSubtitle", "cardBody"}) {
                if (!getPropertyString(f).trim().isEmpty()) {
                    wanted.add(getPropertyString(f).trim());
                }
            }
            wanted.addAll(details);
            columns = new ArrayList<>(wanted);
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
                        ctx.warn("Datalist Table filter \"" + f + "\" is not a field name; it was ignored.");
                        continue;
                    }
                    // a bound query parameter: resolved without escaping
                    filters.put(f, ctx.plain(value == null ? "" : value.toString()));
                }
            }
        }
        int max = 20;
        try {
            max = Math.max(1, Math.min(MAX_ROWS, Integer.parseInt(getPropertyString("maxRows").trim())));
        } catch (NumberFormatException e) {
            // keep the default
        }
        Map<String, List<String>> params = DatalistSource.params(getProperty("filterParams"), ctx, "Datalist Table");
        try {
            table = DatalistSource.instance.read(ctx.getAppDef(), listId, columns, filters, params, getPropertyString("sortBy"),
                    "true".equals(getPropertyString("sortDesc")), max, DatalistSource.CellMode.of(getPropertyString("cellMode")), !ctx.isPreview());
        } catch (RuntimeException e) {
            table = new DatalistSource.Table();
            table.problem = "Datalist \"" + listId + "\" failed: " + e.getMessage();
        }
        if (cards && columns.isEmpty()) {
            // no card fields and no columns: every column as a detail line
            details = new ArrayList<>(table.names);
        }
        if (table.problem != null && !ctx.isPreview()) {
            ctx.warn(table.problem);
        }
        // also when rows came back with a problem (some dropped because the data source ignores filters)
        if (ctx.isBuilder() && !ctx.isPreview() && (table.problem == null || !table.rows.isEmpty())) {
            DatalistSource.note(ctx, "Datalist Table", listId, table, filters, params, max);
        }
    }
}
