package org.joget.etb.lib;

import java.util.regex.Pattern;
import org.joget.etb.api.EmailRenderContext;

/**
 * Badge: a short label in a coloured shape. A pill (a status such as #form.request.status#), a circle (initials,
 * an icon) or a rounded square. A one-cell table, so the background and padding hold in Outlook (which draws it
 * square).
 */
public class BadgeBlock extends AbstractBlock {

    static final Pattern SHAPE = Pattern.compile("pill|circle|rounded");

    public String getName() {
        return "Email Badge Block";
    }

    public String getLabel() {
        return "Badge";
    }

    public String getIcon() {
        return "<i class=\"fas fa-certificate\"></i>";
    }

    public String getCategory() {
        return CATEGORY_COMPONENTS;
    }

    protected String getPropertyResource() {
        return "BadgeBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"text\":\"&#9679;&nbsp; Approved\",\"shape\":\"pill\",\"backgroundColor\":\"#dcfce7\",\"textColor\":\"#15803d\",\"fontSize\":\"11px\","
                + "\"size\":\"44px\",\"bold\":\"true\",\"spaced\":\"true\",\"align\":\"left\",\"padding\":\"8px 32px\"}";
    }

    public String toMjml(EmailRenderContext ctx) {
        String text = ctx.html(getPropertyString("text"));
        String padding = prop("padding", PADDING, "8px 32px");
        if (text.trim().isEmpty()) {
            if (!ctx.isPreview()) {
                ctx.warn("Badge has no text; it was left out.");
                return "";
            }
            text = "Badge";
        }
        String shape = prop("shape", SHAPE, "pill");
        String align = prop("align", ALIGN, "left");
        String bg = color(ctx, "backgroundColor", "link", "#2563eb");
        String fg = prop("textColor", HEX_COLOR, "#ffffff");
        String size = prop("fontSize", PX, "11px");
        String weight = "true".equals(getPropertyString("bold")) ? "bold" : "normal";
        String spacing = "true".equals(getPropertyString("spaced")) ? "letter-spacing:1px;" : "";
        StringBuilder cell = new StringBuilder("background-color:").append(bg).append(";color:").append(fg).append(";font-size:").append(size)
                .append(";font-weight:").append(weight).append(";").append(spacing).append("font-family:Arial, Helvetica, sans-serif;");
        String td;
        if ("circle".equals(shape)) {
            int d = Math.max(16, Math.min(160, Integer.parseInt(prop("size", PX, "44px").replace("px", ""))));
            td = "<td width=\"" + d + "\" height=\"" + d + "\" align=\"center\" valign=\"middle\" style=\"" + cell + "width:" + d + "px;min-width:" + d
                    + "px;max-width:" + d + "px;height:" + d + "px;line-height:" + d + "px;border-radius:50%;padding:0;overflow:hidden;\">"
                    // one line, clipped to the circle: a long value (or an unfilled field on the canvas) must not stretch it into an oval
                    + "<div style=\"width:" + d + "px;max-width:" + d + "px;overflow:hidden;white-space:nowrap;\">" + text + "</div></td>";
        } else {
            td = "<td style=\"" + cell + "border-radius:" + ("pill".equals(shape) ? "999px" : "6px") + ";padding:5px 12px;line-height:1.3;white-space:nowrap;\">"
                    + text + "</td>";
        }
        // aligned by a full-width cell: a table ignores text-align, and align="right" on the table itself would float it
        return "<mj-text padding=\"" + padding + "\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td align=\"" + align
                + "\"><table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:separate;\"><tr>" + td
                + "</tr></table></td></tr></table></mj-text>";
    }

    public String toText(EmailRenderContext ctx) {
        return ctx.text(ctx.html(getPropertyString("text")));
    }
}
