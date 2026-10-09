package org.joget.etb.api;

import org.joget.apps.app.model.AppDefinition;
import org.joget.workflow.model.WorkflowAssignment;

/**
 * Per-block rendering context. It owns merge-field resolution so every block follows the same rules:
 * <ol>
 * <li>merge fields are resolved per property value, before the MJML compile (never over compiled HTML);</li>
 * <li>substituted values are HTML-escaped; the author's static text is trusted as-is.</li>
 * </ol>
 * In preview mode (the builder canvas) merge fields are left unresolved.
 */
public interface EmailRenderContext {

    /** Author HTML with merge fields resolved; values HTML-escaped. */
    String html(String authorText);

    /**
     * An href/src: merge fields resolved (values HTML-escaped), quotes escaped for the attribute, and
     * the result checked against the scheme allowlist (http, https, mailto, tel, cid). A disallowed
     * URL becomes "#" and a warning is recorded.
     */
    String url(String authorUrl);

    /**
     * An image source: like {@link #url(String)}, and also accepts an App Resource picked with the
     * property editor's "image" field ({@code #appResource.logo.png#}). A sent email embeds that file as an
     * inline attachment ({@code cid:}); the builder links to it. Returns "" (with a warning) when the app
     * has no such resource.
     */
    default String image(String authorValue) {
        return url(authorValue);
    }

    /**
     * A background image, which email apps load by URL only (Gmail and others ignore cid: outside img tags).
     * Like {@link #url(String)}; an App Resource becomes an absolute URL under the template's public server
     * address, with a warning when that address is missing or the file is not public. Returns "" when unusable.
     */
    default String publicImage(String authorValue) {
        return url(authorValue);
    }

    /**
     * Author HTML with merge fields resolved but values NOT HTML-escaped, for hash variables that return
     * app-generated HTML (a digest, a change log). Joget's relaxed sanitiser still removes scripts and
     * event handlers from each value (unless the variable itself uses ?noescape), but links and images
     * pass. Never use for raw form input.
     */
    String trustedHtml(String authorText);

    /** Merge fields resolved without escaping, for the subject and the text/plain part only. */
    String plain(String authorText);

    /** Plain text of an HTML fragment; links become "label (url)". */
    String text(String html);

    /** Concatenated MJML of this block's children. */
    String children();

    /** Concatenated plain text of this block's children. */
    String childrenText();

    /** This block's children rendered for one data row ({@code {row.NAME}} fields filled). For repeating blocks. */
    default String children(EmailRow row) {
        return children();
    }

    /**
     * This block's children for one data row, as section-level MJML: rows as they are, runs of other blocks
     * wrapped in full-width sections. For containers that hold rows (a repeat's card).
     */
    default String childSections(EmailRow row) {
        return "<mj-section padding=\"0px\"><mj-column>" + children(row) + "</mj-column></mj-section>";
    }

    /** Plain text of this block's children for one data row. */
    default String childrenText(EmailRow row) {
        return childrenText();
    }

    /**
     * A colour of the template's theme, so a block left on its default colour follows the template: "link" (the
     * accent), "text" or "content" (the card background). A validated hex colour, or null when unknown.
     */
    default String themeColor(String name) {
        return null;
    }

    boolean isPreview();

    /**
     * True in the builder: the canvas, Preview, Send test, the size meter and gallery thumbnails. False when
     * sending. Builder-only aids (e.g. sample rows while no datalist is chosen) must never reach a real email.
     */
    default boolean isBuilder() {
        return isPreview();
    }

    WorkflowAssignment getAssignment();

    AppDefinition getAppDef();

    void warn(String message);

    /**
     * Builder-only: what a data block (Repeat, Datalist Table) found, so the canvas can show it and an empty result
     * can say why. Ignored when sending. Call it only when {@link #isBuilder()} and not {@link #isPreview()}.
     *
     * @param values the filter and filter-parameter values as they were used, e.g. "board_id = b-17",
     *               "dateCreated: 2026-09-24 \u2192 2026-09-25"
     */
    default void dataNote(String blockLabel, String listName, int found, int max, java.util.List<String> values) {
    }
}
