package org.joget.etb.render;

/** A template that cannot be rendered. The message names the failing block. */
public class EmailRenderException extends RuntimeException {

    public EmailRenderException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmailRenderException(String message) {
        super(message);
    }
}
