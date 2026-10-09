package org.joget.etb.lib;

import static org.junit.Assert.*;

import java.util.Map;
import org.joget.etb.render.EmailRenderResult;
import org.joget.etb.render.EmailTemplateRenderer;
import org.joget.etb.render.TestSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class StepsBlockTest {

    static final String ACCENT = "#123456";

    static String template(String current, boolean lastDone) {
        JSONArray steps = new JSONArray()
                .put(new JSONObject().put("label", "Submitted").put("matches", "New, Pending"))
                .put(new JSONObject().put("label", "In review").put("matches", "Reviewing"))
                .put(new JSONObject().put("label", "Done").put("matches", ""));
        JSONObject props = new JSONObject().put("steps", steps).put("current", current).put("previewStep", "2")
                .put("lastDone", lastDone ? "true" : "").put("doneMark", "check").put("color", ACCENT);
        return new JSONObject().put("className", "org.joget.etb.EmailTemplateBuilder").put("properties", new JSONObject())
                .put("elements", new JSONArray().put(new JSONObject().put("className", StepsBlock.class.getName()).put("properties", props))).toString();
    }

    static EmailRenderResult send(String current, Map<String, String> values) {
        return TestSupport.renderer(values).render(template(current, true));
    }

    /** How many steps are drawn done (a check mark) and whether one is current (accent label in bold). */
    static int checks(EmailRenderResult r) {
        return r.getHtml().split("&#10003;|✓", -1).length - 1;
    }

    @Test
    public void aNumberIsTheCurrentStep() {
        EmailRenderResult r = send("2", Map.of());
        assertEquals(1, checks(r));
        assertTrue(r.getHtml().contains("color:" + ACCENT + ";font-weight:bold;\">In review"));
        assertTrue(r.getWarnings().isEmpty());
        assertTrue(r.getText().contains("Submitted (done) > In review (current) > Done"));
    }

    @Test
    public void aFieldIsMatchedAgainstLabelsAndAlsoMatchesIgnoringCase() {
        assertEquals(1, checks(send("#form.request.status#", Map.of("form.request.status", "in REVIEW"))));
        assertEquals(1, checks(send("#form.request.status#", Map.of("form.request.status", "reviewing"))));
        EmailRenderResult pending = send("#form.request.status#", Map.of("form.request.status", " Pending "));
        assertEquals(0, checks(pending));
        assertTrue(pending.getText().contains("Submitted (current)"));
    }

    @Test
    public void reachingTheLastStepCompletesItUnlessTurnedOff() {
        EmailRenderResult done = send("#form.request.status#", Map.of("form.request.status", "Done"));
        assertEquals(3, checks(done));
        EmailRenderResult open = TestSupport.renderer(Map.of()).render(template("3", false));
        assertEquals(2, checks(open));
        assertTrue(open.getText().contains("Done (current)"));
        assertEquals("above the count: all done", 3, checks(send("9", Map.of())));
    }

    @Test
    public void anUnknownOrEmptyValueShowsEveryStepUpcomingWithAWarning() {
        EmailRenderResult unknown = send("#form.request.status#", Map.of("form.request.status", "Cancelled"));
        assertEquals(0, checks(unknown));
        assertFalse(unknown.getText().contains("(current)"));
        assertTrue(unknown.getWarnings().toString(), unknown.getWarnings().get(0).contains("\"Cancelled\" matches no step"));
        EmailRenderResult empty = send("#form.request.status#", Map.of("form.request.status", ""));
        assertEquals(0, checks(empty));
        assertTrue(empty.getWarnings().get(0).contains("has no value, so every step shows as upcoming"));
    }

    @Test
    public void theBuilderShowsThePreviewStepWhileTheFieldHasNoValue() {
        EmailRenderResult canvas = TestSupport.previewRenderer().render(template("#form.request.status#", true));
        assertEquals(1, checks(canvas));
        EmailRenderResult preview = new EmailTemplateRenderer(org.joget.etb.render.BlockFactory.reflective(), TestSupport.resolver(Map.of("form.request.status", "")),
                false, null, null).forBuilder().render(template("#form.request.status#", true));
        assertEquals(1, checks(preview));
        assertTrue(preview.getWarnings().get(0).contains("the builder shows step 2"));
    }

    @Test
    public void labelsAreEscapedAndLeftEmptyColoursFollowTheTheme() {
        JSONObject t = new JSONObject(template("1", true));
        t.put("properties", new JSONObject().put("linkColor", "#abcdef"));
        JSONObject props = t.getJSONArray("elements").getJSONObject(0).getJSONObject("properties");
        props.put("color", "");
        props.getJSONArray("steps").getJSONObject(1).put("label", "#form.x#");
        EmailRenderResult r = TestSupport.renderer(Map.of("form.x", "<b>x</b>")).render(t.toString());
        assertTrue(r.getHtml().contains("&lt;b&gt;x&lt;/b&gt;"));
        assertTrue(r.getHtml().contains("border:2px solid #abcdef"));
    }
}
