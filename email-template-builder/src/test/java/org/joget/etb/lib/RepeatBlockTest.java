package org.joget.etb.lib;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joget.apps.app.model.AppDefinition;
import org.joget.etb.render.EmailRenderResult;
import org.joget.etb.render.TestSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RepeatBlockTest {

    static final String TEXT = TextBlock.class.getName();
    static final String BUTTON = ButtonBlock.class.getName();

    /** "tasks": two planner-style rows; "changes": the changes of the task given by the id filter. */
    static class FakeSource extends DatalistSource {
        final List<String> calls = new ArrayList<>();

        @Override
        public Table read(AppDefinition appDef, String listId, List<String> columns, Map<String, String> filters,
                Map<String, List<String>> params, String sortBy, boolean desc, int maxRows, CellMode mode, boolean fetchRows) {
            calls.add(listId + filters + (params.isEmpty() ? "" : " params" + params));
            Table t = new Table();
            if ("tasks".equals(listId)) {
                t.records.add(task("t1", "Implement WAF", "Hugo Lim", "<ol><li>Start from to 2026-09-18</li><script>x()</script></ol>", "https://x.com/t?id=t1"));
                t.records.add(task("t2", "#envVariable.secret# & <b>co</b>", "Eve", "hi", "javascript:alert(1)"));
            } else if ("partial".equals(listId)) {
                // rows came back, but some were dropped because the data source ignored the filters
                t.records.add(task("t1", "Kept", "Ann", "", ""));
                t.problem = "Datalist \"partial\" ignored the filters; 3 rows that don't match were dropped.";
            } else if ("changes".equals(listId)) {
                t.names.add("change");
                t.headers.add("Change");
                t.rows.add(List.of("Status of " + filters.get("task_id")));
                t.raw.add(List.of("Status of " + filters.get("task_id")));
            }
            return t;
        }

        static Record task(String id, String title, String author, String body, String link) {
            Map<String, Object> row = new HashMap<>(Map.of("id", id));
            Record r = new Record(row);
            r.put("title", title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"), title);
            r.put("author", author, author);
            r.put("body", body, body);
            r.put("link", link, link);
            return r;
        }
    }

    FakeSource fake;
    DatalistSource real;

    @Before
    public void useFake() {
        real = DatalistSource.instance;
        fake = new FakeSource();
        DatalistSource.instance = fake;
    }

    @After
    public void restore() {
        DatalistSource.instance = real;
    }

    static JSONObject el(String className, JSONObject props, JSONObject... children) {
        JSONObject o = new JSONObject().put("className", className).put("properties", props);
        JSONArray a = new JSONArray();
        for (JSONObject c : children) {
            a.put(c);
        }
        return o.put("elements", a);
    }

    static JSONObject repeat(String listId, JSONObject... children) {
        return el(RepeatBlock.class.getName(), new JSONObject(new RepeatBlock().getDefaultPropertyValues()).put("listId", listId), children);
    }

    static JSONObject text(String html) {
        return el(TEXT, new JSONObject().put("html", html));
    }

    static String template(JSONObject... elements) {
        JSONArray a = new JSONArray();
        for (JSONObject e : elements) {
            a.put(e);
        }
        return new JSONObject().put("className", "org.joget.etb.EmailTemplateBuilder").put("properties", new JSONObject()).put("elements", a).toString();
    }

    static EmailRenderResult render(Map<String, String> values, JSONObject... elements) {
        return TestSupport.renderer(values).render(template(elements));
    }

    @Test
    public void oneCardPerRowWithRowFieldsFilled() {
        EmailRenderResult r = render(Map.of(), repeat("tasks",
                text("<p><b>{row.title}</b> by {row.author}</p>{row.body?html}<p>{row.id}</p>"),
                el(BUTTON, new JSONObject().put("label", "Open").put("href", "{row.link}"))));
        String m = r.getMjml();
        assertEquals("a card per row", 2, m.split("<mj-wrapper background-color=\"#f8fafc\"", -1).length - 1);
        assertTrue(m.contains("<b>Implement WAF</b> by Hugo Lim"));
        assertTrue("?html keeps the formatter's list", m.replaceAll("\\s*\n\\s*", "").contains("<ol><li>Start from to 2026-09-18</li></ol>"));
        assertFalse("but not scripts", m.contains("<script>"));
        assertTrue(m.contains("<p>t1</p>") && m.contains("<p>t2</p>"));
        assertTrue(m.contains("href=\"https://x.com/t?id=t1\""));
        assertFalse(m.contains("javascript:"));
        assertTrue(r.getText().startsWith("Implement WAF by Hugo Lim"));
    }

    @Test
    public void rowDataIsNeverResolvedAsMergeFieldsAndIsEscaped() {
        EmailRenderResult r = render(Map.of("envVariable.secret", "TOP-SECRET"), repeat("tasks", text("<p>{row.title}</p>")));
        assertFalse(r.getMjml().contains("TOP-SECRET"));
        assertFalse(r.getText().contains("TOP-SECRET"));
        assertTrue(r.getMjml().contains("<p>#envVariable.secret# &amp; &lt;b&gt;co&lt;/b&gt;</p>"));
    }

    @Test
    public void authorMergeFieldsStillWorkInsideTheRepeat() {
        EmailRenderResult r = render(Map.of("currentUser.firstName", "Alex"), repeat("tasks", text("<p>Hi #currentUser.firstName#: {row.author}</p>")));
        assertTrue(r.getMjml().contains("<p>Hi Alex: Hugo Lim</p>"));
    }

    @Test
    public void aTableInsideGetsEachRowsOwnList() {
        JSONObject changes = el(DatalistTableBlock.class.getName(), new JSONObject(new DatalistTableBlock().getDefaultPropertyValues())
                .put("listId", "changes").put("filters", new JSONArray().put(new JSONObject().put("field", "task_id").put("value", "{row.id}"))));
        EmailRenderResult r = render(Map.of(), repeat("tasks", changes));
        assertTrue(fake.calls.contains("changes{task_id=t1}"));
        assertTrue(fake.calls.contains("changes{task_id=t2}"));
        assertTrue(r.getMjml().contains("Status of t1") && r.getMjml().contains("Status of t2"));
    }

    @Test
    public void linksBuiltFromAFieldAreEncoded() {
        EmailRenderResult r = render(Map.of(), repeat("tasks", el(BUTTON, new JSONObject().put("label", "Open").put("href", "https://x.com/task?t={row.title}"))));
        assertTrue(r.getMjml().contains("href=\"https://x.com/task?t=Implement+WAF\""));
        assertTrue(r.getMjml().contains("t=%23envVariable.secret%23+%26+%3Cb%3Eco%3C%2Fb%3E"));
    }

    @Test
    public void unknownFieldsAreEmptyWithOneWarning() {
        EmailRenderResult r = render(Map.of(), repeat("tasks", text("<p>[{row.nope}]</p>")));
        assertTrue(r.getMjml().contains("<p>[]</p>"));
        assertEquals(1, r.getWarnings().stream().filter(w -> w.startsWith("{row.nope}")).count());
    }

    @Test
    public void canvasShowsTheDesignOnceWithoutQuerying() {
        EmailRenderResult r = TestSupport.previewRenderer().render(template(repeat("tasks", text("<p>{row.title}</p>"))));
        assertTrue(fake.calls.isEmpty());
        assertTrue(r.getMjml().contains("<p>{row.title}</p>"));
    }

    @Test
    public void emptyAndUnsetRepeats() {
        EmailRenderResult empty = render(Map.of(), repeat("none", text("<p>{row.title}</p>")));
        assertTrue(empty.getMjml().contains("Nothing to show."));
        assertFalse(empty.getMjml().contains("{row.title}"));
        EmailRenderResult unset = render(Map.of(), repeat("", text("<p>x</p>")));
        assertTrue(unset.getWarnings().contains("Repeat has no datalist chosen; it was left out."));
        assertFalse(unset.getMjml().contains("<p>x</p>"));
    }

    @Test
    public void rowFieldsOutsideARepeatAreLeftAsWrittenWithAWarning() {
        EmailRenderResult r = render(Map.of(), text("<p>{row.title}</p>"), text("<p>{row.other}</p>"));
        assertTrue(r.getMjml().contains("<p>{row.title}</p>"));
        assertEquals("one warning for all such fields", 1, r.getWarnings().stream().filter(w -> w.contains("only filled inside a Repeat")).count());
    }

    // {var.NAME}: template variables, declared in Settings, mapped by the sender, preview values in the builder

    static JSONObject declares(String... namePreview) {
        JSONArray vars = new JSONArray();
        for (int i = 0; i < namePreview.length; i += 2) {
            vars.put(new JSONObject().put("name", namePreview[i]).put("previewValue", namePreview[i + 1]).put("description", ""));
        }
        return new JSONObject().put("variables", vars);
    }

    static String templateWith(JSONObject templateProps, JSONObject... elements) {
        JSONArray a = new JSONArray();
        for (JSONObject e : elements) {
            a.put(e);
        }
        return new JSONObject().put("className", "org.joget.etb.EmailTemplateBuilder").put("properties", templateProps).put("elements", a).toString();
    }

    static final Map<String, String> BOARD = Map.of("boardId", "b-7", "boardName", "Security <Ops>", "planner", "InfoSec");

    @Test
    public void mappedValuesFillVariablesAndTheSubject() {
        JSONObject props = declares("boardId", "b-preview", "boardName", "Preview board", "planner", "P")
                .put("subject", "Daily Updates for {var.boardName} Board on #date.dd-MM-yyyy#");
        EmailRenderResult r = TestSupport.renderer(Map.of("date.dd-MM-yyyy", "25-09-2026")).withVariables(BOARD)
                .render(templateWith(props, text("<p>Daily Update for {var.boardName} Board</p>"),
                        el(BUTTON, new JSONObject().put("label", "Open {var.planner}").put("href", "https://x.com/jw/web/userview/app/v/{var.boardId}/"))));
        assertEquals("Daily Updates for Security <Ops> Board on 25-09-2026", r.getSubject());
        assertTrue("escaped in the body", r.getMjml().contains("<p>Daily Update for Security &lt;Ops&gt; Board</p>"));
        assertTrue(r.getMjml().contains("href=\"https://x.com/jw/web/userview/app/v/b-7/\""));
        assertTrue(r.getMjml().contains(">Open InfoSec</mj-button>"));
        assertFalse("never the preview values when sending", r.getMjml().contains("Preview board"));
        assertTrue(r.getWarnings().isEmpty());
    }

    @Test
    public void theBuilderUsesItsTestValues() {
        EmailRenderResult r = TestSupport.renderer(Map.of()).withTestVariables(Map.of("boardName", "Information Security", "stale", "x"))
                .render(templateWith(declares("boardName", "", "boardId", ""), text("<p>{var.boardName}[{var.boardId}]</p>")));
        assertTrue(r.getMjml().contains("<p>Information Security[]</p>"));
        assertEquals("stale browser values are ignored quietly", List.of("Template variable \"boardId\" has no test value (Test data); it was left empty."), r.getWarnings());
    }

    @Test
    public void withoutValuesDeclaredVariablesAreEmptyAndQuiet() {
        EmailRenderResult r = TestSupport.renderer(Map.of()).render(templateWith(declares("boardName", "old preview value"), text("<p>[{var.boardName}]</p>")));
        assertTrue("an old template's previewValue is never used by the renderer", r.getMjml().contains("<p>[]</p>"));
        assertTrue(r.getWarnings().isEmpty());
    }

    @Test
    public void unmappedUndeclaredAndUnknownVariablesAreReported() {
        EmailRenderResult r = TestSupport.renderer(Map.of()).withVariables(Map.of("extra", "x"))
                .render(templateWith(declares("boardName", "Preview"), text("<p>[{var.boardName}][{var.nope}]</p>")));
        assertTrue("an unmapped variable is sent empty, not with its preview value", r.getMjml().contains("<p>[][]</p>"));
        assertTrue(r.getWarnings().contains("Template variable \"boardName\" was given no value by the sender; it was left empty."));
        assertTrue(r.getWarnings().contains("\"extra\" is not a variable of this template (Settings); its value was ignored."));
        assertTrue(r.getWarnings().contains("{var.nope} is not a variable of this template; add it in Settings (Template variables). It was left empty."));
    }

    @Test
    public void rowStaysTheRepeatsRowEvenWithVariables() {
        EmailRenderResult r = TestSupport.renderer(Map.of()).withVariables(Map.of()).render(template(text("<p>{row.name}</p>")));
        assertTrue(r.getMjml().contains("<p>{row.name}</p>"));
        assertTrue(r.getWarnings().contains("{row.…} fields are only filled inside a Repeat block; they were left as written."));
    }

    @Test
    public void insideARepeatCardsSeeTheirRowAndTheVariables() {
        JSONObject rep = repeat("tasks", text("<p>{row.title} on {var.boardName}</p>"));
        rep.getJSONObject("properties").put("filterParams", new JSONArray()
                .put(new JSONObject().put("name", "d-4040985-fn_board_id").put("value", "{var.boardId}"))
                .put(new JSONObject().put("name", "fn_dateCreated").put("value", "#date.DAY-1.yyyy-MM-dd#"))
                .put(new JSONObject().put("name", "dateCreated").put("value", "#date.DAY-0.yyyy-MM-dd#"))
                .put(new JSONObject().put("name", "bad name;").put("value", "x")));
        EmailRenderResult r = TestSupport.renderer(Map.of("date.DAY-1.yyyy-MM-dd", "2026-09-24", "date.DAY-0.yyyy-MM-dd", "2026-09-25"))
                .withVariables(BOARD).render(templateWith(declares("boardId", "", "boardName", "", "planner", ""), rep));
        assertEquals("the board's id reaches the Repeat's filter; a name given twice is a range",
                List.of("tasks{} params{fn_board_id=[b-7], fn_dateCreated=[2026-09-24, 2026-09-25]}"), fake.calls);
        assertTrue("cards show their row and the board", r.getMjml().contains("<p>Implement WAF on Security &lt;Ops&gt;</p>"));
        assertTrue(r.getWarnings().contains("Repeat filter parameter \"bad name;\" is not a datalist filter name; it was ignored."));
    }

    @Test
    public void invalidVariableNamesAreNotDeclared() {
        Map<String, String> declared = org.joget.etb.render.EmailTemplateRenderer.declaredVariables(declares("ok_1", "a", "1bad", "b", "has space", "c", "", "d"));
        assertEquals("name to description", Map.of("ok_1", ""), declared);
    }

    @Test
    public void theToolsMappingAndSubjectOverride() {
        Object[] grid = {Map.of("name", "boardName", "value", "Security"), Map.of("name", " ", "value", "skipped"), Map.of("name", "boardId", "value", "b-7")};
        Map<String, String> vars = SendTemplatedEmailTool.variables(grid, null, null);
        assertEquals(Map.of("boardName", "Security", "boardId", "b-7"), vars);
        assertEquals("Updates for Security ({var.nope})", SendTemplatedEmailTool.fillVariables("Updates for {var.boardName} ({var.nope})", vars));
        assertEquals("no mapping: as written", "x {var.a}", SendTemplatedEmailTool.fillVariables("x {var.a}", null));
    }

    @Test
    public void aParagraphHoldingOnlyAnHtmlFieldIsUnwrapped() {
        String m = render(Map.of(), repeat("tasks", text("<p>{row.body?html}</p><p style=\"color:red\"> {row.body?html} </p><p>By {row.author}: {row.body?html}</p>"))).getMjml();
        assertFalse("no <p> around the field's own blocks", m.contains("<p><ol>"));
        assertTrue(m.contains("</ol><ol>") || m.replaceAll("\\s", "").contains("</ol><ol>"));
        assertTrue("a paragraph with other text keeps its <p>", m.contains("<p>By Hugo Lim: "));
    }

    @Test
    public void cardPaddingIsASetting() {
        assertTrue("default", render(Map.of(), repeat("tasks", text("<p>x</p>"))).getMjml().contains("border-radius=\"6px\" padding=\"6px 0px\">"));
        JSONObject none = repeat("tasks", text("<p>x</p>"));
        none.getJSONObject("properties").put("cardPadding", "0px");
        assertTrue(render(Map.of(), none).getMjml().contains("border-radius=\"6px\" padding=\"0px\">"));
    }

    @Test
    public void aRepeatCannotGoInAColumn() {
        JSONObject row = el("org.joget.etb.lib.RowBlock", new JSONObject(), el("org.joget.etb.lib.ColumnBlock", new JSONObject(), repeat("tasks", text("<p>x</p>"))));
        EmailRenderResult r = render(Map.of(), row);
        assertTrue(r.getWarnings().contains("Repeat cannot be placed in a column; it was skipped."));
    }

    static JSONObject row(JSONObject... columns) {
        return el("org.joget.etb.lib.RowBlock", new JSONObject(), columns);
    }

    static JSONObject column(JSONObject... blocks) {
        return el("org.joget.etb.lib.ColumnBlock", new JSONObject(), blocks);
    }

    @Test
    public void rowsInsideTheCardKeepTheirColumns() {
        EmailRenderResult r = render(Map.of(), repeat("tasks",
                text("<p><b>{row.title}</b></p>"),
                row(column(text("<p>By {row.author}</p>")), column(el(BUTTON, new JSONObject().put("label", "Open").put("href", "{row.link}")))),
                text("<p>id {row.id}</p>")));
        String m = r.getMjml();
        // per card: inset wrapper > card wrapper > [text section][row section with 2 columns][text section]
        int card = m.indexOf("<mj-wrapper background-color=\"#f8fafc\"");
        int end = m.indexOf("</mj-wrapper>", card);
        String first = m.substring(card, end);
        assertEquals("text, row, text", 3, first.split("<mj-section", -1).length - 1);
        assertEquals("the row's two columns", 4, first.split("<mj-column", -1).length - 1);
        assertTrue(first.contains("<p>By Hugo Lim</p>"));
        assertTrue(first.contains("href=\"https://x.com/t?id=t1\""));
        assertTrue("two columns side by side", r.getHtml().contains("mj-column-per-50"));
        assertTrue("only the second row's javascript: link", r.getWarnings().stream().allMatch(w -> w.startsWith("Blocked link")));
        assertTrue(r.getText().contains("By Hugo Lim"));
    }

    @Test
    public void consecutiveBlocksShareOneSection() {
        String m = render(Map.of(), repeat("tasks", text("<p>a</p>"), text("<p>b</p>"))).getMjml();
        int card = m.indexOf("<mj-wrapper background-color=\"#f8fafc\"");
        String first = m.substring(card, m.indexOf("</mj-wrapper>", card));
        assertEquals(1, first.split("<mj-section", -1).length - 1);
    }

    @Test
    public void heroAndRepeatStillCannotGoInACard() {
        EmailRenderResult r = render(Map.of(), repeat("tasks",
                el("org.joget.etb.lib.HeroBlock", new JSONObject().put("headline", "H")), repeat("tasks", text("<p>x</p>"))));
        assertFalse(r.getMjml().contains("mj-hero"));
        assertTrue(r.getWarnings().contains("Hero cannot be placed in a repeat; it was skipped."));
        assertTrue(r.getWarnings().contains("Repeat cannot be placed in a repeat; it was skipped."));
    }

    @Test
    public void cardsAreInsetByTheSidePadding() {
        String m = render(Map.of(), repeat("tasks", text("<p>x</p>"))).getMjml();
        assertTrue("first card: top padding, then the gap", m.contains("<mj-wrapper padding=\"12px 24px 12px 24px\"><mj-wrapper background-color=\"#f8fafc\""));
        assertTrue("last card: no top padding (the gap is above it), bottom padding below", m.contains("<mj-wrapper padding=\"0px 24px 12px 24px\"><mj-wrapper background-color"));
        String custom = render(Map.of(), el(RepeatBlock.class.getName(), new JSONObject(new RepeatBlock().getDefaultPropertyValues())
                .put("listId", "tasks").put("padding", "20px 16px 8px").put("cardGap", "6px"), text("<p>x</p>"))).getMjml();
        assertTrue(custom.contains("<mj-wrapper padding=\"20px 16px 6px 16px\">"));
        assertTrue(custom.contains("<mj-wrapper padding=\"0px 16px 8px 16px\">"));
    }

    @Test
    public void theBuilderNotesRowsAndFiltersSoAnEmptyRepeatSaysWhy() {
        JSONObject r = repeat("tasks", text("<p>{row.title}</p>"));
        r.getJSONObject("properties").put("filters", new JSONArray().put(new JSONObject().put("field", "board_id").put("value", "{var.boardId}")))
                .put("filterParams", new JSONArray().put(new JSONObject().put("name", "fn_dateCreated").put("value", "2026-09-24"))
                        .put(new JSONObject().put("name", "fn_dateCreated").put("value", "2026-09-25")));
        String json = templateWith(declares("boardId", "the board"), r);
        EmailRenderResult builder = TestSupport.renderer(Map.of()).withTestVariables(Map.of("boardId", "b-17")).render(json);
        assertEquals(1, builder.getNotes().size());
        EmailRenderResult.DataNote n = builder.getNotes().get(0);
        assertEquals("tasks", n.list);
        assertEquals(2, n.found);
        assertEquals(20, n.max);
        assertEquals(List.of("board_id = b-17", "dateCreated: 2026-09-24 \u2192 2026-09-25"), n.values);
        assertFalse("rows found under the limit: nothing to tell", n.needsAttention());
        EmailRenderResult sent = TestSupport.renderer(Map.of()).withVariables(Map.of("boardId", "b-17")).render(json);
        assertTrue("a send has no notes", sent.getNotes().isEmpty());
        assertFalse("and notes are never warnings", sent.getWarnings().toString().contains("2 rows"));
    }

    @Test
    public void anEmptyResultPointsAtTheValues() {
        JSONObject r = repeat("nothing", text("<p>{row.title}</p>"));
        r.getJSONObject("properties").put("filters", new JSONArray().put(new JSONObject().put("field", "board_id").put("value", "")));
        EmailRenderResult builder = TestSupport.renderer(Map.of()).forBuilder().render(template(r));
        assertEquals(1, builder.getNotes().size());
        EmailRenderResult.DataNote n = builder.getNotes().get(0);
        assertTrue(n.needsAttention());
        assertEquals("Repeat (nothing): no rows match. Values: board_id = (empty). Check the Test data or the datalist's filters.", n.sentence());
        EmailRenderResult.DataNote full = new EmailRenderResult.DataNote("Repeat", "Tasks", 20, 20, List.of());
        assertTrue(full.needsAttention());
        assertEquals("Repeat (Tasks): 20 rows, the limit, so there may be more. Raise Maximum rows to include them.", full.sentence());
    }

    @Test
    public void oneRepeatRendersAloneWithRealRowsForTheCanvasStrip() {
        JSONObject r = repeat("tasks", text("<p><b>{row.title}</b></p>"));
        EmailRenderResult strip = TestSupport.renderer(Map.of()).forBuilder().renderBlock(r.toString(), "{}");
        assertTrue(strip.getHtml().contains("<b>Implement WAF</b>"));
        assertFalse(strip.getHtml().contains("<html"));
        assertEquals(1, strip.getNotes().size());
        assertEquals(2, strip.getNotes().get(0).found);
        r.getJSONObject("properties").put(RepeatBlock.CANVAS_ROWS, "1");
        EmailRenderResult one = TestSupport.renderer(Map.of()).forBuilder().renderBlock(r.toString(), "{}");
        assertTrue(one.getHtml().contains("<b>Implement WAF</b>") && !one.getHtml().contains("envVariable"));
        assertEquals("the note still counts every row", 2, one.getNotes().get(0).found);
        assertTrue("a send ignores it", TestSupport.renderer(Map.of()).render(template(r)).getHtml().contains("envVariable"));
    }

    @Test
    public void aMergeValueIsNeverReadAsARowOrVariableField() {
        // a form value that happens to look like a field prints literally: data never chooses what the email shows
        JSONObject r = repeat("tasks", text("<p>[#form.x.note#]</p>"),
                el(BUTTON, new JSONObject().put("label", "Go").put("href", "#form.x.link#")));
        String json = templateWith(declares("boardId", "the board"), text("<p>(#form.x.note#)</p>"), r);
        EmailRenderResult sent = TestSupport.renderer(Map.of("form.x.note", "{var.boardId} {row.author?html}", "form.x.link", "{row.link}"))
                .withVariables(Map.of("boardId", "b-17")).render(json);
        String html = sent.getHtml();
        assertTrue(html, html.contains("({var.boardId} {row.author?html})"));
        assertTrue(html.contains("[{var.boardId} {row.author?html}]"));
        assertFalse("the variable was not printed", html.contains("b-17"));
        assertFalse("nor a row column", html.contains("Hugo Lim"));
        assertFalse("nor a row link", html.contains("x.com/t?id=t1"));
    }

    @Test
    public void anHtmlFieldReadAsPlainTextIsText() {
        // plain(): subject, text part, datalist filter values, Steps and Progress values. ?html gives the text there.
        JSONObject bar = el(ProgressBarsBlock.class.getName(), new JSONObject().put("bars", new JSONArray()
                .put(new JSONObject().put("label", "x").put("value", "{row.body?html}").put("color", ""))));
        EmailRenderResult sent = TestSupport.renderer(Map.of()).render(template(repeat("tasks", bar)));
        String warnings = sent.getWarnings().toString();
        assertTrue(warnings, warnings.contains("is not a number"));
        assertFalse("the value in the warning is text, not markup", warnings.contains("<ol>") || warnings.contains("<li>"));
    }

    @Test
    public void rowsKeptDespiteAProblemStillGetTheirNote() {
        EmailRenderResult builder = TestSupport.renderer(Map.of()).forBuilder().render(template(repeat("partial", text("<p>{row.title}</p>"))));
        assertTrue(builder.getWarnings().toString(), builder.getWarnings().toString().contains("were dropped"));
        assertEquals(1, builder.getNotes().size());
        assertEquals(1, builder.getNotes().get(0).found);
    }
}
