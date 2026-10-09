package org.joget.etb.render;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The output of rendering a template: everything a sender needs for a multipart/alternative email. */
public final class EmailRenderResult {

    private final String subject;
    private final String html;
    private final String text;
    private final String mjml;
    private final int sizeBytes;
    private final List<String> warnings;
    private final Map<String, File> inlineImages;
    private final List<DataNote> notes;

    public EmailRenderResult(String subject, String html, String text, String mjml, int sizeBytes, List<String> warnings) {
        this(subject, html, text, mjml, sizeBytes, warnings, Collections.<String, File>emptyMap());
    }

    public EmailRenderResult(String subject, String html, String text, String mjml, int sizeBytes, List<String> warnings, Map<String, File> inlineImages) {
        this(subject, html, text, mjml, sizeBytes, warnings, inlineImages, Collections.<DataNote>emptyList());
    }

    private EmailRenderResult(String subject, String html, String text, String mjml, int sizeBytes, List<String> warnings, Map<String, File> inlineImages,
            List<DataNote> notes) {
        this.notes = notes == null ? Collections.<DataNote>emptyList() : Collections.unmodifiableList(new java.util.ArrayList<>(notes));
        this.subject = subject;
        this.html = html;
        this.text = text;
        this.mjml = mjml;
        this.sizeBytes = sizeBytes;
        this.warnings = Collections.unmodifiableList(warnings);
        this.inlineImages = Collections.unmodifiableMap(inlineImages);
    }

    public String getSubject() {
        return subject;
    }

    public String getHtml() {
        return html;
    }

    public String getText() {
        return text;
    }

    /** The MJML that was compiled (for golden tests and debugging). */
    public String getMjml() {
        return mjml;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** App Resource images the HTML references as cid:KEY; a sender must embed each file under its key. */
    public Map<String, File> getInlineImages() {
        return inlineImages;
    }

    /** What a data block found in the builder ({@link org.joget.etb.api.EmailRenderContext#dataNote}). */
    public static final class DataNote {
        public final String block;
        public final String list;
        public final int found;
        public final int max;
        public final List<String> values;

        public DataNote(String block, String list, int found, int max, List<String> values) {
            this.block = block;
            this.list = list;
            this.found = found;
            this.max = max;
            this.values = values == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new java.util.ArrayList<>(values));
        }

        /** It found nothing, or as many rows as it may show: worth telling the author. */
        public boolean needsAttention() {
            return found == 0 || found >= max;
        }

        /** One line for Preview, e.g. "Repeat (Tasks): no rows match. Values: board_id = b-17. Check the Test data ..." */
        public String sentence() {
            String head = block + " (" + list + "): ";
            if (found == 0) {
                return head + "no rows match." + (values.isEmpty() ? "" : " Values: " + String.join(", ", values) + ".")
                        + " Check the Test data or the datalist's filters.";
            }
            return head + found + " rows, the limit, so there may be more. Raise Maximum rows to include them.";
        }
    }

    /** What the data blocks found (builder renders only). Preview shows the ones that need attention. */
    public List<DataNote> getNotes() {
        return notes;
    }

    /** A copy with these notes (like {@link #withWarning}, the result itself never changes). */
    public EmailRenderResult withNotes(List<DataNote> notes) {
        return new EmailRenderResult(subject, html, text, mjml, sizeBytes, warnings, inlineImages, notes);
    }

    /** A copy with one more warning (null adds nothing). */
    public EmailRenderResult withWarning(String warning) {
        if (warning == null) {
            return this;
        }
        List<String> all = new java.util.ArrayList<>(warnings);
        all.add(warning);
        return new EmailRenderResult(subject, html, text, mjml, sizeBytes, all, inlineImages, notes);
    }
}
