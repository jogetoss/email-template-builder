package org.joget.etb.render;

import static org.junit.Assert.*;

import org.junit.Test;

public class RecordResolverTest {

    static final MergeResolver ECHO = (text, format) -> text;

    @Test
    public void formFieldsNameTheTestRecord() {
        RecordResolver r = new RecordResolver(ECHO, "8f3a-01");
        assertEquals("<p>#form.leave.reason[8f3a-01]# and #form.leave.days[8f3a-01]?html#</p>",
                r.resolve("<p>#form.leave.reason# and #form.leave.days?html#</p>", null));
    }

    @Test
    public void otherMergeFieldsAreLeftAlone() {
        RecordResolver r = new RecordResolver(ECHO, "rec-1");
        String text = "#form.leave.reason[other]# #variable.status# #currentUser.firstName# #form.bad# #form.a.b.c#";
        assertEquals(text, r.resolve(text, null));
        assertNull(r.resolve(null, null));
    }

    @Test
    public void onlySafeRecordIdsAreUsed() {
        assertTrue(RecordResolver.isValidRecordId("8f3a2c1e-1b2c-4d5e-9f00-abcdef012345"));
        assertTrue(RecordResolver.isValidRecordId("REQ_2026/001"));
        for (String bad : new String[]{null, "", "a b", "a]b", "a#b", "a?b", "a{b}", "a\r\nb", "x".repeat(256)}) {
            assertFalse(bad, RecordResolver.isValidRecordId(bad));
        }
        assertSame(ECHO, RecordResolver.of(ECHO, "a]b"));
        assertSame(ECHO, RecordResolver.of(ECHO, null));
        assertTrue(RecordResolver.of(ECHO, "rec-1") instanceof RecordResolver);
    }

    @Test
    public void emptyFieldWarningNamesTheFieldAsWritten() {
        TrackingResolver t = new TrackingResolver(new RecordResolver((text, format) -> text.replaceAll("#form\\.[^#]+#", ""), "rec-1"));
        t.resolve("#form.leave.reason#", null);
        assertEquals(java.util.List.of("#form.leave.reason#"), t.getEmpty());
    }
}
