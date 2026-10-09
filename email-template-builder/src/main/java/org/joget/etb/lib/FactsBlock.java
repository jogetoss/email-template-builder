package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.joget.etb.api.EmailRenderContext;

/**
 * Facts list: label and value rows, e.g. STATUS / UPDATED BY / NEEDED BY, or a memo's TO / FROM / RE. Values are
 * usually fields; "Hide rows with an empty value" drops a row whose value came out empty. Labels in small spaced
 * capitals, plain or monospaced; the value beside the label or on the right; optional dividers.
 */
public class FactsBlock extends AbstractBlock {

    static final Pattern LABEL_STYLE = Pattern.compile("caps|plain|mono");

    public String getName() {
        return "Email Facts List Block";
    }

    public String getLabel() {
        return "Facts list";
    }

    public String getIcon() {
        return "<i class=\"fas fa-list-ul\"></i>";
    }

    public String getCategory() {
        return CATEGORY_COMPONENTS;
    }

    protected String getPropertyResource() {
        return "FactsBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"facts\":[{\"label\":\"Status\",\"value\":\"#form.request.status#\"},{\"label\":\"Updated by\",\"value\":\"#form.request.updatedBy#\"},"
                + "{\"label\":\"Needed by\",\"value\":\"#form.request.date#\"}],\"labelStyle\":\"caps\",\"valueAlign\":\"right\",\"labelWidth\":\"35%\","
                + "\"dividers\":\"true\",\"dividerColor\":\"#eef2f7\",\"labelColor\":\"#94a3b8\",\"valueColor\":\"\",\"hideEmpty\":\"\",\"fontSize\":\"14px\","
                + "\"padding\":\"8px 32px\"}";
    }

    /** One row: label and value as HTML, and as text. */
    static final class Fact {
        final String label;
        final String value;
        final String text;

        Fact(String label, String value, String text) {
            this.label = label;
            this.value = value;
            this.text = text;
        }
    }

    private List<Fact> facts;

    List<Fact> facts(EmailRenderContext ctx) {
        if (facts != null) {
            return facts;
        }
        facts = new ArrayList<>();
        boolean hideEmpty = "true".equals(getPropertyString("hideEmpty"));
        for (Map<?, ?> row : gridRows("facts")) {
            String label = cell(row, "label");
            String value = cell(row, "value");
            if (label.isEmpty() && value.isEmpty()) {
                continue;
            }
            String plainValue = ctx.plain(value).trim();
            // on the canvas a field is still unresolved, so it always shows
            if (hideEmpty && plainValue.isEmpty()) {
                continue;
            }
            String plainLabel = ctx.plain(label).trim();
            facts.add(new Fact(ctx.html(label), ctx.html(value), plainLabel + (plainLabel.isEmpty() ? "" : ": ") + plainValue));
        }
        return facts;
    }

    public String toMjml(EmailRenderContext ctx) {
        List<Fact> list = facts(ctx);
        String padding = prop("padding", PADDING, "8px 32px");
        if (list.isEmpty()) {
            if (!ctx.isPreview()) {
                if (gridRows("facts").isEmpty()) {
                    ctx.warn("Facts list has no rows; it was left out.");
                }
                return "";
            }
            return "<mj-text align=\"center\" color=\"#7b8794\" font-size=\"13px\" padding=\"" + padding + "\">Facts list: add rows in its properties</mj-text>";
        }
        String style = prop("labelStyle", LABEL_STYLE, "caps");
        boolean right = !"left".equals(getPropertyString("valueAlign"));
        String labelWidth = prop("labelWidth", PERCENT, "35%");
        boolean dividers = "true".equals(getPropertyString("dividers"));
        String dividerColor = prop("dividerColor", HEX_COLOR, "#eef2f7");
        String labelColor = prop("labelColor", HEX_COLOR, "#94a3b8");
        String valueColor = color(ctx, "valueColor", "text", "#1f2937");
        String labelCss = "caps".equals(style) ? "font-size:11px;letter-spacing:1px;text-transform:uppercase;"
                : "mono".equals(style) ? CalloutBlock.MONO + "font-size:12px;letter-spacing:1px;text-transform:uppercase;" : "";
        String line = dividers ? "border-bottom:1px solid " + dividerColor + ";" : "";
        // rows without dividers sit closer, like a memo header
        String gap = dividers ? "10px" : "5px";

        StringBuilder m = new StringBuilder("<mj-text padding=\"").append(padding).append("\" font-size=\"").append(prop("fontSize", PX, "14px")).append("\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
        for (Fact f : list) {
            // baseline: small capital labels line up with the larger value text beside them
            // the label column keeps its width in both layouts: without it a long value squeezed labels to a letter per line
            m.append("<tr><td valign=\"baseline\" width=\"").append(labelWidth).append("\"").append(" style=\"padding:").append(gap).append(" 12px ").append(gap).append(" 0;").append(line)
                    .append("color:").append(labelColor).append(";").append(labelCss)
                    // a label wraps between words, never inside one ("FRO/M" on a phone): its column grows to fit the longest word
                    .append("word-break:normal;overflow-wrap:normal;\">").append(f.label).append("</td>")
                    .append("<td valign=\"baseline\"").append(right ? " align=\"right\"" : "").append(" style=\"padding:").append(gap).append(" 0;").append(line)
                    .append("color:").append(valueColor).append(";\">").append(f.value).append("</td></tr>");
        }
        return m.append("</table></mj-text>").toString();
    }

    public String toText(EmailRenderContext ctx) {
        List<String> out = new ArrayList<>();
        for (Fact f : facts(ctx)) {
            out.add(f.text);
        }
        return String.join("\n", out);
    }
}
