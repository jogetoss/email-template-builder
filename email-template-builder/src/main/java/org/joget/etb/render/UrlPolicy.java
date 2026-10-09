package org.joget.etb.render;

import java.util.regex.Pattern;

/** Scheme allowlist for href/src values in emails. */
public final class UrlPolicy {

    static final Pattern ALLOWED = Pattern.compile("^(https?:|mailto:|tel:|cid:).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** A link to a place in the same email (#top). Not a leftover merge field such as #form.x.link#, which ends in "#". */
    static final Pattern FRAGMENT = Pattern.compile("#[A-Za-z][A-Za-z0-9_:.-]{0,63}");
    /** Control characters and whitespace, which browsers strip when parsing a scheme ("java\tscript:"). */
    static final Pattern IGNORED = Pattern.compile("[\\x00-\\x20\\x7f]");

    private UrlPolicy() {
    }

    public static boolean isAllowed(String url) {
        if (url == null) {
            return false;
        }
        String compact = IGNORED.matcher(url).replaceAll("");
        return ALLOWED.matcher(compact).matches() || FRAGMENT.matcher(compact).matches();
    }
}
