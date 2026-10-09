package org.joget.etb.render;

import static org.junit.Assert.*;

import org.junit.Test;

public class UrlPolicyTest {

    @Test
    public void allowlist() {
        for (String ok : new String[] {"https://x.com", "HTTP://x.com", "mailto:a@b.com", "tel:+1", "cid:logo.png"}) {
            assertTrue(ok, UrlPolicy.isAllowed(ok));
        }
        for (String bad : new String[] {"javascript:x", "java\nscript:x", "\u0000javascript:x", "data:x", "file:///etc", "//x.com", "x.com", "", null}) {
            assertFalse(String.valueOf(bad), UrlPolicy.isAllowed(bad));
        }
    }

    @Test
    public void aLinkWithinTheEmailIsAllowedButALeftoverMergeFieldIsNot() {
        for (String ok : new String[] {"#top", "#section-2", "#faq.q1"}) {
            assertTrue(ok, UrlPolicy.isAllowed(ok));
        }
        for (String bad : new String[] {"#", "#form.req.link#", "#1", "#javascript:alert(1)x#"}) {
            assertFalse(bad, UrlPolicy.isAllowed(bad));
        }
    }
}
