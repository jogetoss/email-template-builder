package org.joget.etb.render;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.regex.Matcher;
import org.joget.commons.util.StringUtil;
import org.junit.Test;

/**
 * How Joget escapes a merge value (AppUtil.processHashVariable), and why the plugin gives every merge field a format of
 * its own. The fake follows AppUtil: a field's own ?format escapes; without one the value goes through the HTML
 * sanitiser (stripHtmlRelaxed); then the caller's escapeFormat applies. Unknown fields are left as written.
 */
public class MergeFormatTest {

    static final Map<String, String> VALUES = Map.of("form.p.price", "Price < 100", "form.p.name", "Tom & Jerry",
            "form.p.code", "<script>x()</script>after", "date.EEE, d MMM yyyy", "Fri, 25 Sep 2026");

    /** AppUtil.processHashVariable, as far as escaping goes. */
    static final MergeResolver JOGET = (text, apiFormat) -> {
        Matcher m = MergeResolver.TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String body = m.group(1);
            String format = "";
            int q = body.lastIndexOf('?');
            if (q > 0) {
                format = body.substring(q + 1);
                body = body.substring(0, q);
            }
            String v = VALUES.get(body);
            if (v == null) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            if (!format.isEmpty()) {
                v = StringUtil.escapeString(v, format, null);
            }
            if (!format.equals("html") && !format.equals("noescape") && !format.equals("url")) {
                v = StringUtil.stripHtmlRelaxed(v);
            }
            v = StringUtil.escapeString(v, apiFormat, null);
            m.appendReplacement(out, Matcher.quoteReplacement(v));
        }
        m.appendTail(out);
        return out.toString();
    };

    static String html(String text) {
        return MergeResolver.withOwnFormat(JOGET, text, StringUtil.TYPE_HTML);
    }

    static String plain(String text) {
        return MergeResolver.withOwnFormat(JOGET, text, null);
    }

    @Test
    public void jogetAloneEscapesALessThanTwiceAndDropsScripts() {
        // the problem this class exists for
        assertEquals("Price &amp;lt; 100", JOGET.resolve("#form.p.price#", StringUtil.TYPE_HTML));
        assertEquals("after", JOGET.resolve("#form.p.code#", StringUtil.TYPE_HTML).replaceAll("\\s", ""));
    }

    @Test
    public void inHtmlAValueIsEscapedOnce() {
        assertEquals("<p>Price &lt; 100 / Tom &amp; Jerry</p>", html("<p>#form.p.price# / #form.p.name#</p>"));
        assertEquals("shown as text, not dropped", "&lt;script&gt;x()&lt;/script&gt;after", html("#form.p.code#"));
    }

    @Test
    public void inPlainTextAValueIsKeptAsIs() {
        assertEquals("Price < 100 / Tom & Jerry", plain("#form.p.price# / #form.p.name#"));
        assertEquals("<script>x()</script>after", plain("#form.p.code#"));
    }

    @Test
    public void theAuthorsOwnFormatAndUnknownFieldsAreLeftAlone() {
        assertEquals("the author's format wins", "Price%20%3C%20100", html("#form.p.price?url#"));
        assertEquals("unresolved stays as written, without our format", "[#form.p.none#]", html("[#form.p.none#]"));
        assertEquals("Fri, 25 Sep 2026", html("#date.EEE, d MMM yyyy#"));
        assertEquals("text without fields is untouched", "Issue #12 and #13 in #fff", html("Issue #12 and #13 in #fff"));
        assertEquals("", html(null));
    }

    @Test
    public void theTrustedBlockKeepsJogetsSanitiser() {
        // HtmlBlock: no escaping, but a field without a format is still sanitised by Joget (scripts removed)
        MergeResolver joget = new MergeResolver() {
            public String resolve(String text, String escapeFormat) {
                return MergeResolver.withOwnFormat(JOGET, text, escapeFormat);
            }

            public String resolveTrusted(String text) {
                return JOGET.resolve(text, null);
            }
        };
        assertFalse(joget.resolveTrusted("#form.p.code#").contains("<script>"));
        assertTrue(joget.resolve("#form.p.code#", null).contains("<script>"));
        // the builder's wrappers (test record, empty-field tracking) pass the trusted path through, record id included
        java.util.List<String> trustedCalls = new java.util.ArrayList<>();
        MergeResolver spy = new MergeResolver() {
            public String resolve(String text, String escapeFormat) {
                return "escaped";
            }

            public String resolveTrusted(String text) {
                trustedCalls.add(text);
                return "trusted";
            }
        };
        assertEquals("trusted", new TrackingResolver(new RecordResolver(spy, "rec-1")).resolveTrusted("#form.p.code#"));
        assertEquals(java.util.List.of("#form.p.code[rec-1]#"), trustedCalls);
    }
}
