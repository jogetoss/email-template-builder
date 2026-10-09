package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joget.etb.api.EmailRenderContext;

/**
 * Navbar (mj-navbar): a row of text links, e.g. under the logo. Links show inline in every client. MJML's
 * hamburger menu is not offered: it only works in Apple Mail.
 */
public class NavbarBlock extends AbstractBlock {

    private List<String[]> links;

    public String getName() {
        return "Email Navbar Block";
    }

    public String getLabel() {
        return "Navbar";
    }

    public String getIcon() {
        return "<i class=\"fas fa-bars\"></i>";
    }

    protected String getPropertyResource() {
        return "NavbarBlock";
    }

    public String getDefaultPropertyValues() {
        return "{\"links\":[{\"label\":\"Home\",\"href\":\"https://\"},{\"label\":\"Services\",\"href\":\"https://\"},{\"label\":\"Contact\",\"href\":\"https://\"}],"
                + "\"align\":\"center\",\"color\":\"#333333\",\"fontSize\":\"14px\",\"linkPadding\":\"8px 12px\",\"padding\":\"8px 32px\"}";
    }

    public String toMjml(EmailRenderContext ctx) {
        resolve(ctx);
        if (links.isEmpty()) {
            if (!ctx.isPreview()) {
                ctx.warn("Navbar has no links; it was left out.");
                return "";
            }
            return "<mj-text align=\"center\" color=\"#7b8794\" font-size=\"13px\" padding=\"" + prop("padding", PADDING, "8px 32px") + "\">Navbar: add links in its properties</mj-text>";
        }
        String color = prop("color", HEX_COLOR, "#333333");
        String size = prop("fontSize", PX, "14px");
        String linkPadding = prop("linkPadding", PADDING, "8px 12px");
        String weight = "true".equals(getPropertyString("bold")) ? "bold" : "normal";
        StringBuilder m = new StringBuilder("<mj-navbar align=\"").append(prop("align", ALIGN, "center"))
                .append("\" padding=\"").append(prop("padding", PADDING, "8px 32px")).append("\">");
        for (String[] l : links) {
            if (!ctx.isPreview() && ButtonBlock.NO_ADDRESS.matcher(l[1]).matches()) {
                ctx.warn("Navbar link \"" + l[2] + "\" has no link address.");
            }
            m.append("<mj-navbar-link href=\"").append(l[1]).append("\" color=\"").append(color).append("\" font-size=\"").append(size)
                    .append("\" font-weight=\"").append(weight).append("\" text-transform=\"none\" padding=\"").append(linkPadding).append("\">")
                    .append(l[0]).append("</mj-navbar-link>");
        }
        return m.append("</mj-navbar>").toString();
    }

    public String toText(EmailRenderContext ctx) {
        resolve(ctx);
        List<String> out = new ArrayList<>();
        for (String[] l : links) {
            out.add(ctx.text(ButtonBlock.NO_ADDRESS.matcher(l[1]).matches() ? l[0] : "<a href=\"" + l[1] + "\">" + l[0] + "</a>"));
        }
        return String.join("\n", out);
    }

    /** Each link as {label (resolved, escaped), href (checked), label as authored}; rows without a label are skipped. */
    private void resolve(EmailRenderContext ctx) {
        if (links != null) {
            return;
        }
        links = new ArrayList<>();
        Object rows = getProperty("links");
        if (rows instanceof Object[]) {
            for (Object row : (Object[]) rows) {
                if (row instanceof Map) {
                    Map<?, ?> r = (Map<?, ?>) row;
                    String label = r.get("label") == null ? "" : r.get("label").toString().trim();
                    if (!label.isEmpty()) {
                        String href = r.get("href") == null ? "" : r.get("href").toString();
                        links.add(new String[]{ctx.html(label), ctx.url(href), label});
                    }
                }
            }
        }
    }
}
