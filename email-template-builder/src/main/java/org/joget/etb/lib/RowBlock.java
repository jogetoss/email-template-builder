package org.joget.etb.lib;

import org.joget.etb.api.EmailRenderContext;

/** A row (mj-section) of 1 to 3 columns. Top level only. */
public class RowBlock extends AbstractBlock {

    public String getName() {
        return "Email Row Block";
    }

    public String getLabel() {
        return "Row";
    }

    public String getIcon() {
        return "<i class=\"fas fa-columns\"></i>";
    }

    protected String getPropertyResource() {
        return "RowBlock";
    }

    public String getCategory() {
        return CATEGORY_LAYOUT;
    }

    public boolean isContainer() {
        return true;
    }

    /** Top level, or inside a Repeat's card. */
    public String getAllowedParent() {
        return PARENT_TEMPLATE_OR_REPEAT;
    }

    public String getContainerType() {
        return "row";
    }

    public String getDefaultPropertyValues() {
        return "{\"padding\":\"0px 16px\"}";
    }

    /**
     * Canvas: a Row only drops into the email body (data-cbuilder-rows), holds Columns
     * (data-cbuilder-columns), and arrives with two empty columns.
     */
    public String getBuilderTemplate() {
        String column = "{\"className\":\"" + ColumnBlock.class.getName() + "\",\"properties\":{\"padding\":\"0px\"}}";
        return new org.json.JSONObject(super.getBuilderTemplate())
                .put("parentContainerAttr", "rows")
                .put("childsContainerAttr", "columns")
                .put("childsDataHolder", "elements")
                .put("elements", new org.json.JSONArray("[" + column + "," + column + "]"))
                .toString();
    }

    public String toMjml(EmailRenderContext ctx) {
        String bg = prop("backgroundColor", HEX_COLOR, "");
        // mj-group keeps the columns side by side on mobile (e.g. Approve / Reject buttons) instead of stacking
        boolean group = "true".equals(getPropertyString("keepSideBySide"));
        return "<mj-section" + (bg.isEmpty() ? "" : " background-color=\"" + bg + "\"") + " padding=\"" + prop("padding", PADDING, "0px") + "\">"
                + (group ? "<mj-group>" + ctx.children() + "</mj-group>" : ctx.children()) + "</mj-section>";
    }

    public String toText(EmailRenderContext ctx) {
        return ctx.childrenText();
    }
}
