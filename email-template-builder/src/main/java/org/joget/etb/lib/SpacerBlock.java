package org.joget.etb.lib;

import org.joget.etb.api.EmailRenderContext;

/** Vertical whitespace (mj-spacer). */
public class SpacerBlock extends AbstractBlock {

    public String getName() {
        return "Email Spacer Block";
    }

    public String getLabel() {
        return "Spacer";
    }

    public String getIcon() {
        return "<i class=\"fas fa-arrows-alt-v\"></i>";
    }

    protected String getPropertyResource() {
        return "SpacerBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"height\":\"24px\"}";
    }

    public String toMjml(EmailRenderContext ctx) {
        return "<mj-spacer height=\"" + prop("height", PX, "24px") + "\"></mj-spacer>";
    }
}
