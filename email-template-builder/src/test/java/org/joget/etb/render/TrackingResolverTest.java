package org.joget.etb.render;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TrackingResolverTest {

    static final Map<String, String> VALUES = Map.of("#currentUser.firstName#", "Admin", "#form.leave.blank#", " ");

    /** Replaces known tokens; unknown ones stay as-is, like an unresolved Joget hash variable. */
    static final MergeResolver FAKE = (text, format) -> {
        String out = text;
        for (Map.Entry<String, String> e : VALUES.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out.replace("#form.leave.reason#", "");
    };

    @Test
    public void listsFieldsThatCameOutEmptyOnce() {
        TrackingResolver r = new TrackingResolver(FAKE);
        assertEquals("Hi Admin,   ", r.resolve("Hi #currentUser.firstName#, #form.leave.reason# #form.leave.blank#", null));
        r.resolve("Again #form.leave.reason# and #custom.unknown#", null);
        assertEquals(List.of("#form.leave.reason#", "#form.leave.blank#", "#custom.unknown#"), r.getEmpty());
        assertTrue(r.warning().startsWith("3 merge fields came out empty: #form.leave.reason#, #form.leave.blank#, #custom.unknown#."));
    }

    @Test
    public void noWarningWhenEveryFieldHasAValue() {
        TrackingResolver r = new TrackingResolver(FAKE);
        r.resolve("Hi #currentUser.firstName#, total 5 # of 6", null);
        assertNull(r.warning());
    }

    @Test
    public void withWarningAddsOneWarning() {
        EmailRenderResult result = new EmailRenderResult("s", "<p>", "t", "m", 1, List.of("a"));
        assertSame(result, result.withWarning(null));
        assertEquals(List.of("a", "b"), result.withWarning("b").getWarnings());
    }
}
