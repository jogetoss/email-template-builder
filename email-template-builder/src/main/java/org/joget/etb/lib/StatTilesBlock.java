package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joget.etb.api.EmailRenderContext;

/**
 * Stat tiles: up to four big numbers side by side, each with a caption and its own colours. A number may be a
 * field ({var.tasksDone}, #form.report.total#). The tiles are cells of one table row, so they are always the same
 * height; they stay side by side on phones, so keep the numbers short.
 */
public class StatTilesBlock extends AbstractBlock {

    static final int MAX_TILES = 4;

    public String getName() {
        return "Email Stat Tiles Block";
    }

    public String getLabel() {
        return "Stat tiles";
    }

    public String getIcon() {
        return "<i class=\"fas fa-th-large\"></i>";
    }

    public String getCategory() {
        return CATEGORY_COMPONENTS;
    }

    protected String getPropertyResource() {
        return "StatTilesBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"tiles\":[{\"number\":\"128\",\"caption\":\"tasks done\",\"background\":\"#eef2ff\",\"color\":\"#4338ca\"},"
                + "{\"number\":\"94%\",\"caption\":\"on time\",\"background\":\"#dcfce7\",\"color\":\"#15803d\"},"
                + "{\"number\":\"12\",\"caption\":\"overdue\",\"background\":\"#fff1e6\",\"color\":\"#c2410c\"}],"
                + "\"numberSize\":\"30px\",\"fontSize\":\"13px\",\"captionColor\":\"\",\"borderColor\":\"\",\"radius\":\"14px\",\"tilePadding\":\"18px\","
                + "\"gap\":\"12px\",\"align\":\"left\",\"padding\":\"16px 32px\"}";
    }

    /** One tile: number and caption as HTML, number and caption as text, and its colours. */
    static final class Tile {
        final String icon;
        final String number;
        final String caption;
        final String text;
        final String background;
        final String color;

        Tile(String icon, String number, String caption, String text, String background, String color) {
            this.icon = icon;
            this.number = number;
            this.caption = caption;
            this.text = text;
            this.background = background;
            this.color = color;
        }
    }

    private List<Tile> tiles;

    List<Tile> tiles(EmailRenderContext ctx) {
        if (tiles != null) {
            return tiles;
        }
        tiles = new ArrayList<>();
        String accent = color(ctx, "", "link", "#2563eb");
        for (Map<?, ?> row : gridRows("tiles")) {
            String number = cell(row, "number");
            String caption = cell(row, "caption");
            if (number.isEmpty() && caption.isEmpty()) {
                continue;
            }
            if (tiles.size() == MAX_TILES) {
                ctx.warn("Stat tiles shows at most " + MAX_TILES + " tiles; the rest were left out.");
                break;
            }
            String bg = cell(row, "background");
            String fg = cell(row, "color");
            tiles.add(new Tile(ctx.html(cell(row, "icon")).trim(), ctx.html(number), ctx.html(caption), (ctx.plain(number).trim() + " " + ctx.plain(caption).trim()).trim(),
                    HEX_COLOR.matcher(bg).matches() ? bg : "#f1f5f9", HEX_COLOR.matcher(fg).matches() ? fg : accent));
        }
        return tiles;
    }

    public String toMjml(EmailRenderContext ctx) {
        List<Tile> list = tiles(ctx);
        String padding = prop("padding", PADDING, "16px 32px");
        if (list.isEmpty()) {
            if (!ctx.isPreview()) {
                ctx.warn("Stat tiles has no tiles; it was left out.");
                return "";
            }
            return "<mj-text align=\"center\" color=\"#7b8794\" font-size=\"13px\" padding=\"" + padding + "\">Stat tiles: add tiles in its properties</mj-text>";
        }
        int n = list.size();
        String numberSize = prop("numberSize", PX, "30px");
        String captionColor = color(ctx, "captionColor", "text", "#475569");
        String border = prop("borderColor", HEX_COLOR, "");
        String radius = prop("radius", PX, "14px");
        String tilePadding = prop("tilePadding", PX, "18px");
        int gap = Math.min(40, Integer.parseInt(prop("gap", PX, "12px").replace("px", "")));
        String align = prop("align", ALIGN, "left");
        // on phones the tiles stack (the email's @media rule for .etb-tile), unless kept side by side for short numbers
        boolean stack = !"true".equals(getPropertyString("keepSideBySide"));
        // tiles share the row; the gutters are fixed-width cells, so each tile gets an equal share of what is left
        int share = Math.max(1, (100 - 2 * (n - 1)) / n);

        StringBuilder m = new StringBuilder("<mj-text padding=\"").append(padding).append("\" font-size=\"").append(prop("fontSize", PX, "13px")).append("\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:separate;\"><tr>");
        for (int i = 0; i < n; i++) {
            Tile t = list.get(i);
            if (i > 0 && gap > 0) {
                // stacked, the gutter becomes a gap of the same height between tiles
                m.append("<td width=\"").append(gap).append("\"").append(stack ? " class=\"etb-tile-gap\"" : "").append(" style=\"width:").append(gap)
                        .append("px;").append(stack ? "height:" + gap + "px;" : "").append("font-size:0;line-height:0;\">&nbsp;</td>");
            }
            m.append("<td width=\"").append(share).append("%\"").append(stack ? " class=\"etb-tile\"" : "").append(" valign=\"top\" align=\"").append(align).append("\" style=\"background-color:").append(t.background)
                    .append(";border-radius:").append(radius).append(";padding:").append(tilePadding).append(";")
                    .append(border.isEmpty() ? "" : "border:1px solid " + border + ";").append("\">")
                    .append(t.icon.isEmpty() ? "" : "<p style=\"margin:0 0 4px 0;font-size:26px;line-height:1.2;\">" + t.icon + "</p>")
                    .append("<p style=\"margin:0;font-size:").append(numberSize).append(";line-height:1.15;color:").append(t.color).append(";\"><strong>")
                    .append(t.number).append("</strong></p>")
                    .append("<p style=\"margin:4px 0 0 0;line-height:1.35;color:").append(captionColor).append(";\">").append(t.caption).append("</p></td>");
        }
        return m.append("</tr></table></mj-text>").toString();
    }

    public String toText(EmailRenderContext ctx) {
        List<String> out = new ArrayList<>();
        for (Tile t : tiles(ctx)) {
            out.add(t.text);
        }
        return String.join("\n", out);
    }
}
