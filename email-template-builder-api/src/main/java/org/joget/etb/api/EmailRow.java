package org.joget.etb.api;

/**
 * One data row for a repeating block, e.g. a datalist record. Blocks inside the repeat write
 * {@code {row.NAME}} (formatted text), {@code {row.NAME?html}} (formatted HTML, sanitised) or
 * {@code {row.NAME?raw}} (the stored value) in their properties; the render context substitutes them after
 * merge fields are resolved, so row data is never itself treated as merge fields.
 */
public interface EmailRow {

    /** The formatted value as plain text (not escaped), or null if the row has no such field. */
    String text(String name);

    /** The formatted value as sanitised HTML, or null if the row has no such field. */
    String html(String name);

    /** The stored value (not escaped), or null if the row has no such field. */
    String raw(String name);
}
