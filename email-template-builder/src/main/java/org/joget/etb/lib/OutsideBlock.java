package org.joget.etb.lib;

import org.joget.etb.api.EmailRenderContext;
import org.joget.etb.render.EmailTemplateRenderer;

/**
 * Outside the card: its blocks sit on the page background instead of in the content card, e.g. a logo
 * above the card or an address and "Questions?" line below it. First in the email it is above the card, last
 * it is below; in the middle it splits the email into two cards. Holds content blocks and Rows.
 */
public class OutsideBlock extends AbstractBlock {

    public String getName() {
        return "Email Outside the Card Block";
    }

    public String getLabel() {
        return "Outside the card";
    }

    public String getIcon() {
        return "<i class=\"far fa-window-maximize\"></i>";
    }

    @Override
    public String getCategory() {
        return CATEGORY_LAYOUT;
    }

    @Override
    public boolean isContainer() {
        return true;
    }

    @Override
    public String getContainerType() {
        return EmailTemplateRenderer.OUTSIDE;
    }

    @Override
    public String getAllowedParent() {
        return PARENT_TEMPLATE;
    }

    @Override
    public boolean isSectionLevel() {
        return true;
    }

    protected String getPropertyResource() {
        return "OutsideBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"padding\":\"24px 0px\"}";
    }

    /** Only into the email itself; content blocks and Rows drop inside it. */
    @Override
    public String getBuilderTemplate() {
        String text = "{\"className\":\"" + TextBlock.class.getName() + "\",\"properties\":{\"html\":\"<p style=\\\"text-align:center\\\">Logo, address or links</p>\"}}";
        return new org.json.JSONObject(super.getBuilderTemplate())
                .put("parentContainerAttr", "toplevel")
                .put("childsContainerAttr", "elements")
                .put("childsDataHolder", "elements")
                .put("elements", new org.json.JSONArray("[" + text + "]"))
                .toString();
    }

    /** A transparent wrapper (the page background shows) with the block's padding, around the children's sections. */
    public String toMjml(EmailRenderContext ctx) {
        String bg = prop("backgroundColor", HEX_COLOR, "");
        return "<mj-wrapper padding=\"" + prop("padding", PADDING, "24px 0px") + "\"" + (bg.isEmpty() ? "" : " background-color=\"" + bg + "\"") + ">"
                + ctx.childSections(null) + "</mj-wrapper>";
    }

    public String toText(EmailRenderContext ctx) {
        return ctx.childrenText();
    }
}
