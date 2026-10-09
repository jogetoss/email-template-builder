package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joget.etb.api.EmailRenderContext;

/**
 * Timeline: items down a vertical line, each with a dot, an optional time on the left, a title and a detail line.
 * An agenda (10:00 Welcome), an incident log (14:05 Investigating) or a history. The line is a cell border, so it
 * shows everywhere; classic Outlook leaves out the dots (it ignores the negative margin that centres them).
 */
public class TimelineBlock extends AbstractBlock {

    public String getName() {
        return "Email Timeline Block";
    }

    public String getLabel() {
        return "Timeline";
    }

    public String getIcon() {
        return "<i class=\"fas fa-stream\"></i>";
    }

    public String getCategory() {
        return CATEGORY_COMPONENTS;
    }

    protected String getPropertyResource() {
        return "TimelineBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"items\":[{\"time\":\"10:00\",\"title\":\"Welcome\",\"detail\":\"Priya, 5 min\"},{\"time\":\"10:05\",\"title\":\"Roadmap\",\"detail\":\"Sam, 20 min\"},"
                + "{\"time\":\"10:25\",\"title\":\"Next steps\",\"detail\":\"Everyone, 15 min\"}],\"color\":\"\",\"lineColor\":\"#e5e7eb\",\"filledDots\":\"\","
                + "\"titleColor\":\"\",\"detailColor\":\"#6b7280\",\"timeWidth\":\"56px\",\"fontSize\":\"15px\",\"padding\":\"8px 32px\"}";
    }

    /** One item: time, title and detail as HTML, and as text. */
    static final class Item {
        final String time;
        final String title;
        final String detail;
        final String text;
        /** Its own dot colour, or null for the block's. */
        final String color;

        Item(String time, String title, String detail, String text, String color) {
            this.time = time;
            this.title = title;
            this.detail = detail;
            this.text = text;
            this.color = color;
        }
    }

    private List<Item> items;

    List<Item> items(EmailRenderContext ctx) {
        if (items != null) {
            return items;
        }
        items = new ArrayList<>();
        for (Map<?, ?> row : gridRows("items")) {
            String time = cell(row, "time");
            String title = cell(row, "title");
            String detail = cell(row, "detail");
            if (time.isEmpty() && title.isEmpty() && detail.isEmpty()) {
                continue;
            }
            String head = (ctx.plain(time).trim() + " " + ctx.plain(title).trim()).trim();
            String more = ctx.plain(detail).trim();
            String own = cell(row, "color");
            items.add(new Item(ctx.html(time), ctx.html(title), ctx.html(detail), head.isEmpty() ? more : more.isEmpty() ? head : head + " - " + more,
                    HEX_COLOR.matcher(own).matches() ? own : null));
        }
        return items;
    }

    public String toMjml(EmailRenderContext ctx) {
        List<Item> list = items(ctx);
        String padding = prop("padding", PADDING, "8px 32px");
        if (list.isEmpty()) {
            if (!ctx.isPreview()) {
                ctx.warn("Timeline has no items; it was left out.");
                return "";
            }
            return "<mj-text align=\"center\" color=\"#7b8794\" font-size=\"13px\" padding=\"" + padding + "\">Timeline: add items in its properties</mj-text>";
        }
        String accent = color(ctx, "color", "link", "#2563eb");
        String line = prop("lineColor", HEX_COLOR, "#e5e7eb");
        String card = color(ctx, "", "content", "#ffffff");
        boolean filled = "true".equals(getPropertyString("filledDots"));
        String timeFont = "true".equals(getPropertyString("monoTime")) ? CalloutBlock.MONO : "";
        String titleColor = color(ctx, "titleColor", "text", "#1f2937");
        String detailColor = prop("detailColor", HEX_COLOR, "#6b7280");
        boolean times = false;
        for (Item i : list) {
            times |= !i.time.trim().isEmpty();
        }
        String timeWidth = prop("timeWidth", PX, "56px").replace("px", "");

        StringBuilder m = new StringBuilder("<mj-text padding=\"").append(padding).append("\" font-size=\"").append(prop("fontSize", PX, "15px")).append("\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
        for (int k = 0; k < list.size(); k++) {
            Item i = list.get(k);
            boolean last = k == list.size() - 1;
            String dot = i.color == null ? accent : i.color;
            m.append("<tr>");
            if (times) {
                m.append("<td width=\"").append(timeWidth).append("\" valign=\"top\" style=\"padding:1px 12px 18px 0;color:").append(accent)
                        .append(";font-size:13px;white-space:nowrap;").append(timeFont).append("\"><strong>").append(i.time).append("</strong></td>");
            }
            // the line runs down the left border of this cell; the last item's line stops at its dot
            m.append("<td width=\"22\" valign=\"top\" style=\"width:22px;border-left:2px solid ").append(last ? card : line).append(";\">")
                    .append("<span style=\"display:inline-block;width:12px;height:12px;border-radius:50%;background-color:").append(filled ? dot : card)
                    .append(";border:3px solid ").append(dot).append(";margin-left:-9px;\"></span></td>")
                    .append("<td valign=\"top\" style=\"padding:0 0 ").append(last ? "0" : "18px").append(" 0;line-height:1.35;\">");
            if (!i.title.trim().isEmpty()) {
                m.append("<strong style=\"color:").append(titleColor).append(";\">").append(i.title).append("</strong>");
            }
            if (!i.detail.trim().isEmpty()) {
                m.append(i.title.trim().isEmpty() ? "" : "<br>").append("<span style=\"color:").append(detailColor).append(";font-size:13px;\">").append(i.detail).append("</span>");
            }
            m.append("</td></tr>");
        }
        return m.append("</table></mj-text>").toString();
    }

    public String toText(EmailRenderContext ctx) {
        List<String> out = new ArrayList<>();
        for (Item i : items(ctx)) {
            out.add("- " + i.text);
        }
        return String.join("\n", out);
    }
}
