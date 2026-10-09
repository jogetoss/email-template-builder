package org.joget.etb.render;

import ch.digitalfondue.jfiveparse.Element;
import ch.digitalfondue.jfiveparse.Node;
import ch.digitalfondue.jfiveparse.Parser;
import ch.digitalfondue.jfiveparse.Text;
import java.util.List;
import java.util.Set;

/**
 * HTML fragment to plain text for the text/plain part: block elements become line breaks, links
 * become "label (url)", entities are decoded by the HTML5 parser.
 */
public final class HtmlToText {

    static final Set<String> BLOCKS = Set.of("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "tr", "blockquote", "pre");

    private HtmlToText() {
    }

    public static String convert(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        List<Node> nodes = new Parser().parseFragment(new Element("div"), html);
        for (Node n : nodes) {
            walk(n, sb);
        }
        return normalize(sb.toString());
    }

    static void walk(Node node, StringBuilder sb) {
        if (node instanceof Text) {
            sb.append(((Text) node).getData().replaceAll("\\s+", " "));
            return;
        }
        if (!(node instanceof Element)) {
            return;
        }
        Element el = (Element) node;
        String name = el.getNodeName().toLowerCase();
        switch (name) {
            case "br":
                sb.append('\n');
                return;
            case "script":
            case "style":
            case "head":
                return;
            case "img":
                String alt = el.getAttributeValue("alt");
                if (alt != null && !alt.isBlank()) {
                    sb.append(alt);
                }
                return;
            case "li":
                sb.append("\n- ");
                children(el, sb);
                return;
            case "td":
            case "th":
                children(el, sb);
                sb.append(' ');
                return;
            case "a":
                int start = sb.length();
                children(el, sb);
                String label = sb.substring(start).trim();
                String href = el.getAttributeValue("href");
                if (href != null && !href.isBlank() && !href.startsWith("#") && !href.equals(label)
                        && !href.equals("mailto:" + label)) {
                    sb.append(" (").append(href).append(')');
                }
                return;
            default:
                if (BLOCKS.contains(name)) {
                    sb.append("\n\n");
                    children(el, sb);
                    sb.append("\n\n");
                } else {
                    children(el, sb);
                }
        }
    }

    static void children(Element el, StringBuilder sb) {
        for (Node child : el.getChildNodes()) {
            walk(child, sb);
        }
    }

    /** Trim each line, drop leading/trailing blank lines, collapse runs of blank lines to one. */
    public static String normalize(String text) {
        StringBuilder out = new StringBuilder();
        int blank = 0;
        for (String line : text.split("\n", -1)) {
            String t = line.replaceAll("[ \\t\\u00a0]+", " ").trim();
            if (t.isEmpty()) {
                blank++;
                continue;
            }
            if (out.length() > 0) {
                out.append(blank > 0 ? "\n\n" : "\n");
            }
            out.append(t);
            blank = 0;
        }
        return out.toString();
    }
}
