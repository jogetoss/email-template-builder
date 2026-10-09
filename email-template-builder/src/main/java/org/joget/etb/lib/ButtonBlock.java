package org.joget.etb.lib;

import org.joget.etb.api.EmailRenderContext;

/** Bulletproof button (mj-button): table-based, renders in Outlook without VML. */
public class ButtonBlock extends AbstractBlock {

    /** A link with no address: blank, or only a scheme (the property's default "https://"). */
    static final java.util.regex.Pattern NO_ADDRESS = java.util.regex.Pattern.compile("(?i)(https?://|mailto:|tel:)?");

    private String url;
    private String label;

    public String getName() {
        return "Email Button Block";
    }

    public String getLabel() {
        return "Button";
    }

    public String getIcon() {
        return "<i class=\"fas fa-square\"></i>";
    }

    protected String getPropertyResource() {
        return "ButtonBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"label\":\"Button\",\"href\":\"https://\",\"backgroundColor\":\"#6e9f4b\",\"textColor\":\"#ffffff\",\"align\":\"center\","
                + "\"fontSize\":\"16px\",\"borderRadius\":\"4px\",\"padding\":\"12px 32px\"}";
    }

    public String toMjml(EmailRenderContext ctx) {
        resolve(ctx);
        return "<mj-button href=\"" + url + "\" background-color=\"" + prop("backgroundColor", HEX_COLOR, "#6e9f4b")
                + "\" color=\"" + prop("textColor", HEX_COLOR, "#ffffff") + "\" font-size=\"" + prop("fontSize", PX, "16px")
                + "\" font-weight=\"bold\" border-radius=\"" + prop("borderRadius", PX, "4px") + "\" align=\"" + prop("align", ALIGN, "center")
                + "\" inner-padding=\"" + prop("innerPadding", PADDING, "12px 24px") + "\" padding=\"" + prop("padding", PADDING, "12px 32px") + "\">" + label + "</mj-button>";
    }

    public String toText(EmailRenderContext ctx) {
        resolve(ctx);
        if (NO_ADDRESS.matcher(url).matches()) {
            return ctx.text(label);
        }
        return ctx.text("<a href=\"" + url + "\">" + label + "</a>");
    }

    private void resolve(EmailRenderContext ctx) {
        if (url == null) {
            url = ctx.url(getPropertyString("href"));
            label = ctx.html(getPropertyString("label"));
            if (!ctx.isPreview() && NO_ADDRESS.matcher(url).matches()) {
                ctx.warn("Button \"" + getPropertyString("label") + "\" has no link address.");
            }
        }
    }
}
