package org.joget.etb.render;

import static org.junit.Assert.*;

import org.junit.Test;

public class HtmlToTextTest {

    @Test
    public void blocksBreaksAndEntities() {
        assertEquals("Hi Al,\n\nLine one\nLine two & more",
                HtmlToText.convert("<p>Hi <strong>Al</strong>,</p><p>Line one<br>Line two &amp; more</p>"));
    }

    @Test
    public void linksShowTheirTarget() {
        assertEquals("See the request (https://x.com/r/1) now", HtmlToText.convert("See <a href=\"https://x.com/r/1\">the request</a> now"));
        assertEquals("https://x.com", HtmlToText.convert("<a href=\"https://x.com\">https://x.com</a>"));
        assertEquals("a@b.com", HtmlToText.convert("<a href=\"mailto:a@b.com\">a@b.com</a>"));
    }

    @Test
    public void listsAndImages() {
        assertEquals("Items:\n\n- One\n- Two\n\nLogo", HtmlToText.convert("<p>Items:</p><ul><li>One</li><li>Two</li></ul><img alt=\"Logo\" src=\"x\">"));
    }

    @Test
    public void scriptsAndStylesDropped() {
        assertEquals("Visible", HtmlToText.convert("<style>p{}</style><script>x()</script><p>Visible</p>"));
    }

    @Test
    public void emptyInput() {
        assertEquals("", HtmlToText.convert(null));
        assertEquals("", HtmlToText.convert(""));
    }
}
