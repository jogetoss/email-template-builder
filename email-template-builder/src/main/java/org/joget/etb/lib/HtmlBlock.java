package org.joget.etb.lib;

import org.joget.etb.api.EmailRenderContext;

/**
 * Custom HTML: the author's own markup, and merge fields inserted WITHOUT HTML-escaping, for hash variables
 * that return app-generated HTML (a digest, a change log). Joget's relaxed sanitiser still strips
 * scripts and event handlers from each value, but links and images pass, so never feed it raw form input.
 */
public class HtmlBlock extends AbstractBlock {

    private String resolvedHtml;

    public String getName() {
        return "Email Trusted HTML Block";
    }

    public String getLabel() {
        return "Custom HTML";
    }

    public String getIcon() {
        return "<i class=\"fas fa-code\"></i>";
    }

    protected String getPropertyResource() {
        return "HtmlBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"html\":\"<p>#yourPlugin.htmlDigest#</p>\",\"padding\":\"8px 32px\"}";
    }

    public String toMjml(EmailRenderContext ctx) {
        String color = prop("color", HEX_COLOR, "");
        return "<mj-text font-size=\"" + prop("fontSize", PX, "16px") + "\"" + (color.isEmpty() ? "" : " color=\"" + color + "\"")
                + " padding=\"" + prop("padding", PADDING, "8px 32px") + "\">" + html(ctx) + "</mj-text>";
    }

    public String toText(EmailRenderContext ctx) {
        return ctx.text(html(ctx));
    }

    private String html(EmailRenderContext ctx) {
        if (resolvedHtml == null) {
            resolvedHtml = ctx.trustedHtml(getPropertyString("html"));
        }
        return resolvedHtml;
    }
}
