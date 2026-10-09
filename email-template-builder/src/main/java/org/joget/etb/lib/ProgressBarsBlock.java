package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joget.etb.api.EmailRenderContext;

/**
 * Progress bars: one or more labelled bars. A value is a percentage (86, 86%) or a fraction (12/20), typed or
 * from a field such as #form.project.progress# or {row.done}; it is clamped to 0-100. Each bar is a two-cell
 * table (filled, remaining), so it holds in Outlook.
 */
public class ProgressBarsBlock extends AbstractBlock {

    static final Pattern PERCENT_VALUE = Pattern.compile("(-?\\d{1,9}(?:\\.\\d+)?)\\s*%?");
    static final Pattern FRACTION = Pattern.compile("(\\d{1,9}(?:\\.\\d+)?)\\s*/\\s*(\\d{1,9}(?:\\.\\d+)?)");

    public String getName() {
        return "Email Progress Bars Block";
    }

    public String getLabel() {
        return "Progress bars";
    }

    public String getIcon() {
        return "<i class=\"fas fa-tasks\"></i>";
    }

    public String getCategory() {
        return CATEGORY_COMPONENTS;
    }

    protected String getPropertyResource() {
        return "ProgressBarsBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"bars\":[{\"label\":\"Progress\",\"value\":\"60\",\"color\":\"\"}],\"showValue\":\"true\",\"previewValue\":\"60\","
                + "\"color\":\"\",\"trackColor\":\"#e2e8f0\",\"height\":\"8px\",\"rounded\":\"true\",\"labelColor\":\"\",\"fontSize\":\"14px\","
                + "\"padding\":\"8px 32px\"}";
    }

    /** One bar: label (HTML and plain), the filled share, and the text shown beside it. */
    static final class Bar {
        final String html;
        final String plain;
        final int percent;
        final String shown;
        final String color;

        Bar(String html, String plain, int percent, String shown, String color) {
            this.html = html;
            this.plain = plain;
            this.percent = percent;
            this.shown = shown;
            this.color = color;
        }
    }

    private List<Bar> bars;

    public String toMjml(EmailRenderContext ctx) {
        List<Bar> list = bars(ctx);
        String padding = prop("padding", PADDING, "8px 32px");
        if (list.isEmpty()) {
            if (!ctx.isPreview()) {
                ctx.warn("Progress bars has no bars; it was left out.");
                return "";
            }
            return "<mj-text align=\"center\" color=\"#7b8794\" font-size=\"13px\" padding=\"" + padding + "\">Progress bars: add bars in its properties</mj-text>";
        }
        String track = prop("trackColor", HEX_COLOR, "#e2e8f0");
        String label = color(ctx, "labelColor", "text", "#333333");
        String size = prop("fontSize", PX, "14px");
        String height = prop("height", PX, "8px");
        int h = Math.max(1, Math.min(40, Integer.parseInt(height.replace("px", ""))));
        String r = "true".equals(getPropertyString("rounded")) ? (h / 2) + "px" : "0";
        boolean showValue = "true".equals(getPropertyString("showValue"));

        StringBuilder m = new StringBuilder("<mj-text padding=\"").append(padding).append("\" font-size=\"").append(size).append("\">");
        for (int i = 0; i < list.size(); i++) {
            Bar b = list.get(i);
            m.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"").append(i > 0 ? " style=\"margin-top:14px;\"" : "")
                    .append("><tr><td style=\"padding:0 0 6px 0;color:").append(label).append(";line-height:1.3;\">").append(b.html).append("</td>");
            if (showValue) {
                m.append("<td align=\"right\" style=\"padding:0 0 6px 12px;color:").append(label).append(";line-height:1.3;white-space:nowrap;\"><strong>")
                        .append(b.shown).append("</strong></td>");
            }
            m.append("</tr></table>")
                    .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:separate;\"><tr>");
            String cell = "height:" + h + "px;font-size:0;line-height:0;mso-line-height-rule:exactly;";
            if (b.percent > 0) {
                m.append("<td width=\"").append(b.percent).append("%\" height=\"").append(h).append("\" style=\"").append(cell)
                        .append("background-color:").append(b.color).append(";border-radius:").append(b.percent == 100 ? r : r + " 0 0 " + r).append(";\">&nbsp;</td>");
            }
            if (b.percent < 100) {
                m.append("<td height=\"").append(h).append("\" style=\"").append(cell).append("background-color:").append(track)
                        .append(";border-radius:").append(b.percent == 0 ? r : "0 " + r + " " + r + " 0").append(";\">&nbsp;</td>");
            }
            m.append("</tr></table>");
        }
        return m.append("</mj-text>").toString();
    }

    public String toText(EmailRenderContext ctx) {
        List<String> out = new ArrayList<>();
        for (Bar b : bars(ctx)) {
            out.add(b.plain + ": " + b.shown);
        }
        return String.join("\n", out);
    }

    List<Bar> bars(EmailRenderContext ctx) {
        if (bars != null) {
            return bars;
        }
        bars = new ArrayList<>();
        String fill = color(ctx, "color", "link", "#2563eb");
        for (Map<?, ?> row : gridRows("bars")) {
            String authoredLabel = cell(row, "label");
            String authoredValue = cell(row, "value");
            if (authoredLabel.isEmpty() && authoredValue.isEmpty()) {
                continue;
            }
            String plainLabel = ctx.plain(authoredLabel).trim();
            String own = cell(row, "color");
            String barColor = HEX_COLOR.matcher(own).matches() ? own : fill;
            String value = authoredValue.isEmpty() ? "" : ctx.plain(authoredValue).trim();
            if (value.isEmpty() || UNRESOLVED_FIELD.matcher(value).find()) {
                if (ctx.isBuilder()) {
                    value = previewValue();
                    if (!ctx.isPreview() && !authoredValue.isEmpty()) {
                        ctx.warn("Progress bar \"" + plainLabel + "\": the value (" + authoredValue + ") has no value here, so the builder shows " + value + "%.");
                    }
                } else {
                    ctx.warn("Progress bar \"" + plainLabel + "\": the value" + (authoredValue.isEmpty() ? "" : " (" + authoredValue + ")") + " has no value; shown as 0%.");
                    value = "0";
                }
            }
            int percent;
            String shown;
            Matcher f = FRACTION.matcher(value);
            Matcher p = PERCENT_VALUE.matcher(value);
            if (f.matches()) {
                double total = Double.parseDouble(f.group(2));
                percent = total <= 0 ? 0 : clamp(Double.parseDouble(f.group(1)) * 100 / total);
                shown = trim(f.group(1)) + " / " + trim(f.group(2));
            } else if (p.matches()) {
                double v = Double.parseDouble(p.group(1));
                if (v < 0 || v > 100) {
                    ctx.warn("Progress bar \"" + plainLabel + "\": " + value + " is outside 0-100%; shown as " + clamp(v) + "%.");
                }
                percent = clamp(v);
                shown = percent + "%";
            } else {
                ctx.warn("Progress bar \"" + plainLabel + "\": \"" + value + "\" is not a number or a fraction such as 12/20; shown as 0%.");
                percent = 0;
                shown = "0%";
            }
            bars.add(new Bar(ctx.html(authoredLabel), plainLabel, percent, shown, barColor));
        }
        return bars;
    }

    private String previewValue() {
        String v = getPropertyString("previewValue").trim();
        return PERCENT_VALUE.matcher(v).matches() || FRACTION.matcher(v).matches() ? v : "60";
    }

    static int clamp(double v) {
        return (int) Math.round(Math.max(0, Math.min(100, v)));
    }

    private static String trim(String number) {
        return number.contains(".") ? number.replaceAll("\\.?0+$", "") : number;
    }
}
