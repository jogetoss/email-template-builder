package org.joget.etb.lib;

import static org.junit.Assert.*;

import org.joget.etb.api.EmailBlock;
import org.json.JSONObject;
import org.junit.Test;

/** Palette metadata every block must provide for the canvas. */
public class BlockMetadataTest {

    static final EmailBlock[] BLOCKS = {new TextBlock(), new ButtonBlock(), new DividerBlock(), new SpacerBlock(), new RowBlock(), new ColumnBlock(), new HtmlBlock(), new ImageBlock(), new HeroBlock(), new NavbarBlock(), new DatalistTableBlock(), new RepeatBlock(), new OutsideBlock()};

    @Test
    public void everyBlockHasCanvasMetadata() {
        for (EmailBlock b : BLOCKS) {
            JSONObject template = new JSONObject(b.getBuilderTemplate());
            assertTrue(b.getLabel() + ": dragHtml is required, or the palette drag has no drop zone", template.optString("dragHtml").contains(b.getLabel()));
            new JSONObject(b.getDefaultPropertyValues());
            assertNotNull(b.getIcon());
            assertFalse(b.getName().isEmpty());
        }
    }

    @Test
    public void textBlockDeclaresTheInlineEditor() {
        JSONObject editor = new JSONObject(new TextBlock().getBuilderTemplate()).getJSONObject("inlineEditor");
        assertEquals("html", editor.getString("property"));
        assertEquals("[" + EmailBlock.INLINE_EDIT_ATTR + "]", editor.getString("selector"));
    }

    /**
     * Regression: ExtDefaultPlugin.setProperties wraps properties in DX 9's HashVariableSupportedMap, which
     * resolves hash variables on get() with no escaping. Blocks must see the raw author text.
     */
    @Test
    public void blocksReadRawAuthorTextNotAutoResolvedValues() {
        TextBlock b = new TextBlock();
        b.setProperties(new java.util.HashMap<String, Object>(java.util.Map.of("html", "<p>#form.request.note#</p>")));
        assertEquals("<p>#form.request.note#</p>", b.getPropertyString("html"));
        assertFalse(b.getProperties() instanceof org.joget.plugin.base.HashVariableSupportedMap);
    }

    @Test
    public void rowAndColumnCanvasWiring() {
        JSONObject row = new JSONObject(new RowBlock().getBuilderTemplate());
        assertEquals("rows", row.getString("parentContainerAttr"));
        assertEquals("columns", row.getString("childsContainerAttr"));
        assertEquals("a dropped Row arrives with two Columns", 2, row.getJSONArray("elements").length());
        assertEquals(ColumnBlock.class.getName(), row.getJSONArray("elements").getJSONObject(0).getString("className"));
        JSONObject col = new JSONObject(new ColumnBlock().getBuilderTemplate());
        assertEquals("columns", col.getString("parentContainerAttr"));
        assertEquals("elements", col.getString("childsContainerAttr"));
    }

    @Test
    public void containerContracts() {
        assertEquals("row", new RowBlock().getContainerType());
        assertEquals(EmailBlock.PARENT_TEMPLATE_OR_REPEAT, new RowBlock().getAllowedParent());
        assertEquals("column", new ColumnBlock().getContainerType());
        assertEquals(EmailBlock.PARENT_ROW, new ColumnBlock().getAllowedParent());
        assertNull(new TextBlock().getContainerType());
    }

    @Test
    public void heroSitsAtTheTopLevelOnlyAndIsASection() {
        HeroBlock hero = new HeroBlock();
        assertEquals(EmailBlock.PARENT_TEMPLATE, hero.getAllowedParent());
        assertTrue(hero.isSectionLevel());
        assertEquals("dropped only into the email itself, never a Repeat's card", "toplevel", new JSONObject(hero.getBuilderTemplate()).getString("parentContainerAttr"));
        assertEquals("toplevel", new JSONObject(new RepeatBlock().getBuilderTemplate()).getString("parentContainerAttr"));
        assertEquals("a Row may sit in a Repeat's card", EmailBlock.PARENT_TEMPLATE_OR_REPEAT, new RowBlock().getAllowedParent());
        assertFalse(new NavbarBlock().isSectionLevel());
        assertTrue(new RowBlock().isSectionLevel());
    }

    @Test
    public void heroPaddingShorthand() {
        assertEquals("0px 0px 0px 0px", String.join(" ", HeroBlock.sides("0px")));
        assertEquals("40px 32px 40px 32px", String.join(" ", HeroBlock.sides("40px 32px")));
        assertEquals("1px 2px 3px 2px", String.join(" ", HeroBlock.sides("1px 2px 3px")));
        assertEquals("1px 2px 3px 4px", String.join(" ", HeroBlock.sides("1px 2px 3px 4px")));
    }

    @Test
    public void theVersionComesFromThePom() {
        // etb.version=${project.version}, filtered at build time; "unknown" would mean the filtering was lost
        assertTrue(org.joget.etb.Activator.VERSION, org.joget.etb.Activator.VERSION.matches("\\d+\\.\\d+.*"));
        assertEquals(org.joget.etb.Activator.VERSION, new TextBlock().getVersion());
    }

    @Test
    public void aLabelWithABackslashOrQuoteStillMakesValidBuilderJson() {
        TextBlock odd = new TextBlock() {
            public String getLabel() {
                return "C:\\x \"quoted\" <b>";
            }
        };
        org.json.JSONObject t = new org.json.JSONObject(odd.getBuilderTemplate());
        assertTrue(t.getString("dragHtml"), t.getString("dragHtml").contains("C:\\x &quot;quoted&quot; &lt;b&gt;"));
    }

    @Test
    public void aButtonWithOnlyASchemeHasNoAddress() {
        for (String none : new String[] {"", "https://", "HTTP://", "mailto:", "tel:"}) {
            assertTrue(none, ButtonBlock.NO_ADDRESS.matcher(none).matches());
        }
        for (String some : new String[] {"https://x.com", "mailto:a@b.c", "#top"}) {
            assertFalse(some, ButtonBlock.NO_ADDRESS.matcher(some).matches());
        }
    }
}
