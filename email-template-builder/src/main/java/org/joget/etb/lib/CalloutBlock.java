package org.joget.etb.lib;

import java.util.regex.Pattern;
import org.joget.etb.api.EmailRenderContext;

/**
 * Callout: a highlighted box with an optional icon and title, and text. Styles: a soft box, a box with a coloured
 * left bar (a warning), a dashed box (a code), or a quote (a large quote with a left rule; the title is shown under
 * it as the attribution). "Large code text" shows the text big, monospaced and spaced out (a one-time code).
 */
public class CalloutBlock extends AbstractBlock {

    static final Pattern STYLE = Pattern.compile("box|bar|dashed|quote");
    static final String MONO = "font-family:Courier New, Courier, monospace;";

    public String getName() {
        return "Email Callout Block";
    }

    public String getLabel() {
        return "Callout";
    }

    public String getIcon() {
        return "<i class=\"fas fa-comment-alt\"></i>";
    }

    public String getCategory() {
        return CATEGORY_COMPONENTS;
    }

    protected String getPropertyResource() {
        return "CalloutBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"style\":\"box\",\"icon\":\"\",\"title\":\"Good to know\",\"text\":\"Write the note here.\",\"backgroundColor\":\"#f1f5f9\","
                + "\"accentColor\":\"\",\"titleColor\":\"\",\"textColor\":\"#475569\",\"radius\":\"10px\",\"fontSize\":\"14px\",\"align\":\"left\","
                + "\"padding\":\"8px 32px\"}";
    }

    public String toMjml(EmailRenderContext ctx) {
        String style = prop("style", STYLE, "box");
        String icon = ctx.html(getPropertyString("icon")).trim();
        String title = ctx.html(getPropertyString("title")).trim();
        String text = ctx.html(getPropertyString("text")).trim();
        String padding = prop("padding", PADDING, "8px 32px");
        if (title.isEmpty() && text.isEmpty()) {
            if (!ctx.isPreview()) {
                ctx.warn("Callout has no title or text; it was left out.");
                return "";
            }
            text = "Callout: add a title or text in its properties";
        }
        String accent = color(ctx, "accentColor", "link", "#2563eb");
        String bg = prop("backgroundColor", HEX_COLOR, "");
        String titleColor = color(ctx, "titleColor", "text", "#1f2937");
        String textColor = prop("textColor", HEX_COLOR, "#475569");
        String radius = prop("radius", PX, "10px");
        String size = prop("fontSize", PX, "14px");
        String align = prop("align", ALIGN, "left");
        boolean code = "true".equals(getPropertyString("code"));

        StringBuilder cell = new StringBuilder("padding:").append("quote".equals(style) ? "4px 0 4px 18px" : "14px 16px").append(";");
        if (!bg.isEmpty()) {
            cell.append("background-color:").append(bg).append(";");
        }
        switch (style) {
            case "bar":
                cell.append("border-left:4px solid ").append(accent).append(";border-radius:0 ").append(radius).append(" ").append(radius).append(" 0;");
                break;
            case "dashed":
                cell.append("border:2px dashed ").append(accent).append(";border-radius:").append(radius).append(";");
                break;
            case "quote":
                cell.append("border-left:4px solid ").append(accent).append(";");
                break;
            default:
                cell.append("border-radius:").append(radius).append(";");
        }
        StringBuilder inner = new StringBuilder();
        if ("quote".equals(style)) {
            inner.append("<p style=\"margin:0;font-size:21px;line-height:1.4;color:").append(titleColor).append(";\">").append(text).append("</p>");
            if (!title.isEmpty()) {
                inner.append("<p style=\"margin:8px 0 0 0;font-size:12px;letter-spacing:1px;color:").append(textColor).append(";\">&mdash; ").append(title).append("</p>");
            }
        } else {
            if (!title.isEmpty() || !icon.isEmpty()) {
                inner.append("<p style=\"margin:0;color:").append(titleColor).append(";\"><strong>").append(icon.isEmpty() ? "" : icon + "&nbsp; ")
                        .append(title).append("</strong></p>");
            }
            if (!text.isEmpty()) {
                String gap = title.isEmpty() && icon.isEmpty() ? "0" : "6px 0 0 0";
                inner.append(code
                        ? "<p style=\"margin:" + gap + ";" + MONO + "font-size:32px;letter-spacing:10px;line-height:1.3;color:" + titleColor + ";\"><strong>" + text + "</strong></p>"
                        : "<div style=\"margin:" + gap + ";color:" + textColor + ";\">" + text + "</div>");
            }
        }
        return "<mj-text align=\"" + align + "\" font-size=\"" + size + "\" padding=\"" + padding + "\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:separate;\"><tr><td align=\""
                + align + "\" style=\"" + cell + "\">" + inner + "</td></tr></table></mj-text>";
    }

    public String toText(EmailRenderContext ctx) {
        String title = ctx.text(ctx.html(getPropertyString("title"))).trim();
        String text = ctx.text(ctx.html(getPropertyString("text"))).trim();
        if ("quote".equals(prop("style", STYLE, "box"))) {
            return text + (title.isEmpty() ? "" : "\n- " + title);
        }
        return title.isEmpty() ? text : text.isEmpty() ? title : title + "\n" + text;
    }
}
