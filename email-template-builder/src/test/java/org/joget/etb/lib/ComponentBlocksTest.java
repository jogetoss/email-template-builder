package org.joget.etb.lib;

import static org.junit.Assert.*;

import java.util.Map;
import org.joget.etb.render.EmailRenderResult;
import org.joget.etb.render.TestSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Badge, Callout, Stat tiles, Facts list and Timeline: fields, escaping, theme colours and empty states. */
public class ComponentBlocksTest {

    static String template(Class<?> block, JSONObject props, JSONObject theme) {
        return new JSONObject().put("className", "org.joget.etb.EmailTemplateBuilder").put("properties", theme == null ? new JSONObject() : theme)
                .put("elements", new JSONArray().put(new JSONObject().put("className", block.getName()).put("properties", props))).toString();
    }

    static EmailRenderResult send(Class<?> block, JSONObject props, Map<String, String> values) {
        return TestSupport.renderer(values).render(template(block, props, null));
    }

    static JSONArray rows(String[] keys, String... cells) {
        JSONArray a = new JSONArray();
        for (int i = 0; i < cells.length; i += keys.length) {
            JSONObject o = new JSONObject();
            for (int k = 0; k < keys.length; k++) {
                o.put(keys[k], cells[i + k]);
            }
            a.put(o);
        }
        return a;
    }

    @Test
    public void badgeShowsAFieldEscapedAndAlignsWithACell() {
        JSONObject p = new JSONObject().put("text", "#form.r.status#").put("shape", "pill").put("align", "right").put("backgroundColor", "#dcfce7");
        EmailRenderResult r = send(BadgeBlock.class, p, Map.of("form.r.status", "<i>Approved</i>"));
        assertTrue(r.getHtml().contains("&lt;i&gt;Approved&lt;/i&gt;"));
        assertTrue(r.getHtml().contains("align=\"right\""));
        assertTrue(r.getHtml().contains("border-radius:999px"));
        assertEquals("<i>Approved</i>", r.getText().trim());
        JSONObject circle = new JSONObject().put("text", "JT").put("shape", "circle").put("size", "40px");
        String c = send(BadgeBlock.class, circle, Map.of()).getHtml();
        assertTrue(c.contains("width:40px;min-width:40px;max-width:40px;height:40px;line-height:40px;border-radius:50%"));
        assertTrue("one line, clipped to the circle", c.contains("width:40px;max-width:40px;overflow:hidden;white-space:nowrap;\">JT</div>"));
        EmailRenderResult empty = send(BadgeBlock.class, new JSONObject().put("text", "#form.r.none#x").put("text", ""), Map.of());
        assertTrue(empty.getWarnings().contains("Badge has no text; it was left out."));
    }

    @Test
    public void calloutStyles() {
        JSONObject bar = new JSONObject().put("style", "bar").put("title", "Due #form.r.date#").put("text", "Act now").put("accentColor", "#f59e0b");
        EmailRenderResult r = send(CalloutBlock.class, bar, Map.of("form.r.date", "1 Oct"));
        assertTrue(r.getHtml().contains("border-left:4px solid #f59e0b"));
        assertTrue(r.getHtml().contains("Due 1 Oct"));
        assertTrue(r.getText().contains("Due 1 Oct\nAct now"));
        JSONObject code = new JSONObject().put("style", "dashed").put("text", "#form.r.code#").put("code", "true");
        EmailRenderResult c = send(CalloutBlock.class, code, Map.of("form.r.code", "482 913"));
        assertTrue(c.getHtml().contains("border:2px dashed"));
        assertTrue(c.getHtml().contains("letter-spacing:10px") && c.getHtml().contains("482 913"));
        JSONObject quote = new JSONObject().put("style", "quote").put("title", "Priya").put("text", "Fast is quiet.");
        EmailRenderResult q = send(CalloutBlock.class, quote, Map.of());
        assertTrue(q.getHtml().contains("&mdash; Priya") || q.getHtml().contains("— Priya"));
        assertEquals("Fast is quiet.\n- Priya", q.getText().trim());
        assertTrue(send(CalloutBlock.class, new JSONObject().put("style", "box"), Map.of()).getWarnings().contains("Callout has no title or text; it was left out."));
    }

    @Test
    public void statTilesAreOneRowWithGuttersAndFieldNumbers() {
        JSONObject p = new JSONObject().put("tiles", rows(new String[] {"number", "caption", "background", "color"},
                "#form.k.done#", "done", "#eef2ff", "#4338ca", "94%", "on time", "not-a-colour", "")).put("gap", "12px");
        JSONObject theme = new JSONObject().put("linkColor", "#abcdef");
        EmailRenderResult r = TestSupport.renderer(Map.of("form.k.done", "128")).render(template(StatTilesBlock.class, p, theme));
        String html = r.getHtml();
        assertTrue(html.contains(">128<"));
        assertTrue("an invalid background falls back", html.contains("background-color:#f1f5f9"));
        assertTrue("an empty number colour follows the theme accent", html.contains("color:#abcdef"));
        assertTrue(html.contains("width:12px"));
        assertEquals("two tiles, each an equal share beside one gutter", 2, html.split("width=\"49%\"", -1).length - 1);
        assertEquals("128 done\n94% on time", r.getText().trim());
        assertTrue("they stack on phones", html.contains("class=\"etb-tile\"") && html.contains("class=\"etb-tile-gap\""));
        assertTrue(html.contains("@media only screen and (max-width:480px){td.etb-tile,td.etb-tile-gap{display:block !important;"));

        p.put("keepSideBySide", "true");
        String kept = TestSupport.renderer(Map.of("form.k.done", "128")).render(template(StatTilesBlock.class, p, theme)).getHtml();
        assertFalse("kept side by side: nothing to stack", kept.contains("class=\"etb-tile"));
    }

    @Test
    public void factsHideEmptyValuesOnlyWhenAsked() {
        JSONObject p = new JSONObject().put("facts", rows(new String[] {"label", "value"}, "Status", "#form.r.status#", "Owner", "#form.r.owner#"));
        EmailRenderResult all = send(FactsBlock.class, p, Map.of("form.r.status", "Open", "form.r.owner", ""));
        assertTrue(all.getHtml().contains("Owner"));
        p.put("hideEmpty", "true");
        EmailRenderResult some = send(FactsBlock.class, p, Map.of("form.r.status", "Open", "form.r.owner", ""));
        assertFalse(some.getHtml().contains("Owner"));
        assertTrue("a label wraps between words, never inside one", all.getHtml().contains("word-break:normal;overflow-wrap:normal;"));
        assertEquals("Status: Open", some.getText().trim());
        EmailRenderResult canvas = TestSupport.previewRenderer().render(template(FactsBlock.class, p, null));
        assertTrue("the canvas keeps rows whose field is unresolved", canvas.getHtml().contains("Owner"));
        p.put("valueAlign", "left").put("labelStyle", "mono").put("labelWidth", "20%");
        String memo = send(FactsBlock.class, p, Map.of("form.r.status", "Open")).getHtml();
        assertTrue(memo.contains("width=\"20%\"") && memo.contains("Courier New"));
    }

    @Test
    public void timelineDropsTheTimeColumnWhenNoItemHasATime() {
        JSONObject p = new JSONObject().put("items", rows(new String[] {"time", "title", "detail"}, "10:00", "Welcome", "Priya", "10:05", "Roadmap", ""));
        EmailRenderResult r = send(TimelineBlock.class, p, Map.of());
        assertTrue(r.getHtml().contains(">10:00<"));
        assertEquals("- 10:00 Welcome - Priya\n- 10:05 Roadmap", r.getText().trim());
        JSONObject untimed = new JSONObject().put("items", rows(new String[] {"time", "title", "detail"}, "", "Investigating", "#form.i.note#", "", "Fixed", ""));
        EmailRenderResult u = send(TimelineBlock.class, untimed, Map.of("form.i.note", "<b>x</b>"));
        assertFalse(u.getHtml().contains("white-space:nowrap"));
        assertTrue(u.getHtml().contains("&lt;b&gt;x&lt;/b&gt;"));
        assertTrue(send(TimelineBlock.class, new JSONObject(), Map.of()).getWarnings().contains("Timeline has no items; it was left out."));
    }
}
