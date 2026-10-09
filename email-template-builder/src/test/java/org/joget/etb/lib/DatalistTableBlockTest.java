package org.joget.etb.lib;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.etb.render.EmailRenderResult;
import org.joget.etb.render.TestSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DatalistTableBlockTest {

    /** Records the last request and answers with two line items. */
    static class FakeSource extends DatalistSource {
        String listId;
        List<String> columns;
        Map<String, String> filters;
        int maxRows;
        boolean fetched;
        CellMode mode;
        Table answer;

        @Override
        public Table read(AppDefinition appDef, String listId, List<String> columns, Map<String, String> filters,
                Map<String, List<String>> params, String sortBy, boolean desc, int maxRows, CellMode mode, boolean fetchRows) {
            this.listId = listId;
            this.columns = columns;
            this.filters = filters;
            this.maxRows = maxRows;
            this.fetched = fetchRows;
            this.mode = mode;
            if (answer != null) {
                return answer;
            }
            Table t = new Table();
            t.names.addAll(List.of("item", "qty"));
            t.headers.add("Item");
            t.headers.add("Qty");
            t.rows.add(List.of("Laptop", "1"));
            t.raw.add(List.of("Laptop", "1"));
            t.rows.add(List.of("Dock &amp; cable", "2"));
            t.raw.add(List.of("Dock & cable", "2"));
            return t;
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

    static String template(JSONObject props) {
        return new JSONObject().put("className", "org.joget.etb.EmailTemplateBuilder").put("properties", new JSONObject())
                .put("elements", new JSONArray().put(new JSONObject().put("className", DatalistTableBlock.class.getName()).put("properties", props))).toString();
    }

    static JSONObject props() {
        return new JSONObject(new DatalistTableBlock().getDefaultPropertyValues()).put("listId", "list_order_items");
    }

    @Test
    public void rendersHeadersAndRowsAsAnEmailTable() {
        EmailRenderResult r = TestSupport.renderer(Map.of()).render(template(props().put("columns", "item;qty")));
        assertEquals("list_order_items", fake.listId);
        assertEquals(List.of("item", "qty"), fake.columns);
        assertTrue(fake.fetched);
        assertEquals(DatalistSource.CellMode.TEXT, fake.mode);
        assertTrue(r.getMjml().contains("<mj-table"));
        assertTrue(r.getMjml().contains(">Item</th>"));
        assertTrue(r.getMjml().contains(">Dock &amp; cable</td>"));
        assertTrue("striped by default", r.getMjml().contains("<tr style=\"background-color:#f8fafc;\">"));
        assertEquals("Item | Qty\nLaptop | 1\nDock & cable | 2", r.getText());
        assertTrue(r.getWarnings().isEmpty());
    }

    @Test
    public void filtersAreMergeResolvedUnescapedAndBadFieldsIgnored() {
        JSONArray filters = new JSONArray()
                .put(new JSONObject().put("field", "order_id").put("value", "#form.orders.id#"))
                .put(new JSONObject().put("field", "x = 1 OR 1").put("value", "y"))
                .put(new JSONObject().put("field", "").put("value", "skipped"));
        EmailRenderResult r = TestSupport.renderer(Map.of("form.orders.id", "A&B-1")).render(template(props().put("filters", filters)));
        assertEquals(Map.of("order_id", "A&B-1"), fake.filters);
        assertTrue(r.getWarnings().contains("Datalist Table filter \"x = 1 OR 1\" is not a field name; it was ignored."));
    }

    @Test
    public void rowsAreCappedAtOneHundred() {
        TestSupport.renderer(Map.of()).render(template(props().put("maxRows", "5000")));
        assertEquals(100, fake.maxRows);
        TestSupport.renderer(Map.of()).render(template(props().put("maxRows", "abc")));
        assertEquals(20, fake.maxRows);
    }

    @Test
    public void canvasShowsHeadersWithoutQuerying() {
        EmailRenderResult r = TestSupport.previewRenderer().render(template(props()));
        assertFalse(fake.fetched);
        assertTrue(r.getMjml().contains(">Item</th>"));
        assertFalse(r.getMjml().contains("Laptop"));
        assertTrue("placeholder rows name the column", r.getMjml().contains("<span style=\"color:#9aa5b1;\">Item</span>"));
    }

    @Test
    public void emptyAndFailingListsExplainThemselves() {
        fake.answer = new DatalistSource.Table();
        fake.answer.headers.add("Task");
        EmailRenderResult empty = TestSupport.renderer(Map.of()).render(template(props().put("emptyText", "No new tasks today")));
        assertTrue(empty.getMjml().contains("No new tasks today"));

        fake.answer = new DatalistSource.Table();
        fake.answer.problem = "Datalist \"list_order_items\" is not available to this process's user, so the table is empty.";
        EmailRenderResult denied = TestSupport.renderer(Map.of()).render(template(props()));
        assertTrue(denied.getWarnings().contains(fake.answer.problem));
        assertFalse(denied.getMjml().contains("mj-table"));
        assertTrue("the canvas says why", TestSupport.previewRenderer().render(template(props())).getMjml().contains("not available to this process"));
    }

    @Test
    public void cellModes() {
        String formatted = "<span class=\"badge\" onclick=\"x()\">Approved</span><script>alert(1)</script><br/>on 25 Sep &amp; later";
        assertEquals("Approved on 25 Sep &amp; later", DatalistSource.cell(DatalistSource.CellMode.TEXT, "raw", formatted));
        String html = DatalistSource.cell(DatalistSource.CellMode.HTML, "raw", formatted);
        assertTrue(html.contains("Approved"));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("onclick"));
        assertEquals("&lt;b&gt;raw&lt;/b&gt;", DatalistSource.cell(DatalistSource.CellMode.RAW, "<b>raw</b>", null));
    }

    @Test
    public void columnsInTheChosenOrderSkippingHiddenAndUnknown() {
        DataListColumn a = new DataListColumn("a", "A", false);
        DataListColumn b = new DataListColumn("b", "B", false);
        DataListColumn hidden = new DataListColumn("h", "H", false);
        hidden.setHidden(true);
        DataListColumn[] all = {a, b, hidden};
        assertEquals(List.of(a, b), DatalistSource.pick(all, new ArrayList<>()));
        assertEquals(List.of(b, a), DatalistSource.pick(all, List.of("b", "zzz", "h", "a")));
    }

    @Test
    public void aTableWithoutADatalistIsLeftOutWithAWarning() {
        EmailRenderResult r = TestSupport.renderer(Map.of()).render(template(props().put("listId", "")));
        assertNull("no query", fake.listId);
        assertFalse(r.getMjml().contains("mj-table"));
        assertTrue(r.getWarnings().contains("Datalist Table has no datalist chosen; it was left out."));
    }

    @Test
    public void sortColumnIsPassedAsTheBinderExpectsIt() {
        assertEquals("regression: not getColumnName's cast(dateModified as string)", "dateModified", DatalistSource.sortColumn(" dateModified "));
        assertNull(DatalistSource.sortColumn("name; DROP TABLE x"));
        assertNull(DatalistSource.sortColumn(""));
    }

    /** Two planner-style updates: title, link, author line, a detail and an HTML body. */
    static DatalistSource.Table updates() {
        DatalistSource.Table t = new DatalistSource.Table();
        t.names.addAll(List.of("title", "link", "author", "status", "body"));
        t.headers.addAll(List.of("Title", "Link", "Author", "Status", "Body"));
        t.rows.add(List.of("Implement WAF", "https://x.com/t/1", "Hugo Lim", "Done", "<ol><li>Start from to 2026-09-18</li></ol>"));
        t.raw.add(List.of("Implement WAF", "https://x.com/t/1", "Hugo Lim", "Done", "<ol><li>Start from to 2026-09-18</li></ol>"));
        t.rows.add(List.of("#envVariable.secret#", "javascript:alert(1)", "Eve", "New", "hi"));
        t.raw.add(List.of("#envVariable.secret#", "javascript:alert(1)", "Eve", "New", "hi"));
        return t;
    }

    static JSONObject cards() {
        return props().put("layout", "cards").put("cardTitle", "title").put("cardLink", "link").put("cardSubtitle", "author")
                .put("cardBody", "body").put("columns", "status");
    }

    @Test
    public void cardLayoutRendersOneCardPerRow() {
        fake.answer = updates();
        EmailRenderResult r = TestSupport.renderer(Map.of()).render(template(cards()));
        assertEquals("card fields first, then the detail columns", List.of("title", "link", "author", "body", "status"), fake.columns);
        String m = r.getMjml();
        assertFalse(m.contains("<mj-table"));
        assertTrue(m.contains("<a href=\"https://x.com/t/1\" style=\"color:inherit;text-decoration:none;\">Implement WAF</a>"));
        assertTrue(m.contains(">Hugo Lim</div>"));
        assertTrue(m.contains("<span style=\"color:#6b7280;\">Status:</span> Done"));
        assertTrue(m.contains("<ol><li>Start from to 2026-09-18</li></ol>"));
        assertEquals(2, m.split("<table role=\"presentation\"", -1).length - 1);
        assertTrue(r.getText().startsWith("Implement WAF (https://x.com/t/1)\nHugo Lim\nStatus: Done"));
    }

    @Test
    public void rowValuesAreNeverMergeResolvedAndBadLinksAreDropped() {
        fake.answer = updates();
        EmailRenderResult r = TestSupport.renderer(Map.of("envVariable.secret", "TOP-SECRET")).render(template(cards()));
        assertFalse("a record's #...# text must not be resolved", r.getMjml().contains("TOP-SECRET"));
        assertFalse(r.getText().contains("TOP-SECRET"));
        assertTrue(r.getMjml().contains("#envVariable.secret#"));
        assertFalse(r.getMjml().contains("javascript:"));
    }

    @Test
    public void cardsWithoutFieldsShowEveryColumnAsADetail() {
        EmailRenderResult r = TestSupport.renderer(Map.of()).render(template(props().put("layout", "cards")));
        assertTrue(r.getMjml().contains("<span style=\"color:#6b7280;\">Item:</span> Laptop"));
        assertTrue(r.getMjml().contains("<span style=\"color:#6b7280;\">Qty:</span> 2"));
    }

    @Test
    public void emptyCardsShowTheEmptyText() {
        fake.answer = new DatalistSource.Table();
        fake.answer.names.add("title");
        fake.answer.headers.add("Title");
        EmailRenderResult r = TestSupport.renderer(Map.of()).render(template(cards().put("emptyText", "No updates today")));
        assertTrue(r.getMjml().contains("No updates today"));
        assertEquals("No updates today", r.getText());
    }

    @Test
    public void rowsThatIgnoreTheFilterAreDropped() {
        Map<String, String> byOrder = Map.of("order_id", "ORD-1");
        assertTrue(DatalistSource.matches(new java.util.HashMap<>(Map.of("order_id", "ord-1 ", "qty", "2")), byOrder));
        assertFalse("a data source that ignored the filter", DatalistSource.matches(Map.of("order_id", "ORD-2"), byOrder));
        assertFalse("the field is missing from the row", DatalistSource.matches(Map.of("qty", "2"), byOrder));
        assertTrue("numbers by value", DatalistSource.matches(Map.of("qty", "2.0"), Map.of("qty", "2")));
        assertTrue("no filters", DatalistSource.matches(Map.of(), Map.of()));
    }
}
