package org.joget.etb;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import org.joget.etb.render.BlockFactory;
import org.joget.etb.render.EmailRenderResult;
import org.joget.etb.render.EmailTemplateRenderer;
import org.json.JSONObject;
import org.joget.etb.render.TestSupport;
import org.junit.Test;

/**
 * Every bundled starter must open cleanly: renders with no warnings, every merge field it uses has a
 * gallery sample, and the id is safe. Writes target/starter-previews/ID.html for a visual check.
 */
public class StarterCatalogTest {

    @Test
    public void everyStarterRendersCleanlyWithSamples() throws Exception {
        StarterCatalog catalog = new StarterCatalog();
        assertEquals(27, catalog.getStarters().size());
        File out = new File("target/starter-previews");
        out.mkdirs();
        for (StarterCatalog.Starter s : catalog.getStarters()) {
            String json = catalog.load(s.id);
            assertNotNull(s.id, json);
            assertEquals("org.joget.etb.EmailTemplateBuilder", new JSONObject(json).getString("className"));
            // as the gallery thumbnail renders it: sample merge values, sample variable values, builder mode
            EmailRenderResult r = new EmailTemplateRenderer(BlockFactory.reflective(), catalog.sampleResolver(), false, null, null)
                    .withTestVariables(catalog.variableSamples()).render(json);
            // expected: images without a source, and data blocks showing their sample rows (starters ship with both)
            r.getWarnings().forEach(w -> assertTrue(s.id + ": " + w, w.startsWith("Image ") || w.contains("shows sample rows")));
            assertFalse(s.id + ": unfilled {var.}/{row.} field", r.getHtml().matches("(?s).*\\{(var|row)\\.[A-Za-z0-9_.]+\\}.*"));
            Matcher leftover = StarterCatalog.HASH.matcher(r.getHtml().replaceAll("(?s)<style.*?</style>", ""));
            assertFalse(s.id + ": merge field without a sample: " + (leftover.find() ? leftover.group() : ""), leftover.find(0));
            assertFalse(s.id + ": MJML left uncompiled", r.getHtml().contains("<mj-"));
            assertTrue(s.id + " stays well under Gmail's clip", r.getSizeBytes() < 60 * 1024);
            Files.write(new File(out, s.id + ".html").toPath(), r.getHtml().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void onlyBundledIdsLoad() {
        StarterCatalog catalog = new StarterCatalog();
        assertNotNull(catalog.load(StarterCatalog.DEFAULT_ID));
        for (String bad : new String[] {null, "", "../index", "index", "Newsletter", "nope", "a/b", "blank.json"}) {
            assertNull(String.valueOf(bad), catalog.load(bad));
        }
    }

    @Test
    public void galleryIndexListsEveryStarterInOrder() {
        StarterCatalog catalog = new StarterCatalog();
        org.json.JSONArray list = catalog.indexJson().getJSONArray("starters");
        assertEquals(catalog.getStarters().size(), list.length());
        assertEquals(StarterCatalog.DEFAULT_ID, list.getJSONObject(0).getString("id"));
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            assertNotNull(catalog.load(o.getString("id")));
            assertFalse(o.getString("label").isEmpty());
            assertFalse(o.getString("category").isEmpty());
        }
    }

    @Test
    public void aRealSendNeverShowsSampleRows() {
        StarterCatalog catalog = new StarterCatalog();
        EmailRenderResult sent = new EmailTemplateRenderer(BlockFactory.reflective(), catalog.sampleResolver(), false, null, null)
                .withVariables(catalog.variableSamples()).render(catalog.load("receipt"));
        assertFalse(sent.getHtml().contains("Laptop stand"));
        assertTrue(sent.getWarnings().contains("Datalist Table has no datalist chosen; it was left out."));
        EmailRenderResult digest = new EmailTemplateRenderer(BlockFactory.reflective(), catalog.sampleResolver(), false, null, null)
                .withVariables(catalog.variableSamples()).render(catalog.load("digest"));
        assertFalse(digest.getHtml().contains("Decide on the launch date"));
        assertTrue(digest.getWarnings().contains("Repeat has no datalist chosen; it was left out."));
    }

    @Test
    public void theBuilderShowsThem() {
        StarterCatalog catalog = new StarterCatalog();
        EmailRenderResult r = new EmailTemplateRenderer(BlockFactory.reflective(), catalog.sampleResolver(), false, null, null)
                .withTestVariables(catalog.variableSamples()).render(catalog.load("receipt"));
        assertTrue(r.getHtml().contains("Laptop stand") && r.getHtml().contains("USD 129.00"));
        assertTrue(r.getHtml().contains("SO-10482"));
        EmailRenderResult canvas = TestSupport.previewRenderer().render(catalog.load("tasks"));
        assertTrue("the canvas shows the sample cards", canvas.getMjml().contains("Prepare the Q4 budget"));
    }

    @Test
    public void everyComponentRendersCleanlyOnItsOwnAndInsertsAsOneElement() throws Exception {
        StarterCatalog catalog = StarterCatalog.components();
        assertTrue(catalog.getStarters().size() >= 15);
        File out = new File("target/component-previews");
        out.mkdirs();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (StarterCatalog.Starter c : catalog.getStarters()) {
            assertTrue("unique id " + c.id, ids.add(c.id));
            JSONObject component = new JSONObject(catalog.load(c.id));
            // a component goes into any template: {var.} needs a variable the template may not declare, {row.} a Repeat
            assertFalse(c.id + ": uses a template variable or row field", catalog.load(c.id).matches("(?s).*\\{(var|row)\\..*"));
            String className = component.getJSONObject("element").getString("className");
            assertNotNull(c.id + ": a bundled block", BlockFactory.reflective().create(className));
            String place = component.optString("place", "");
            assertTrue(c.id + ": place", place.isEmpty() || place.equals("first") || place.equals("last"));
            EmailRenderResult r = new EmailTemplateRenderer(BlockFactory.reflective(), catalog.sampleResolver(), false, null, null)
                    .withTestVariables(catalog.variableSamples()).render(catalog.componentPreviewJson(c.id));
            assertTrue(c.id + ": " + r.getWarnings(), r.getWarnings().isEmpty());
            assertFalse(c.id + ": MJML left uncompiled", r.getHtml().contains("<mj-"));
            Matcher leftover = StarterCatalog.HASH.matcher(r.getHtml().replaceAll("(?s)<style.*?</style>", ""));
            assertFalse(c.id + ": merge field without a sample: " + (leftover.find() ? leftover.group() : ""), leftover.find(0));
            // a component is also valid inside a column or a repeat's card when it is a content block
            Files.write(new File(out, c.id + ".html").toPath(), r.getHtml().getBytes(StandardCharsets.UTF_8));
        }
        // a contact sheet of all of them, to look at in a browser
        StringBuilder sheet = new StringBuilder("<!doctype html><meta charset=\"utf-8\"><title>Components</title><style>body{font:12px Arial,sans-serif;"
                + "background:#ccc;margin:8px}div{display:inline-block;vertical-align:top;margin:4px;background:#fff}iframe{width:640px;border:0;display:block}</style>");
        for (StarterCatalog.Starter c : catalog.getStarters()) {
            sheet.append("<div><b>").append(c.id).append("</b><iframe src=\"").append(c.id)
                    .append(".html\" onload=\"this.style.height=this.contentDocument.documentElement.scrollHeight+'px'\"></iframe></div>");
        }
        Files.write(new File(out, "index.html").toPath(), sheet.toString().getBytes(StandardCharsets.UTF_8));
        assertNull(catalog.load("../starters/blank"));
        assertNull(catalog.componentPreviewJson("nope"));
    }

    @Test
    public void theStatusStepsComponentFollowsItsField() {
        StarterCatalog catalog = StarterCatalog.components();
        // the gallery sample for #form.request.status# is "Approved": the third of four steps
        EmailRenderResult r = new EmailTemplateRenderer(BlockFactory.reflective(), catalog.sampleResolver(), false, null, null)
                .forBuilder().render(catalog.componentPreviewJson("steps-status"));
        assertTrue(r.getText(), r.getText().contains("Submitted (done) > In review (done) > Approved (current) > Done"));
    }

    @Test
    public void theCatalogsAreReadOnceAndCannotBeChanged() {
        assertSame(StarterCatalog.starters(), StarterCatalog.starters());
        assertSame(StarterCatalog.components(), StarterCatalog.components());
        try {
            StarterCatalog.starters().getStarters().clear();
            fail("the shared list is read-only");
        } catch (UnsupportedOperationException expected) {
            assertEquals(27, StarterCatalog.starters().getStarters().size());
        }
    }

    /** Emoji look informal in a business email and some apps draw them flat or as boxes: icon places hold a symbol, a number or a word. */
    @Test
    public void noStarterOrComponentUsesEmoji() {
        java.util.regex.Pattern emoji = java.util.regex.Pattern.compile("&#(\\d+);|[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2300}-\\x{23FF}]");
        // plain text symbols that draw in the text colour everywhere: check, cross, ballot box, warning, star, sparkle (shapes such as a dot are outside the ranges)
        java.util.Set<Integer> symbols = new java.util.HashSet<>(java.util.Arrays.asList(0x2713, 0x2715, 0x2610, 0x26A0, 0x2605, 0x2726));
        java.util.List<String[]> all = new java.util.ArrayList<>();
        StarterCatalog starters = StarterCatalog.starters();
        starters.getStarters().forEach(s -> all.add(new String[] {s.id, starters.load(s.id)}));
        StarterCatalog components = StarterCatalog.components();
        components.getStarters().forEach(c -> all.add(new String[] {c.id, components.load(c.id)}));
        for (String[] item : all) {
            Matcher m = emoji.matcher(item[1]);
            while (m.find()) {
                int cp = m.group(1) != null ? Integer.parseInt(m.group(1)) : m.group().codePointAt(0);
                boolean isEmoji = cp >= 0x1F000 || ((cp >= 0x2300 && cp <= 0x23FF || cp >= 0x2600 && cp <= 0x27BF) && !symbols.contains(cp));
                assertFalse(item[0] + " uses an emoji: " + m.group(), isEmoji);
            }
        }
    }
}
