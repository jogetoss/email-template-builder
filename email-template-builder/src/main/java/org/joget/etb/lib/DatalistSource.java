package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListBinder;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.service.DataListDecorator;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.StringUtil;
import org.joget.etb.api.EmailRow;

/**
 * Reads a datalist for the Datalist Table block, as the current thread's user (a process's performer; nobody
 * in a scheduled run), so the datalist's own permission rules apply and access is never widened.
 * Tests replace {@link #instance} with a fake.
 */
public class DatalistSource {

    /** What a cell shows. */
    public enum CellMode {
        /** the column formatters' output as plain text (dates, labels); markup and links dropped */
        TEXT,
        /** the formatters' HTML with scripts and event handlers removed */
        HTML,
        /** the stored value, no formatters */
        RAW;

        static CellMode of(String value) {
            return "html".equals(value) ? HTML : "raw".equals(value) ? RAW : TEXT;
        }
    }

    /** Headers and rows; every cell is HTML-safe for the chosen mode. {@code problem} explains an empty result. */
    public static final class Table {
        /** column names, in the order of headers and cells */
        public final List<String> names = new ArrayList<>();
        public final List<String> headers = new ArrayList<>();
        public final List<List<String>> rows = new ArrayList<>();
        /** the stored values, unformatted and unescaped (for links); same shape as rows */
        public final List<List<String>> raw = new ArrayList<>();
        public String problem;
        /** the datalist's name, once it is found */
        public String listName;
        /** each row as {row.NAME} fields, for the Repeat block */
        public final List<EmailRow> records = new ArrayList<>();

        /** Index of a column name, or -1. */
        public int column(String name) {
            return name == null ? -1 : names.indexOf(name);
        }
    }

    /**
     * A block's sample rows (starter templates), shown in the builder until a datalist is chosen:
     * {"columns":[{"name":"item","label":"Item"}, ...], "rows":[{"item":"Laptop stand", ...}, ...]}. Values are
     * plain text (escaped here); in cards and Repeat fields they behave like a datalist's stored values.
     */
    public static Table sample(String json) {
        Table t = new Table();
        if (json == null || json.trim().isEmpty()) {
            return t;
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            org.json.JSONArray cols = o.optJSONArray("columns");
            org.json.JSONArray rows = o.optJSONArray("rows");
            for (int i = 0; cols != null && i < cols.length(); i++) {
                org.json.JSONObject c = cols.getJSONObject(i);
                String name = c.optString("name", "");
                if (FIELD.matcher(name).matches()) {
                    t.names.add(name);
                    t.headers.add(StringUtil.escapeString(c.optString("label", name), StringUtil.TYPE_HTML, null));
                }
            }
            for (int r = 0; rows != null && r < rows.length(); r++) {
                org.json.JSONObject row = rows.getJSONObject(r);
                List<String> cells = new ArrayList<>();
                List<String> raw = new ArrayList<>();
                Map<String, Object> values = new LinkedHashMap<>();
                for (String key : row.keySet()) {
                    values.put(key, row.optString(key, ""));
                }
                for (String name : t.names) {
                    String v = row.optString(name, "");
                    cells.add(StringUtil.escapeString(v, StringUtil.TYPE_HTML, null));
                    raw.add(v);
                }
                t.rows.add(cells);
                t.raw.add(raw);
                t.records.add(new org.joget.etb.render.MapRow(values));
            }
        } catch (RuntimeException e) {
            // not valid sample JSON: no sample
        }
        return t;
    }

    /** A datalist filter parameter: fn_ and the filter's name, e.g. fn_dateCreated. */
    static final Pattern PARAM = Pattern.compile("fn_[A-Za-z0-9_]{1,64}");

    /**
     * The "Datalist filter parameters" grid (name, value) as fn_NAME to values. A name may be written as NAME,
     * fn_NAME or as copied from a datalist URL, d-4040985-fn_NAME. A name given twice passes two values (a date
     * range's from and to). Values are merge-resolved without escaping; they are the filters' input.
     */
    public static Map<String, List<String>> params(Object grid, org.joget.etb.api.EmailRenderContext ctx, String blockLabel) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (!(grid instanceof Object[])) {
            return out;
        }
        for (Object row : (Object[]) grid) {
            if (!(row instanceof Map)) {
                continue;
            }
            Object n = ((Map<?, ?>) row).get("name");
            Object v = ((Map<?, ?>) row).get("value");
            String name = n == null ? "" : n.toString().trim();
            if (name.isEmpty()) {
                continue;
            }
            int fn = name.indexOf("fn_");
            name = fn >= 0 ? name.substring(fn) : "fn_" + name;
            if (!PARAM.matcher(name).matches()) {
                ctx.warn(blockLabel + " filter parameter \"" + n + "\" is not a datalist filter name; it was ignored.");
                continue;
            }
            out.computeIfAbsent(name, k -> new ArrayList<>()).add(ctx.plain(v == null ? "" : v.toString()));
        }
        return out;
    }

    /**
     * The builder note of a data block: which datalist, how many rows, and the values its filters and filter
     * parameters were run with (Test data included), readable: "board_id = b-17", "dateCreated: 2026-09-24 \u2192
     * 2026-09-25" (a from/to pair), "(empty)" for an empty value. So an empty result can say why.
     */
    static void note(org.joget.etb.api.EmailRenderContext ctx, String blockLabel, String listId, Table t, Map<String, String> filters,
            Map<String, List<String>> params, int max) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, String> e : filters.entrySet()) {
            values.add(e.getKey() + " = " + shown(e.getValue()));
        }
        if (params != null) {
            for (Map.Entry<String, List<String>> e : params.entrySet()) {
                String name = e.getKey().startsWith("fn_") ? e.getKey().substring(3) : e.getKey();
                List<String> v = e.getValue();
                values.add(v.size() == 2 ? name + ": " + shown(v.get(0)) + " \u2192 " + shown(v.get(1))
                        : name + " = " + String.join(", ", v.stream().map(DatalistSource::shown).toArray(String[]::new)));
            }
        }
        int n = t.records.isEmpty() ? t.rows.size() : t.records.size();
        ctx.dataNote(blockLabel, t.listName == null || t.listName.isEmpty() ? listId : t.listName, n, max, values);
    }

    private static String shown(String value) {
        return value == null || value.isEmpty() ? "(empty)" : value;
    }

    /** A filter field: a binder column name, e.g. order_id or id. */
    static final Pattern FIELD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    static DatalistSource instance = new DatalistSource();

    /**
     * @param columns   column names to show, in order; empty for all visible columns
     * @param filters   field to value (already merge-resolved), each applied as {@code field = ?}
     * @param params    the datalist's own filter parameters (fn_NAME to values), as its page URL would pass them
     * @param fetchRows false for the builder canvas: headers only, no query
     */
    public Table read(AppDefinition appDef, String listId, List<String> columns, Map<String, String> filters,
            Map<String, List<String>> params, String sortBy, boolean desc, int maxRows, CellMode mode, boolean fetchRows) {
        Table t = new Table();
        DatalistDefinitionDao dao = (DatalistDefinitionDao) AppUtil.getApplicationContext().getBean("datalistDefinitionDao");
        DatalistDefinition def = appDef == null || listId == null || listId.isEmpty() ? null : dao.loadById(listId, appDef);
        if (def == null) {
            t.problem = "Datalist \"" + listId + "\" was not found in this app.";
            return t;
        }
        DataListService service = (DataListService) AppUtil.getApplicationContext().getBean("dataListService");
        DataList list = service.fromJson(def.getJson());
        if (list == null) {
            t.problem = "Datalist \"" + listId + "\" could not be loaded.";
            return t;
        }
        list.setId(def.getId());
        // a datalist with "Use session" would fill any parameter missing here (its own filters, sort, page) from the
        // user's HTTP session, and write ours into it: the builder (admin's session) would then query differently from
        // a send (no session), and the admin's list page would pick up the email's filters
        list.setUseSession(false);
        t.listName = def.getName();
        // the datalist's parameters come only from here (never from the builder's own request): its own filters
        // (date range, select, contains, ...) read their values as from its page URL, d-XXXX-fn_NAME=value
        Map<String, String[]> encoded = new java.util.HashMap<>();
        if (params != null) {
            for (Map.Entry<String, List<String>> e : params.entrySet()) {
                encoded.put(list.getDataListEncodedParamName(e.getKey()), e.getValue().toArray(new String[0]));
            }
        }
        list.setRequestParamMap(encoded);
        if (!list.isIsAuthorized()) {
            t.problem = "Datalist \"" + listId + "\" is not available to this process's user, so the table is empty.";
            return t;
        }

        List<DataListColumn> shown = pick(list.getColumns(), columns);
        for (DataListColumn c : shown) {
            // a column action builds its link from the HTTP request, which a process doesn't have; links are dropped anyway
            c.setAction(null);
            t.names.add(c.getName());
            t.headers.add(StringUtil.escapeString(c.getLabel() == null || c.getLabel().isEmpty() ? c.getName() : c.getLabel(), StringUtil.TYPE_HTML, null));
        }
        if (!fetchRows) {
            return t;
        }

        DataListBinder binder = list.getBinder();
        if (binder == null) {
            t.problem = "Datalist \"" + listId + "\" has no data source (binder).";
            return t;
        }
        for (Map.Entry<String, String> f : filters.entrySet()) {
            DataListFilterQueryObject q = new DataListFilterQueryObject();
            q.setOperator("AND");
            q.setQuery(binder.getColumnName(f.getKey()) + " = ?");
            q.setValues(new String[]{f.getValue()});
            list.addFilterQueryObject(q);
        }
        // the plain column name, as DataList.getRows passes it: binders map it themselves (FormRowDataListBinder uses
        // getSortColumnName; getColumnName is for filters and would turn dateModified into an invalid ORDER BY)
        String sortColumn = sortColumn(sortBy);
        DataListCollection data = sortColumn == null
                ? list.getRows(maxRows, 0)
                : binder.getData(list, binder.getProperties(), list.getFilterQueryObjects(), sortColumn, desc, 0, maxRows);
        if (data == null) {
            return t;
        }
        DataListDecorator decorator = new DataListDecorator(list);
        int unmatched = 0;
        for (Object row : (Collection<?>) data) {
            if (!matches(row, filters)) {
                unmatched++;
                continue;
            }
            List<String> cells = new ArrayList<>();
            List<String> raw = new ArrayList<>();
            Record record = new Record(row);
            for (DataListColumn c : shown) {
                Object value = DataListService.evaluateColumnValueFromRow(row, c.getName());
                String formatted = decorator.formatColumn(c, row, value);
                cells.add(cell(mode, value, mode == CellMode.RAW ? null : formatted));
                raw.add(value == null ? "" : value.toString());
                record.put(c.getName(), formatted, value);
            }
            t.rows.add(cells);
            t.raw.add(raw);
            t.records.add(record);
        }
        if (unmatched > 0) {
            t.problem = "Datalist \"" + listId + "\" returned " + unmatched + " row(s) that don't match the filters (its data source may not apply"
                    + " filters, e.g. a JSON API list without \"handle filters\"); they were left out.";
        }
        return t;
    }

    /**
     * Whether the row really has each filter's value. Some data sources ignore filters (a JSON API list without
     * "handle filters", a BeanShell script that doesn't read them); without this check a line-items email would
     * list every order. Case-insensitive, like SQL's usual collation; numbers compare by value.
     */
    static boolean matches(Object row, Map<String, String> filters) {
        for (Map.Entry<String, String> f : filters.entrySet()) {
            Object v = row instanceof Map ? ((Map<?, ?>) row).get(f.getKey()) : DataListService.evaluateColumnValueFromRow(row, f.getKey());
            String actual = v == null ? null : v.toString().trim();
            String wanted = f.getValue() == null ? "" : f.getValue().trim();
            if (actual == null || !(actual.equalsIgnoreCase(wanted) || sameNumber(actual, wanted))) {
                return false;
            }
        }
        return true;
    }

    static boolean sameNumber(String a, String b) {
        try {
            return new java.math.BigDecimal(a).compareTo(new java.math.BigDecimal(b)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * A fetched row as {row.NAME} fields: a column gives its formatted value (as text or sanitised HTML) and its
     * stored value; any other field of the row (e.g. id) gives its stored value.
     */
    static final class Record implements EmailRow {
        private final Object row;
        private final Map<String, String[]> columns = new java.util.HashMap<>();

        Record(Object row) {
            this.row = row;
        }

        void put(String name, String formatted, Object value) {
            columns.put(name, new String[]{formatted, value == null ? "" : value.toString()});
        }

        public String text(String name) {
            String[] c = columns.get(name);
            return c == null ? raw(name) : textOf(c[0]);
        }

        public String html(String name) {
            String[] c = columns.get(name);
            if (c == null) {
                String v = raw(name);
                return v == null ? null : StringUtil.escapeString(v, StringUtil.TYPE_HTML, null);
            }
            return c[0] == null ? "" : StringUtil.stripHtmlRelaxed(c[0]);
        }

        public String raw(String name) {
            String[] c = columns.get(name);
            if (c != null) {
                return c[1];
            }
            if (!FIELD.matcher(name).matches()) {
                return null;
            }
            Object v = row instanceof Map ? ((Map<?, ?>) row).get(name) : DataListService.evaluateColumnValueFromRow(row, name);
            if (v == null && !(row instanceof Map && ((Map<?, ?>) row).containsKey(name))) {
                return null;
            }
            return v == null ? "" : v.toString();
        }
    }

    /** A formatter's output as plain text (not escaped): markup dropped, line breaks as spaces, entities decoded. */
    static String textOf(String formatted) {
        if (formatted == null) {
            return "";
        }
        return StringUtil.unescapeString(StringUtil.stripAllHtmlTag(formatted.replaceAll("(?i)<br\\s*/?>", " "), false), StringUtil.TYPE_HTML, null).trim();
    }

    static String sortColumn(String sortBy) {
        return sortBy != null && FIELD.matcher(sortBy.trim()).matches() ? sortBy.trim() : null;
    }

    static String cell(CellMode mode, Object raw, String formatted) {
        if (mode == CellMode.RAW) {
            return raw == null ? "" : StringUtil.escapeString(raw.toString(), StringUtil.TYPE_HTML, null);
        }
        if (formatted == null) {
            return "";
        }
        if (mode == CellMode.HTML) {
            return StringUtil.stripHtmlRelaxed(formatted);
        }
        // Joget's own unescape (in textOf): commons-lang is not exported to plugins
        return StringUtil.escapeString(textOf(formatted), StringUtil.TYPE_HTML, null);
    }

    /** The named columns in the given order (unknown names skipped), or every visible column. */
    static List<DataListColumn> pick(DataListColumn[] all, List<String> names) {
        Map<String, DataListColumn> byName = new LinkedHashMap<>();
        if (all != null) {
            for (DataListColumn c : all) {
                if (c != null && !c.isHidden()) {
                    byName.put(c.getName(), c);
                }
            }
        }
        if (names == null || names.isEmpty()) {
            return new ArrayList<>(byName.values());
        }
        List<DataListColumn> out = new ArrayList<>();
        for (String n : names) {
            if (byName.containsKey(n)) {
                out.add(byName.get(n));
            }
        }
        return out;
    }
}
