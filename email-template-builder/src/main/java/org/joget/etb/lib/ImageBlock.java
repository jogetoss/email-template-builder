package org.joget.etb.lib;

import org.joget.etb.api.EmailRenderContext;

/**
 * Image (mj-image), optionally linked. The source is an App Resource (embedded in the sent email), an
 * absolute URL, or cid:NAME for a file embedded through the tool's Attachments. Without a source it draws a labelled placeholder box, so
 * starter templates and new blocks look right before an image is chosen.
 */
public class ImageBlock extends AbstractBlock {

    private String src;
    private String href;
    private String alt;

    public String getName() {
        return "Email Image Block";
    }

    public String getLabel() {
        return "Image";
    }

    public String getIcon() {
        return "<i class=\"far fa-image\"></i>";
    }

    protected String getPropertyResource() {
        return "ImageBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"src\":\"\",\"alt\":\"Image\",\"align\":\"center\",\"padding\":\"8px 32px\",\"placeholderHeight\":\"200px\"}";
    }

    public String toMjml(EmailRenderContext ctx) {
        resolve(ctx);
        String padding = prop("padding", PADDING, "8px 32px");
        String radius = prop("borderRadius", PX, "0px");
        if (src.isEmpty() || "#".equals(src)) {
            if (!ctx.isPreview()) {
                ctx.warn("Image \"" + getPropertyString("alt") + "\" has no source; a placeholder is shown.");
            }
            String h = prop("placeholderHeight", PX, "200px");
            return "<mj-text padding=\"" + padding + "\"><div style=\"background-color:#e3e8ee;color:#7b8794;text-align:center;font-size:13px;"
                    + "height:" + h + ";line-height:" + h + ";border-radius:" + radius + ";\">" + (alt.isEmpty() ? "Image" : alt) + "</div></mj-text>";
        }
        String width = prop("width", PX, "");
        return "<mj-image src=\"" + src + "\" alt=\"" + alt.replace("\"", "&quot;") + "\""
                + (href.isEmpty() ? "" : " href=\"" + href + "\"")
                + (width.isEmpty() ? "" : " width=\"" + width + "\"")
                + " align=\"" + prop("align", ALIGN, "center") + "\" border-radius=\"" + radius + "\" padding=\"" + padding + "\"></mj-image>";
    }

    public String toText(EmailRenderContext ctx) {
        resolve(ctx);
        if (alt.isEmpty()) {
            return "";
        }
        return href.isEmpty() ? ctx.text(alt) : ctx.text("<a href=\"" + href + "\">" + alt + "</a>");
    }

    private void resolve(EmailRenderContext ctx) {
        if (src == null) {
            src = ctx.image(getPropertyString("src"));
            href = ctx.url(getPropertyString("href"));
            alt = ctx.html(getPropertyString("alt"));
        }
    }
}
