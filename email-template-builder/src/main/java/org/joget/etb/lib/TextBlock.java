package org.joget.etb.lib;

import org.joget.etb.api.EmailRenderContext;
import org.json.JSONObject;

/** Rich text (mj-text). Inline-editable on the canvas through the builder's built-in inline editor. */
public class TextBlock extends AbstractBlock {

    private String resolvedHtml;

    public String getName() {
        return "Email Text Block";
    }

    public String getLabel() {
        return "Text";
    }

    public String getIcon() {
        return "<i class=\"fas fa-font\"></i>";
    }

    protected String getPropertyResource() {
        return "TextBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"html\":\"<p>New text</p>\",\"align\":\"left\",\"fontSize\":\"16px\",\"padding\":\"8px 32px\"}";
    }

    public String getBuilderTemplate() {
        return new JSONObject(super.getBuilderTemplate())
                .put("inlineEditor", new JSONObject().put("property", "html").put("mode", "default").put("selector", "[" + INLINE_EDIT_ATTR + "]"))
                .toString();
    }

    public String toMjml(EmailRenderContext ctx) {
        String html = html(ctx);
        if (ctx.isPreview()) {
            html = "<div " + INLINE_EDIT_ATTR + ">" + html + "</div>";
        }
        String color = prop("color", HEX_COLOR, "");
        String bg = prop("backgroundColor", HEX_COLOR, "");
        return "<mj-text align=\"" + prop("align", ALIGN, "left") + "\" font-size=\"" + prop("fontSize", PX, "16px") + "\""
                + (color.isEmpty() ? "" : " color=\"" + color + "\"")
                + (bg.isEmpty() ? "" : " container-background-color=\"" + bg + "\"")
                + " padding=\"" + prop("padding", PADDING, "8px 32px") + "\">" + html + "</mj-text>";
    }

    public String toText(EmailRenderContext ctx) {
        return ctx.text(html(ctx));
    }

    /** Resolved once per element: toMjml and toText share it, so merge fields are looked up once. */
    private String html(EmailRenderContext ctx) {
        if (resolvedHtml == null) {
            resolvedHtml = ctx.html(getPropertyString("html"));
        }
        return resolvedHtml;
    }
}
