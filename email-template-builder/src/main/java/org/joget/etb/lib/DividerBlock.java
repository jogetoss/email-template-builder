package org.joget.etb.lib;

import org.joget.etb.api.EmailRenderContext;

/** Horizontal rule (mj-divider). */
public class DividerBlock extends AbstractBlock {

    public String getName() {
        return "Email Divider Block";
    }

    public String getLabel() {
        return "Divider";
    }

    public String getIcon() {
        return "<i class=\"fas fa-minus\"></i>";
    }

    protected String getPropertyResource() {
        return "DividerBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"color\":\"#dddddd\",\"thickness\":\"1px\",\"width\":\"100%\",\"padding\":\"12px 32px\"}";
    }

    public String toMjml(EmailRenderContext ctx) {
        return "<mj-divider border-color=\"" + prop("color", HEX_COLOR, "#dddddd") + "\" border-width=\"" + prop("thickness", PX, "1px")
                + "\" width=\"" + prop("width", PERCENT, "100%") + "\" padding=\"" + prop("padding", PADDING, "12px 32px") + "\"></mj-divider>";
    }

    public String toText(EmailRenderContext ctx) {
        return "----------------------------------------";
    }
}
