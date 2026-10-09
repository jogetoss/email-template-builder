package org.joget.etb.render;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The builder's "Test data": fills {@code #form.table.column#} merge fields from one form record, by rewriting
 * each to Joget's explicit-record syntax {@code #form.table.column[RECORD_ID]#} (a {@code ?format} suffix is kept).
 * Merge fields that already name a record are left alone. Builder only; real sends take the record from the process.
 */
public final class RecordResolver implements MergeResolver {

    /** Record IDs that are safe inside a hash variable: UUIDs and ID-generator values such as REQ-0001 or 2026/001. */
    static final Pattern RECORD_ID = Pattern.compile("[A-Za-z0-9_\\-.:@/]{1,255}");
    /** #form.TABLE.COLUMN# or #form.TABLE.COLUMN?format#; Joget only accepts [A-Za-z0-9_] in table and column names. */
    static final Pattern FORM_FIELD = Pattern.compile("#form\\.([A-Za-z0-9_]+\\.[A-Za-z0-9_]+)(\\?[^#\"\\s\\[\\]]*)?#");

    private final MergeResolver delegate;
    private final String recordId;

    public RecordResolver(MergeResolver delegate, String recordId) {
        this.delegate = delegate;
        this.recordId = recordId;
    }

    public static boolean isValidRecordId(String recordId) {
        return recordId != null && RECORD_ID.matcher(recordId).matches();
    }

    /** The delegate itself when there is no (valid) test record. */
    public static MergeResolver of(MergeResolver delegate, String recordId) {
        return isValidRecordId(recordId) ? new RecordResolver(delegate, recordId) : delegate;
    }

    public String resolve(String text, String escapeFormat) {
        return delegate.resolve(withRecord(text), escapeFormat);
    }

    public String resolveTrusted(String text) {
        return delegate.resolveTrusted(withRecord(text));
    }

    String withRecord(String text) {
        if (text == null || !text.contains("#form.")) {
            return text;
        }
        Matcher m = FORM_FIELD.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String format = m.group(2) == null ? "" : m.group(2);
            m.appendReplacement(out, Matcher.quoteReplacement("#form." + m.group(1) + "[" + recordId + "]" + format + "#"));
        }
        m.appendTail(out);
        return out.toString();
    }
}
