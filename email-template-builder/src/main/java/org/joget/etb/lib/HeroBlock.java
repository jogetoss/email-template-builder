package org.joget.etb.lib;

import org.joget.etb.api.EmailRenderContext;

/**
 * Hero (mj-hero): a headline, text and an optional button over a background image, full width at the
 * template's top level. mj-hero emits a VML background for classic Outlook. The background colour shows
 * while images are blocked or loading, so text must stay readable on it.
 */
public class HeroBlock extends AbstractBlock {

    private String background;
    private String headline;
    private String body;
    private String buttonLabel;
    private String buttonHref;

    public String getName() {
        return "Email Hero Block";
    }

    public String getLabel() {
        return "Hero";
    }

    public String getIcon() {
        return "<i class=\"far fa-images\"></i>";
    }

    @Override
    public String getCategory() {
        return CATEGORY_LAYOUT;
    }

    /** mj-hero is a section: never inside a column. */
    @Override
    public String getAllowedParent() {
        return PARENT_TEMPLATE;
    }

    @Override
    public boolean isSectionLevel() {
        return true;
    }

    protected String getPropertyResource() {
        return "HeroBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"headline\":\"Big news, in one line\",\"text\":\"A sentence or two that says why it matters.\",\"buttonLabel\":\"Learn more\","
                + "\"buttonHref\":\"https://\",\"backgroundUrl\":\"\",\"backgroundColor\":\"#2d4a6b\",\"textColor\":\"#ffffff\",\"height\":\"300px\","
                + "\"align\":\"center\",\"padding\":\"40px 32px\"}";
    }

    /** Dropped only into the email itself (data-cbuilder-toplevel), never into a Repeat's card. */
    @Override
    public String getBuilderTemplate() {
        return new org.json.JSONObject(super.getBuilderTemplate()).put("parentContainerAttr", "toplevel").toString();
    }

    public String toMjml(EmailRenderContext ctx) {
        resolve(ctx);
        String align = prop("align", ALIGN, "center");
        String color = prop("textColor", HEX_COLOR, "#ffffff");
        StringBuilder m = new StringBuilder("<mj-hero mode=\"fixed-height\" height=\"").append(prop("height", PX, "300px"))
                .append("\" background-color=\"").append(prop("backgroundColor", HEX_COLOR, "#2d4a6b")).append('"');
        if (!background.isEmpty() && !"#".equals(background)) {
            m.append(" background-url=\"").append(background).append("\" background-position=\"")
                    .append(prop("backgroundPosition", BACKGROUND_POSITION, "center center")).append('"');
        }
        // mjml4j narrows the hero itself by its left/right padding (a full-width band becomes an inset box),
        // so only top/bottom padding goes on mj-hero; the sides go on each child
        String[] pad = sides(prop("padding", PADDING, "40px 32px"));
        String right = pad[1];
        String left = pad[3];
        m.append(" vertical-align=\"middle\" padding=\"").append(pad[0]).append(" 0px ").append(pad[2]).append("\">");
        if (!headline.isEmpty()) {
            m.append("<mj-text align=\"").append(align).append("\" color=\"").append(color).append("\" font-size=\"")
                    .append(prop("headlineSize", PX, "32px")).append("\" font-weight=\"bold\" line-height=\"1.2\" padding=\"0px ").append(right).append(" 12px ").append(left).append("\">")
                    .append(headline).append("</mj-text>");
        }
        if (!body.isEmpty()) {
            m.append("<mj-text align=\"").append(align).append("\" color=\"").append(color)
                    .append("\" font-size=\"16px\" line-height=\"1.5\" padding=\"0px ").append(right).append(" 16px ").append(left).append("\">").append(body).append("</mj-text>");
        }
        if (!buttonLabel.isEmpty()) {
            if (!ctx.isPreview() && ButtonBlock.NO_ADDRESS.matcher(buttonHref).matches()) {
                ctx.warn("Hero button \"" + getPropertyString("buttonLabel") + "\" has no link address.");
            }
            m.append("<mj-button href=\"").append(buttonHref).append("\" align=\"").append(align)
                    .append("\" background-color=\"").append(prop("buttonColor", HEX_COLOR, "#6e9f4b"))
                    .append("\" color=\"").append(prop("buttonTextColor", HEX_COLOR, "#ffffff"))
                    .append("\" font-size=\"16px\" font-weight=\"bold\" border-radius=\"4px\" inner-padding=\"12px 24px\" padding=\"8px ").append(right).append(" 0px ").append(left).append("\">")
                    .append(buttonLabel).append("</mj-button>");
        }
        return m.append("</mj-hero>").toString();
    }

    public String toText(EmailRenderContext ctx) {
        resolve(ctx);
        StringBuilder t = new StringBuilder();
        if (!headline.isEmpty()) {
            t.append(ctx.text(headline));
        }
        if (!body.isEmpty()) {
            t.append(t.length() > 0 ? "\n\n" : "").append(ctx.text(body));
        }
        if (!buttonLabel.isEmpty()) {
            String link = ButtonBlock.NO_ADDRESS.matcher(buttonHref).matches() ? buttonLabel : "<a href=\"" + buttonHref + "\">" + buttonLabel + "</a>";
            t.append(t.length() > 0 ? "\n\n" : "").append(ctx.text(link));
        }
        return t.toString();
    }

    /** CSS padding shorthand (1 to 4 px values, already validated) as {top, right, bottom, left}. */
    static String[] sides(String padding) {
        String[] v = padding.trim().split(" ");
        switch (v.length) {
            case 1: return new String[]{v[0], v[0], v[0], v[0]};
            case 2: return new String[]{v[0], v[1], v[0], v[1]};
            case 3: return new String[]{v[0], v[1], v[2], v[1]};
            default: return new String[]{v[0], v[1], v[2], v[3]};
        }
    }

    static final java.util.regex.Pattern BACKGROUND_POSITION = java.util.regex.Pattern.compile("(top|center|bottom) (left|center|right)");

    private void resolve(EmailRenderContext ctx) {
        if (headline == null) {
            background = ctx.publicImage(getPropertyString("backgroundUrl"));
            headline = ctx.html(getPropertyString("headline"));
            body = ctx.html(getPropertyString("text"));
            buttonLabel = ctx.html(getPropertyString("buttonLabel"));
            buttonHref = ctx.url(getPropertyString("buttonHref"));
        }
    }
}
