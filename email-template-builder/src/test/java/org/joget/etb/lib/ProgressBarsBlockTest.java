package org.joget.etb.lib;

import static org.junit.Assert.*;

import java.util.Map;
import org.joget.etb.render.EmailRenderResult;
import org.joget.etb.render.TestSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ProgressBarsBlockTest {

    static String template(String... labelValue) {
        JSONArray bars = new JSONArray();
        for (int i = 0; i < labelValue.length; i += 2) {
            bars.put(new JSONObject().put("label", labelValue[i]).put("value", labelValue[i + 1]).put("color", ""));
        }
        JSONObject props = new JSONObject().put("bars", bars).put("showValue", "true").put("previewValue", "40").put("color", "#111111")
                .put("trackColor", "#eeeeee").put("height", "8px").put("rounded", "true");
        return new JSONObject().put("className", "org.joget.etb.EmailTemplateBuilder").put("properties", new JSONObject())
                .put("elements", new JSONArray().put(new JSONObject().put("className", ProgressBarsBlock.class.getName()).put("properties", props))).toString();
    }

    static EmailRenderResult send(Map<String, String> values, String... labelValue) {
        return TestSupport.renderer(values).render(template(labelValue));
    }

    @Test
    public void percentagesAndFractionsFillTheBar() {
        EmailRenderResult r = send(Map.of("form.p.done", "12", "form.p.total", "20"),
                "Goal", "86", "Budget", "62.4 %", "Tasks", "#form.p.done#/#form.p.total#");
        assertTrue(r.getWarnings().toString(), r.getWarnings().isEmpty());
        assertTrue(r.getHtml().contains("width=\"86%\""));
        assertTrue(r.getHtml().contains("width=\"62%\""));
        assertTrue(r.getHtml().contains("width=\"60%\""));
        assertTrue(r.getHtml().contains("12 / 20"));
        assertTrue(r.getText().contains("Goal: 86%"));
    }

    @Test
    public void emptyAndFullBarsHaveOneCell() {
        EmailRenderResult r = send(Map.of(), "None", "0", "All", "100");
        String html = r.getHtml();
        assertFalse(html.contains("width=\"0%\""));
        assertTrue("a full bar: one cell, rounded at both ends", html.contains("background-color:#111111;border-radius:4px;\" width=\"100%\""));
        assertTrue("an empty bar: only the remaining cell", html.contains("background-color:#eeeeee;border-radius:4px;\">"));
    }

    @Test
    public void outOfRangeAndNonNumbersWarn() {
        EmailRenderResult r = send(Map.of("form.p.v", "lots"), "Over", "140", "Bad", "#form.p.v#", "Empty", "#form.p.none#");
        assertTrue(r.getHtml().contains("background-color:#111111;border-radius:4px;\" width=\"100%\""));
        assertEquals(3, r.getWarnings().size());
        assertTrue(r.getWarnings().get(0).contains("outside 0-100%"));
        assertTrue(r.getWarnings().get(1).contains("\"lots\" is not a number"));
        assertTrue(r.getWarnings().get(2).contains("(#form.p.none#) has no value; shown as 0%"));
    }

    @Test
    public void theCanvasShowsThePreviewValueForAField() {
        EmailRenderResult canvas = TestSupport.previewRenderer().render(template("Tasks", "#form.p.done#"));
        assertTrue(canvas.getHtml().contains("width=\"40%\""));
    }

    @Test
    public void valuesInTheMarkupAreNeverTheAuthorsRawText() {
        EmailRenderResult r = send(Map.of("form.p.v", "50\" onmouseover=\"x"), "Hack", "#form.p.v#");
        assertFalse(r.getHtml().contains("onmouseover"));
    }
}
