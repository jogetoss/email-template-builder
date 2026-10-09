package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.joget.etb.api.EmailRenderContext;

/**
 * Steps: a horizontal status tracker (circles joined by a line, a label under each). The current step is a number
 * or a field: a value such as #form.request.status# is matched against each step's label and its "Also matches"
 * values, so one template shows the right stage in every email. Tables only, so it holds in Outlook (where the
 * circles are square).
 */
public class StepsBlock extends AbstractBlock {

    static final int MAX_STEPS = 7;
    private static final String CHECK = "&#10003;";

    public String getName() {
        return "Email Steps Block";
    }

    public String getLabel() {
        return "Steps";
    }

    public String getIcon() {
        return "<i class=\"fas fa-list-ol\"></i>";
    }

    public String getCategory() {
        return CATEGORY_COMPONENTS;
    }

    protected String getPropertyResource() {
        return "StepsBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"steps\":[{\"label\":\"Submitted\",\"matches\":\"\"},{\"label\":\"Reviewed\",\"matches\":\"\"},{\"label\":\"Done\",\"matches\":\"\"}],"
                + "\"current\":\"2\",\"previewStep\":\"2\",\"lastDone\":\"true\",\"doneMark\":\"check\",\"color\":\"\",\"mutedColor\":\"#e2e8f0\","
                + "\"labelColor\":\"\",\"mutedLabelColor\":\"#94a3b8\",\"fontSize\":\"12px\",\"padding\":\"16px 32px\"}";
    }

    /** Per step: label as HTML, label as plain text, and the other values that mean this step (lower case). */
    static final class Step {
        final String html;
        final String plain;
        final List<String> matches = new ArrayList<>();

        Step(String html, String plain) {
            this.html = html;
            this.plain = plain;
        }
    }

    public String toMjml(EmailRenderContext ctx) {
        List<Step> steps = steps(ctx);
        if (steps.isEmpty()) {
            if (!ctx.isPreview()) {
                ctx.warn("Steps has no steps; it was left out.");
                return "";
            }
            return "<mj-text align=\"center\" color=\"#7b8794\" font-size=\"13px\" padding=\"" + prop("padding", PADDING, "16px 32px") + "\">Steps: add steps in its properties</mj-text>";
        }
        int n = steps.size();
        int current = current(ctx, steps);
        boolean complete = current > n || (current == n && "true".equals(getPropertyString("lastDone")));

        String accent = color(ctx, "color", "link", "#2563eb");
        String muted = prop("mutedColor", HEX_COLOR, "#e2e8f0");
        String label = color(ctx, "labelColor", "text", "#333333");
        String mutedLabel = prop("mutedLabelColor", HEX_COLOR, "#94a3b8");
        String card = color(ctx, "", "content", "#ffffff");
        boolean numbers = "number".equals(getPropertyString("doneMark"));

        String width = (100 / n) + "%";
        StringBuilder m = new StringBuilder("<mj-text padding=\"").append(prop("padding", PADDING, "16px 32px"))
                .append("\" font-size=\"").append(prop("fontSize", PX, "12px")).append("\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"table-layout:fixed;\"><tr>");
        for (int i = 1; i <= n; i++) {
            Step s = steps.get(i - 1);
            boolean done = complete || i < current;
            boolean now = !complete && i == current;
            String circleBg = done ? accent : now ? card : muted;
            String circleBorder = done || now ? accent : muted;
            String mark = done ? (numbers ? String.valueOf(i) : CHECK) : String.valueOf(i);
            String markColor = done ? "#ffffff" : now ? accent : mutedLabel;
            m.append("<td width=\"").append(width).append("\" valign=\"top\" align=\"center\" style=\"padding:0;\">")
                    // separate borders: MJML's base style collapses them, and a collapsed cell ignores border-radius
                    .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:separate;\"><tr>")
                    .append(half(i > 1, complete || i <= current, accent, muted))
                    .append("<td width=\"28\" height=\"28\" align=\"center\" valign=\"middle\" style=\"width:28px;min-width:28px;height:28px;border-radius:50%;background-color:")
                    .append(circleBg).append(";border:2px solid ").append(circleBorder).append(";color:").append(markColor)
                    .append(";font-size:13px;line-height:28px;font-weight:bold;font-family:Arial, Helvetica, sans-serif;\">").append(mark).append("</td>")
                    .append(half(i < n, complete || i + 1 <= current, accent, muted))
                    .append("</tr></table>")
                    .append("<div style=\"padding:8px 2px 0 2px;text-align:center;line-height:1.3;color:")
                    .append(now ? accent : done ? label : mutedLabel).append(";").append(now ? "font-weight:bold;" : "").append("\">")
                    .append(s.html).append("</div></td>");
        }
        return m.append("</tr></table></mj-text>").toString();
    }

    /**
     * Half of the line on one side of a circle; a blank cell before the first step and after the last. No width: the
     * two halves share what the circle leaves (as percentages they would squeeze the circle).
     */
    private static String half(boolean line, boolean reached, String accent, String muted) {
        if (!line) {
            return "<td style=\"font-size:0;line-height:0;\">&nbsp;</td>";
        }
        return "<td valign=\"middle\" style=\"font-size:0;line-height:0;\"><div style=\"height:3px;line-height:3px;font-size:0;background-color:"
                + (reached ? accent : muted) + ";\">&nbsp;</div></td>";
    }

    public String toText(EmailRenderContext ctx) {
        List<Step> steps = steps(ctx);
        int n = steps.size();
        int current = current(ctx, steps);
        boolean complete = current > n || (current == n && "true".equals(getPropertyString("lastDone")));
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            String state = complete || i < current ? " (done)" : i == current ? " (current)" : "";
            out.add(steps.get(i - 1).plain + state);
        }
        return String.join(" > ", out);
    }

    private List<Step> steps;
    private Integer currentStep;

    /** Steps with a label, at most {@link #MAX_STEPS}. */
    List<Step> steps(EmailRenderContext ctx) {
        if (steps != null) {
            return steps;
        }
        steps = new ArrayList<>();
        for (Map<?, ?> row : gridRows("steps")) {
            String label = cell(row, "label");
            if (label.isEmpty()) {
                continue;
            }
            if (steps.size() == MAX_STEPS) {
                ctx.warn("Steps shows at most " + MAX_STEPS + " steps; the rest were left out.");
                break;
            }
            Step s = new Step(ctx.html(label), ctx.plain(label).trim());
            for (String alias : cell(row, "matches").split(",")) {
                String a = ctx.plain(alias.trim()).trim();
                if (!a.isEmpty()) {
                    s.matches.add(a.toLowerCase(Locale.ROOT));
                }
            }
            steps.add(s);
        }
        return steps;
    }

    /**
     * The current step, 1-based: 0 before the first step, above the count when all are done. A number is used
     * as it is; other text is matched (ignoring case) against each step's label and "Also matches" values. In the
     * builder, a field that is still unresolved or empty shows the "step shown in the builder".
     */
    int current(EmailRenderContext ctx, List<Step> steps) {
        if (currentStep != null) {
            return currentStep;
        }
        String authored = getPropertyString("current").trim();
        String value = authored.isEmpty() ? "" : ctx.plain(authored).trim();
        if (value.isEmpty() || UNRESOLVED_FIELD.matcher(value).find()) {
            if (ctx.isBuilder()) {
                if (!ctx.isPreview() && !authored.isEmpty()) {
                    ctx.warn("Steps: the current step (" + authored + ") has no value here, so the builder shows step " + previewStep() + ".");
                }
                return currentStep = previewStep();
            }
            ctx.warn("Steps: the current step" + (authored.isEmpty() ? "" : " (" + authored + ")") + " has no value, so every step shows as upcoming.");
            return currentStep = 0;
        }
        if (value.matches("\\d{1,3}")) {
            return currentStep = Integer.parseInt(value);
        }
        String key = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            if (s.plain.toLowerCase(Locale.ROOT).equals(key) || s.matches.contains(key)) {
                return currentStep = i + 1;
            }
        }
        ctx.warn("Steps: \"" + value + "\" matches no step, so every step shows as upcoming. Add it to a step's \"Also matches\".");
        return currentStep = 0;
    }

    private int previewStep() {
        String v = getPropertyString("previewStep").trim();
        return v.matches("\\d{1,3}") ? Integer.parseInt(v) : 1;
    }
}
