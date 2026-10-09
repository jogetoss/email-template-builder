package org.joget.etb.render;

import java.io.File;

/**
 * App Resource images ({@code #appResource.NAME#}) for the renderer: where the file is, and whether to link
 * to it (the builder's canvas and Preview) or embed it as {@code cid:} (a sent email).
 */
public interface ResourceImages {

    /** The file of this app's resource, or null if the app has no such resource. */
    File file(String name);

    /** A browser URL for the builder, or null to embed the file (sending). */
    String link(String name);

    /** Whether anyone may load the resource without logging in (needed for images linked from an email). */
    boolean isPublic(String name);

    /** The resource's path under the server address, e.g. "/web/app/APP_ID/resources/logo.png" (URL-encoded name). */
    String path(String name);
}
