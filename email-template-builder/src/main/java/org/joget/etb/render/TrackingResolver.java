package org.joget.etb.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves like its delegate, and remembers each merge field that came out empty (or unresolved), so the
 * builder's Preview and Send test can list them. Each distinct field is resolved once more on its own, so
 * this is for the builder only, not for real sends.
 */
public final class TrackingResolver implements MergeResolver {

    static final Pattern HASH = Pattern.compile("#([a-zA-Z]+\\.[^#\\s\"]+?)#");

    private final MergeResolver delegate;
    private final Set<String> checked = new HashSet<>();
    private final Set<String> empty = new LinkedHashSet<>();

    public TrackingResolver(MergeResolver delegate) {
        this.delegate = delegate;
    }

    public String resolve(String text, String escapeFormat) {
        track(text);
        return delegate.resolve(text, escapeFormat);
    }

    public String resolveTrusted(String text) {
        track(text);
        return delegate.resolveTrusted(text);
    }

    private void track(String text) {
        if (text != null) {
            Matcher m = HASH.matcher(text);
            while (m.find()) {
                String token = m.group();
                if (checked.add(token)) {
                    String value = delegate.resolve(token, null);
                    if (value == null || value.trim().isEmpty() || value.equals(token)) {
                        empty.add(token);
                    }
                }
            }
        }
    }

    /** Merge fields that came out empty, in the order they first appear. */
    public List<String> getEmpty() {
        return new ArrayList<>(empty);
    }

    /** The warning for the builder, or null when every merge field had a value. */
    public String warning() {
        if (empty.isEmpty()) {
            return null;
        }
        return (empty.size() == 1 ? "1 merge field came out empty: " : empty.size() + " merge fields came out empty: ")
                + String.join(", ", empty) + ". Set a test record (Test data) or check the field names.";
    }
}
