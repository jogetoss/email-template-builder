package org.joget.etb;

import static org.junit.Assert.*;

import java.util.List;
import java.util.stream.Collectors;
import org.joget.etb.api.EmailBlock;
import org.joget.etb.lib.ButtonBlock;
import org.joget.etb.lib.DividerBlock;
import org.joget.etb.lib.SpacerBlock;
import org.joget.etb.lib.TextBlock;
import org.junit.Test;

public class EmailTemplateBuilderTest {

    @Test
    public void paletteOrderPutsTextFirst() {
        List<EmailBlock> sorted = EmailTemplateBuilder.paletteOrder(List.of(new SpacerBlock(), new DividerBlock(), new org.joget.etb.lib.HtmlBlock(), new ButtonBlock(), new TextBlock()));
        assertEquals(List.of("Text", "Button", "Custom HTML", "Divider", "Spacer"), sorted.stream().map(EmailBlock::getLabel).collect(Collectors.toList()));
    }

    @Test
    public void testRecipientsSplitOnSemicolonOrComma() {
        assertEquals(List.of("a@x.com", "b@x.com"), EmailTemplateBuilder.testRecipients(" a@x.com; b@x.com,"));
        assertTrue(EmailTemplateBuilder.testRecipients(null).isEmpty());
        assertTrue(EmailTemplateBuilder.testRecipients(" ").isEmpty());
    }

    @Test
    public void testRecipientsAreChecked() {
        assertNull(EmailTemplateBuilder.checkTestRecipients(List.of("a@x.com", "first.last+tag@sub.example.org")));
        assertNotNull(EmailTemplateBuilder.checkTestRecipients(List.of()));
        assertNotNull(EmailTemplateBuilder.checkTestRecipients(List.of("not-an-address")));
        assertNotNull(EmailTemplateBuilder.checkTestRecipients(List.of("a@x.com\r\nBcc: b@x.com")));
        assertNotNull(EmailTemplateBuilder.checkTestRecipients(List.of("Name <a@x.com>")));
        assertNotNull(EmailTemplateBuilder.checkTestRecipients(List.of("1@x.com", "2@x.com", "3@x.com", "4@x.com", "5@x.com", "6@x.com")));
    }

    @Test
    public void previewBarsKeepBackslashesAndDollarsAndSitRightAfterBody() {
        String bars = EmailTemplateBuilder.previewBars(List.of("Blocked link \"C:\\Users\\x\" costs $5"),
                List.of(new org.joget.etb.render.EmailRenderResult.DataNote("Repeat", "Tasks", 0, 20, List.of("board_id = b-17")),
                        new org.joget.etb.render.EmailRenderResult.DataNote("Repeat", "Fine", 3, 20, List.of())));
        assertTrue(bars, bars.contains("C:\\Users\\x") && bars.contains("$5"));
        assertTrue(bars.contains("Repeat (Tasks): no rows match."));
        assertFalse("a block that simply works says nothing", bars.contains("Fine"));
        // a block of the design using the warning colour does not move the bars
        String html = "<html><body class=\"x\"><div style=\"background:#fff3cd;\">mine</div></body></html>";
        String out = EmailTemplateBuilder.afterBody(html, bars);
        assertTrue(out.startsWith("<html><body class=\"x\">" + bars + "<div style=\"background:#fff3cd;\">mine"));
        assertEquals(html, EmailTemplateBuilder.afterBody(html, ""));
        assertEquals("", EmailTemplateBuilder.previewBars(List.of(), List.of()));
    }
}
