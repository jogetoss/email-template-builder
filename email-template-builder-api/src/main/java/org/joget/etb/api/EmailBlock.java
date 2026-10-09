package org.joget.etb.api;

import org.joget.plugin.property.model.PropertyEditable;

/**
 * A block in an email template. Blocks are OSGi plugins, discovered with
 * {@code pluginManager.list(EmailBlock.class)}, and emit MJML that the builder compiles to email HTML.
 *
 * <p>Implementations should extend {@link DefaultEmailBlock}. All author text must go through the
 * {@link EmailRenderContext} so merge fields are resolved and escaped consistently.</p>
 */
public interface EmailBlock extends PropertyEditable {

    String CATEGORY_LAYOUT = "layout";
    String CATEGORY_CONTENT = "content";
    String CATEGORY_DATA = "data";
    /** Ready-made parts (steps, progress bars): also offered, in preset looks, in the builder's Components gallery. */
    String CATEGORY_COMPONENTS = "components";

    /**
     * A content block: at the template's top level (auto-wrapped in a full-width section), in a column, in a
     * repeat's card or outside the card.
     */
    String PARENT_TEMPLATE_OR_COLUMN = "template,column,repeat,outside";
    /** Top level only (a row). */
    String PARENT_TEMPLATE = "template";
    /** At the top level, in a repeat's card or outside the card (a row). */
    String PARENT_TEMPLATE_OR_REPEAT = "template,repeat,outside";
    /** Inside a row only (a column). */
    String PARENT_ROW = "row";

    /**
     * Attribute marking a block's inline-editable region on the canvas (preview mode only); the builder's
     * inline editor targets {@code [data-etb-inline]}. Never emitted in a sent email.
     */
    String INLINE_EDIT_ATTR = "data-etb-inline";

    String getName();

    String getIcon();

    String getCategory();

    boolean isContainer();

    /** One of the PARENT_* constants. Enforced by the renderer, and by the canvas. */
    String getAllowedParent();

    /** The container type this block provides to its children: "row", "column", "repeat", "outside", or null. */
    String getContainerType();

    /**
     * True when {@link #toMjml} returns a section-level element (mj-section, mj-hero) that goes at the
     * template's top level as-is. Anything else at the top level is wrapped in a full-width section.
     */
    default boolean isSectionLevel() {
        return "row".equals(getContainerType());
    }

    /** JSON object of the property values a new block starts with. */
    String getDefaultPropertyValues();

    /** JSON object merged into the builder canvas's builderTemplate (dragHtml, inlineEditor, ...). */
    String getBuilderTemplate();

    /** MJML fragment. Containers include {@link EmailRenderContext#children()}. */
    String toMjml(EmailRenderContext ctx);

    /** Plain-text contribution for the text/plain part; "" to skip. */
    String toText(EmailRenderContext ctx);
}
