package org.joget.etb.lib;

import java.util.regex.Pattern;
import org.joget.etb.api.EmailRenderContext;

/** A column (mj-column) inside a row. Columns stack on mobile. */
public class ColumnBlock extends AbstractBlock {

    static final Pattern WIDTH = Pattern.compile("\\d{1,3}%|\\d{1,3}px");

    public String getName() {
        return "Email Column Block";
    }

    public String getLabel() {
        return "Column";
    }

    public String getIcon() {
        return "<i class=\"fas fa-grip-lines-vertical\"></i>";
    }

    protected String getPropertyResource() {
        return "ColumnBlock";
    }

    public String getCategory() {
        return CATEGORY_LAYOUT;
    }

    public boolean isContainer() {
        return true;
    }

    public String getAllowedParent() {
        return PARENT_ROW;
    }

    public String getContainerType() {
        return "column";
    }

    /** Canvas: a Column only drops into a Row, and holds content blocks. */
    public String getBuilderTemplate() {
        return new org.json.JSONObject(super.getBuilderTemplate())
                .put("parentContainerAttr", "columns")
                .put("parentDataHolder", "elements")
                .put("childsContainerAttr", "elements")
                .put("childsDataHolder", "elements")
                .toString();
    }

    public String toMjml(EmailRenderContext ctx) {
        String width = prop("width", WIDTH, "");
        String bg = prop("backgroundColor", HEX_COLOR, "");
        String borderColor = prop("borderColor", HEX_COLOR, "");
        String radius = prop("borderRadius", PX, "");
        return "<mj-column" + (width.isEmpty() ? "" : " width=\"" + width + "\"") + " vertical-align=\"" + prop("verticalAlign", VALIGN, "top") + "\""
                + (bg.isEmpty() ? "" : " background-color=\"" + bg + "\"")
                + (borderColor.isEmpty() ? "" : " border=\"" + prop("borderWidth", PX, "1px") + " solid " + borderColor + "\"")
                + (radius.isEmpty() ? "" : " border-radius=\"" + radius + "\"")
                + " padding=\"" + prop("padding", PADDING, "0px") + "\">"
                + ctx.children() + "</mj-column>";
    }

    public String toText(EmailRenderContext ctx) {
        return ctx.childrenText();
    }
}
