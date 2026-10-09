package org.joget.etb.render;

import static org.junit.Assert.*;

import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** The design's rendering rules (v1 design §3, §8), one test each. */
public class RendererRulesTest {

    static final String TEXT = "org.joget.etb.lib.TextBlock";
    static final String BUTTON = "org.joget.etb.lib.ButtonBlock";
    static final String DIVIDER = "org.joget.etb.lib.DividerBlock";
    static final String SPACER = "org.joget.etb.lib.SpacerBlock";
    static final String ROW = "org.joget.etb.lib.RowBlock";
    static final String COLUMN = "org.joget.etb.lib.ColumnBlock";
    static final String HTML = "org.joget.etb.lib.HtmlBlock";
    static final String IMAGE = "org.joget.etb.lib.ImageBlock";
    static final String HERO = "org.joget.etb.lib.HeroBlock";
    static final String NAVBAR = "org.joget.etb.lib.NavbarBlock";
    static final String OUTSIDE = "org.joget.etb.lib.OutsideBlock";

    static JSONObject el(String className, JSONObject props, JSONObject... children) {
        JSONObject o = new JSONObject().put("className", className).put("properties", props);
        if (children.length > 0) {
            JSONArray a = new JSONArray();
            for (JSONObject c : children) {
                a.put(c);
            }
            o.put("elements", a);
        }
        return o;
    }

    static JSONObject text(String html) {
        return el(TEXT, new JSONObject().put("html", html));
    }

    static JSONObject button(String label, String href) {
        return el(BUTTON, new JSONObject().put("label", label).put("href", href));
    }

    static String template(JSONObject props, JSONObject... elements) {
        JSONArray a = new JSONArray();
        for (JSONObject e : elements) {
            a.put(e);
        }
        return new JSONObject().put("className", "org.joget.etb.EmailTemplateBuilder").put("properties", props).put("elements", a).toString();
    }

    static EmailRenderResult render(Map<String, String> values, JSONObject props, JSONObject... elements) {
        return TestSupport.renderer(values).render(template(props, elements));
    }

    static EmailRenderResult render(JSONObject... elements) {
        return render(Map.of(), new JSONObject(), elements);
    }

    @Test
    public void nestedTablesWithoutTbodyStillCompile() {
        // mjml4j lost the enclosing tags after a nested table with an implied tbody: the next column came out as literal MJML
        String nested = "<table><tr><td>A<table><tr><td>B</td></tr></table></td></tr></table>";
        JSONObject row = el("org.joget.etb.lib.RowBlock", new JSONObject(),
                el("org.joget.etb.lib.ColumnBlock", new JSONObject().put("width", "30%"), text(nested)),
                el("org.joget.etb.lib.ColumnBlock", new JSONObject().put("width", "70%"), text("<p>NEXT</p>")));
        EmailRenderResult r = render(row);
        assertFalse(r.getHtml().contains("<mj-"));
        assertTrue(r.getHtml().contains("NEXT"));
        assertEquals("<table><tbody><tr><td>A<table><tbody><tr><td>B</td></tr></tbody></table></td></tr></tbody></table><table><thead></thead></table>",
                EmailTemplateRenderer.explicitTbody(nested + "<table><thead></thead></table>"));
    }

    // rule 2: merge values are HTML-escaped, the author's static HTML is trusted

    @Test
    public void mergeValuesAreHtmlEscaped() {
        EmailRenderResult r = render(Map.of("form.payload", "<a href=\"https://evil.example/login\">Reset</a><img src=\"https://evil.example/p.gif\">"),
                new JSONObject(), text("<p><strong>Payload:</strong> #form.payload#</p>"));
        assertFalse("no live injected link", r.getHtml().contains("<a href=\"https://evil.example"));
        assertFalse("no live injected image", r.getHtml().contains("<img src=\"https://evil.example"));
        assertTrue(r.getHtml().contains("&lt;a href="));
        assertTrue("author markup kept", r.getHtml().contains("<strong>Payload:</strong>"));
    }

    @Test
    public void unresolvedMergeFieldsStayVisible() {
        EmailRenderResult r = render(text("<p>Hi #user.unknown#</p>"));
        assertTrue(r.getHtml().contains("#user.unknown#"));
    }

    @Test
    public void trustedHtmlBlockInsertsHtmlValuesButTextStillEscapes() {
        Map<String, String> v = Map.of("app.digest", "<ol><li><strong>Changed</strong></li></ol>");
        EmailRenderResult r = render(v, new JSONObject(), el(HTML, new JSONObject().put("html", "#app.digest#")), text("<p>#app.digest#</p>"));
        assertTrue("trusted block keeps the HTML", r.getHtml().contains("<ol><li><strong>Changed</strong></li></ol>"));
        assertTrue("text block still escapes the same value", r.getHtml().contains("&lt;ol&gt;"));
        assertTrue(r.getText().contains("- Changed"));
    }

    @Test
    public void imagesRenderLinkAndFallBackToAPlaceholder() {
        EmailRenderResult r = render(el(IMAGE, new JSONObject().put("src", "https://cdn.example.com/logo.png").put("alt", "ACME \"logo\"")
                .put("href", "https://example.com").put("width", "120px")));
        assertTrue(r.getHtml().contains("src=\"https://cdn.example.com/logo.png\""));
        assertTrue(r.getHtml().contains("alt=\"ACME &quot;logo&quot;\""));
        assertTrue(r.getHtml().contains("href=\"https://example.com\""));
        assertTrue(r.getWarnings().isEmpty());
        assertEquals("ACME \"logo\" (https://example.com)", r.getText());

        assertTrue(render(el(IMAGE, new JSONObject().put("src", "cid:logo.png").put("alt", "Logo"))).getHtml().contains("src=\"cid:logo.png\""));

        EmailRenderResult none = render(el(IMAGE, new JSONObject().put("alt", "Hero image")));
        assertTrue("placeholder box", none.getHtml().contains(">Hero image</div>"));
        assertEquals(1, none.getWarnings().size());

        EmailRenderResult js = render(el(IMAGE, new JSONObject().put("src", "javascript:alert(1)").put("alt", "x")));
        assertFalse(js.getHtml().contains("javascript:"));
    }

    // card theme, bands, bordered columns

    @Test
    public void cardThemeRoundsAndShadowsTheContent() {
        EmailRenderResult r = render(Map.of(), new JSONObject().put("contentBorderRadius", "8px").put("contentShadow", "true"), text("<p>x</p>"));
        assertTrue("mjml4j clips children to the radius", r.getHtml().contains("border-radius:8px;overflow:hidden"));
        assertTrue(r.getHtml().contains("box-shadow"));
        EmailRenderResult plain = render(text("<p>x</p>"));
        assertFalse(plain.getHtml().contains("box-shadow"));
        assertFalse(plain.getHtml().contains("border-radius:8px"));
    }

    @Test
    public void textBackgroundMakesAFullWidthBand() {
        EmailRenderResult r = render(el(TEXT, new JSONObject().put("html", "<p>Header</p>").put("backgroundColor", "#0b5641").put("color", "#ffffff")));
        assertTrue(r.getHtml().contains("background:#0b5641"));
    }

    @Test
    public void columnsTakeBorderRadiusAndBackground() {
        EmailRenderResult r = render(el(ROW, new JSONObject(), el(COLUMN, new JSONObject().put("borderColor", "#dddddd").put("borderRadius", "4px")
                .put("backgroundColor", "#f9f9f9").put("padding", "12px"), text("<p>card</p>"))));
        assertTrue(r.getHtml().contains("border:1px solid #dddddd"));
        assertTrue(r.getHtml().contains("border-radius:4px"));
        assertTrue(r.getHtml().contains("background-color:#f9f9f9"));
    }

    @Test
    public void canvasFragmentsHaveNoPageOrSectionBackground() {
        String canvas = TestSupport.previewRenderer().renderBlockPreview(text("<p>x</p>").toString(), new JSONObject().put("backgroundColor", "#123456").toString());
        assertFalse("a block shows the colour of what it sits in", canvas.contains("#123456"));
        assertFalse(canvas.contains("background-color:#ffffff"));
    }

    // URL policy

    @Test
    public void dangerousSchemesAreBlocked() {
        for (String href : new String[] {"javascript:alert(1)", "JAVASCRIPT:alert(1)", "java\tscript:alert(1)", " javascript:alert(1)",
                "data:text/html,<script>alert(1)</script>", "vbscript:x"}) {
            EmailRenderResult r = render(button("Go", href));
            assertFalse(href, r.getHtml().toLowerCase().contains("script:"));
            assertFalse(href, r.getHtml().contains("data:text"));
            assertTrue(href, r.getHtml().contains("href=\"#\""));
            assertEquals(href, 1, r.getWarnings().size());
        }
    }

    @Test
    public void mergedUrlIsCheckedAfterResolution() {
        EmailRenderResult r = render(Map.of("form.link", "javascript:alert(1)"), new JSONObject(), button("Go", "#form.link#"));
        assertFalse(r.getHtml().contains("javascript:"));
        assertEquals(1, r.getWarnings().size());
    }

    @Test
    public void safeUrlsPassWithQueryStrings() {
        EmailRenderResult r = render(Map.of("form.id", "42"), new JSONObject(), button("Go", "https://example.com/view?id=#form.id#&tab=1"));
        assertTrue(r.getHtml().contains("https://example.com/view?id=42&"));
        assertTrue(r.getWarnings().isEmpty());
        for (String ok : new String[] {"mailto:a@b.com", "tel:+6512345678", "cid:logo.png", "http://x.com"}) {
            assertTrue(ok, render(button("Go", ok)).getWarnings().isEmpty());
        }
    }

    @Test
    public void quoteInUrlCannotBreakTheAttribute() {
        EmailRenderResult r = render(button("Go", "https://x.com/\" onclick=\"alert(1)"));
        assertFalse(r.getHtml().contains("onclick=\"alert"));
    }

    @Test
    public void buttonWithoutAnAddressWarns() {
        for (String href : new String[] {"", "https://", "http://", "mailto:"}) {
            assertEquals("[" + href + "]", 1, render(button("Go", href)).getWarnings().size());
        }
        assertTrue(render(button("Go", "https://x.com")).getWarnings().isEmpty());
    }

    // style properties are validated, never written raw

    @Test
    public void invalidStyleValuesFallBackToDefaults() {
        JSONObject b = el(BUTTON, new JSONObject().put("label", "Go").put("href", "https://x.com")
                .put("backgroundColor", "red\" onmouseover=\"x").put("align", "middle").put("padding", "1px;background:url(x)"));
        EmailRenderResult r = render(b);
        assertFalse(r.getHtml().contains("onmouseover"));
        assertFalse(r.getHtml().contains("url(x)"));
        assertTrue(r.getHtml().contains("#6e9f4b"));
    }

    @Test
    public void aBareNumberTakesTheFieldsUnit() {
        EmailRenderResult r = render(Map.of(), new JSONObject().put("width", "520").put("contentBorderRadius", "12"),
                el(TEXT, new JSONObject().put("html", "<p>x</p>").put("fontSize", "14").put("padding", "8 32")),
                el(DIVIDER, new JSONObject().put("width", "50").put("thickness", "2")),
                el(BUTTON, new JSONObject().put("label", "Go").put("href", "https://x.com").put("fontSize", "12pt").put("padding", "4px 10").put("innerPadding", "10 14")));
        String mjml = r.getMjml();
        assertTrue(mjml, mjml.contains("font-size=\"14px\"") && mjml.contains("padding=\"8px 32px\""));
        assertTrue(mjml, mjml.contains("width=\"520px\""));
        assertTrue("a % field takes %", mjml.contains("width=\"50%\""));
        assertTrue(mjml, mjml.contains("border-width=\"2px\""));
        assertTrue("px and bare numbers mix", mjml.contains("padding=\"4px 10px\""));
        assertFalse("another unit is still not accepted", mjml.contains("12pt"));
        assertTrue("a button's inside space", mjml.contains("inner-padding=\"10px 14px\""));
        assertEquals("16px", org.joget.etb.api.DefaultEmailBlock.withUnit("16px", org.joget.etb.api.DefaultEmailBlock.PX));
        assertEquals("1.5em", org.joget.etb.api.DefaultEmailBlock.withUnit(" 1.5em ", org.joget.etb.api.DefaultEmailBlock.PX));
    }

    @Test
    public void themeIsValidatedAndClamped() {
        EmailRenderResult r = render(Map.of(), new JSONObject().put("backgroundColor", "#123456").put("width", "1200px")
                .put("fontFamily", "Arial\"><script>x</script>"), text("<p>x</p>"));
        assertTrue(r.getHtml().contains("#123456"));
        assertTrue("width clamped to 800px", r.getMjml().contains("width=\"800px\""));
        assertFalse(r.getHtml().contains("<script>x"));
    }

    // subject

    @Test
    public void subjectIsResolvedAndLineBreaksStripped() {
        EmailRenderResult r = render(Map.of("form.title", "Laptop"), new JSONObject().put("subject", "Approved: #form.title#\r\nBcc: attacker@example.com"),
                text("<p>x</p>"));
        assertEquals("Approved: Laptop Bcc: attacker@example.com", r.getSubject());
    }

    @Test
    public void preheaderIsEmittedAndEscaped() {
        EmailRenderResult r = render(Map.of("user.name", "<b>Al</b>"), new JSONObject().put("preheader", "Hi #user.name#"), text("<p>x</p>"));
        assertTrue(r.getHtml().contains("Hi &lt;b&gt;Al&lt;/b&gt;"));
    }

    // structure

    @Test
    public void unknownBlocksAreSkippedWithAWarning() {
        EmailRenderResult r = render(el("x.y.Nope", new JSONObject()), text("<p>kept</p>"));
        assertTrue(r.getHtml().contains("kept"));
        assertEquals(1, r.getWarnings().size());
        assertTrue(r.getWarnings().get(0).contains("x.y.Nope"));
    }

    @Test
    public void placementRulesAreEnforced() {
        EmailRenderResult columnAtTop = render(el(COLUMN, new JSONObject(), text("<p>orphan</p>")));
        assertFalse(columnAtTop.getHtml().contains("orphan"));
        assertEquals(1, columnAtTop.getWarnings().size());

        EmailRenderResult textInRow = render(el(ROW, new JSONObject(), text("<p>not in a column</p>")));
        assertFalse(textInRow.getHtml().contains("not in a column"));
        assertEquals(1, textInRow.getWarnings().size());
    }

    @Test
    public void twoColumnRowRendersAndFeedsTheTextPart() {
        EmailRenderResult r = render(el(ROW, new JSONObject(),
                el(COLUMN, new JSONObject(), text("<p>Left side</p>")),
                el(COLUMN, new JSONObject(), text("<p>Right side</p>"))));
        assertTrue(r.getHtml().contains("mj-column-per-50"));
        assertTrue(r.getText().contains("Left side"));
        assertTrue(r.getText().contains("Right side"));
    }

    @Test
    public void rowCanKeepColumnsSideBySideOnMobile() {
        JSONObject cols = el(ROW, new JSONObject().put("keepSideBySide", "true"),
                el(COLUMN, new JSONObject(), button("Approve", "https://x.com/a")), el(COLUMN, new JSONObject(), button("Reject", "https://x.com/r")));
        EmailRenderResult grouped = render(cols);
        assertTrue(grouped.getMjml().contains("<mj-group><mj-column"));
        assertTrue(grouped.getWarnings().isEmpty());
        assertFalse(render(el(ROW, new JSONObject(), el(COLUMN, new JSONObject(), text("<p>x</p>")))).getMjml().contains("<mj-group>"));
    }

    @Test
    public void textPartCoversEveryBlock() {
        EmailRenderResult r = render(text("<p><strong>Hello</strong> there</p>"), button("View request", "https://x.com/r/1"),
                el(DIVIDER, new JSONObject()), el(SPACER, new JSONObject()), text("<p>Footer</p>"));
        assertEquals("Hello there\n\nView request (https://x.com/r/1)\n\n----------------------------------------\n\nFooter", r.getText());
    }

    @Test
    public void textPartDropsAButtonsEmptyAddress() {
        assertEquals("View request", render(button("View request", "https://")).getText());
    }

    @Test
    public void oversizedEmailsWarn() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            big.append("<p>Line ").append(i).append(" with some padding text.</p>");
        }
        EmailRenderResult r = render(text(big.toString()));
        assertTrue(r.getSizeBytes() > EmailTemplateRenderer.SIZE_WARN_BYTES);
        assertTrue(r.getWarnings().get(0).contains("Gmail"));
    }

    // canvas preview

    @Test
    public void inlineEditMarkerOnlyOnTheCanvas() {
        JSONObject t = text("<p>Hi #user.name#</p>");
        String canvas = TestSupport.previewRenderer().renderBlockPreview(t.toString(), null);
        assertTrue(canvas.contains("data-etb-inline"));
        assertTrue("merge fields unresolved on canvas", canvas.contains("#user.name#"));
        assertFalse("fragment, not a document", canvas.contains("<body"));

        EmailRenderResult sent = render(Map.of("user.name", "Al"), new JSONObject(), t);
        assertFalse(sent.getHtml().contains("data-etb-inline"));
        assertTrue(sent.getHtml().contains("Hi Al"));
    }

    @Test
    public void canvasAllowsMergeFieldLinksButNotJavascript() {
        String mergeLink = TestSupport.previewRenderer().renderBlockPreview(button("Go", "#form.req.link#").toString(), null);
        assertTrue(mergeLink.contains("href=\"#form.req.link#\""));
        String js = TestSupport.previewRenderer().renderBlockPreview(button("Go", "javascript:alert(1)").toString(), null);
        assertFalse(js.contains("javascript:"));
    }

    @Test
    public void canvasUsesTheTemplateTheme() {
        String canvas = TestSupport.previewRenderer().renderBlockPreview(text("<p>x</p>").toString(),
                new JSONObject().put("fontFamily", "Georgia, Times New Roman, serif").toString());
        assertTrue(canvas.contains("Georgia"));
    }

    // Hero: a top-level section with an Outlook (VML) background

    @Test
    public void heroIsATopLevelSectionWithAnOutlookBackground() {
        EmailRenderResult r = render(el(HERO, new JSONObject().put("headline", "Launch day").put("text", "It is here.")
                .put("buttonLabel", "See it").put("buttonHref", "https://x.com/launch").put("backgroundUrl", "https://x.com/bg.jpg")));
        assertTrue("not wrapped in a section", r.getMjml().contains("<mj-hero mode=\"fixed-height\" height=\"300px\""));
        assertFalse(r.getMjml().contains("<mj-column><mj-hero"));
        assertTrue("VML background for classic Outlook", r.getHtml().contains("v:image") && r.getHtml().contains("https://x.com/bg.jpg"));
        assertEquals("Launch day\n\nIt is here.\n\nSee it (https://x.com/launch)", r.getText());
        assertTrue(r.getWarnings().isEmpty());
    }

    @Test
    public void heroIsFullWidthWithItsSidePaddingOnTheContent() {
        EmailRenderResult r = render(el(HERO, new JSONObject().put("headline", "H").put("text", "T").put("buttonLabel", "B")
                .put("buttonHref", "https://x.com").put("padding", "40px 24px 30px 16px")));
        assertTrue("mjml4j narrows a hero by its side padding", r.getMjml().contains("padding=\"40px 0px 30px\""));
        assertTrue(r.getMjml().contains("padding=\"0px 24px 12px 16px\">H<"));
        assertTrue(r.getMjml().contains("padding=\"0px 24px 16px 16px\">T<"));
        assertTrue(r.getMjml().contains("padding=\"8px 24px 0px 16px\">B<"));
        assertTrue(r.getHtml().contains("max-width:600px"));
        assertFalse(r.getHtml().contains("max-width:560px"));
    }

    @Test
    public void heroEscapesMergeValuesAndChecksItsLinks() {
        EmailRenderResult r = render(Map.of("form.p.name", "<b>Box</b>"), new JSONObject(),
                el(HERO, new JSONObject().put("headline", "New: #form.p.name#").put("buttonLabel", "Go").put("buttonHref", "javascript:alert(1)")
                        .put("backgroundUrl", "javascript:alert(2)").put("backgroundColor", "red;x").put("height", "9999999px")));
        assertTrue(r.getMjml().contains("New: &lt;b&gt;Box&lt;/b&gt;"));
        assertFalse(r.getMjml().contains("javascript:"));
        assertFalse("a blocked background is left out", r.getMjml().contains("background-url"));
        assertTrue("bad styles fall back", r.getMjml().contains("background-color=\"#2d4a6b\"") && r.getMjml().contains("height=\"300px\""));
        assertEquals(2, r.getWarnings().stream().filter(w -> w.startsWith("Blocked link")).count());
    }

    @Test
    public void heroCannotGoInAColumn() {
        EmailRenderResult r = render(el(ROW, new JSONObject(), el(COLUMN, new JSONObject(), el(HERO, new JSONObject().put("headline", "x")))));
        assertFalse(r.getMjml().contains("mj-hero"));
        assertTrue(r.getWarnings().contains("Hero cannot be placed in a column; it was skipped."));
    }

    @Test
    public void heroWithoutImageOrButtonIsAColourBand() {
        EmailRenderResult r = render(el(HERO, new JSONObject().put("headline", "Hello")));
        assertFalse(r.getMjml().contains("background-url"));
        assertFalse(r.getMjml().contains("mj-button"));
        assertEquals("Hello", r.getText());
    }

    // Navbar: inline links, no hamburger

    static JSONObject navbar(String... labelHref) {
        JSONArray links = new JSONArray();
        for (int i = 0; i < labelHref.length; i += 2) {
            links.put(new JSONObject().put("label", labelHref[i]).put("href", labelHref[i + 1]));
        }
        return el(NAVBAR, new JSONObject().put("links", links));
    }

    @Test
    public void navbarRendersInlineLinksInPlainCase() {
        EmailRenderResult r = render(navbar("Home", "https://x.com/", "Blog", "https://x.com/blog", "", "https://x.com/skipped"));
        assertTrue(r.getMjml().contains("<mj-navbar-link href=\"https://x.com/blog\" color=\"#333333\" font-size=\"14px\" font-weight=\"normal\" text-transform=\"none\""));
        assertFalse("rows without a label are skipped", r.getMjml().contains("skipped"));
        assertFalse("no hamburger", r.getMjml().contains("hamburger"));
        assertTrue(r.getHtml().contains(">\nBlog\n</a>") || r.getHtml().contains(">Blog</a>"));
        assertEquals("Home (https://x.com/)\nBlog (https://x.com/blog)", r.getText());
    }

    @Test
    public void navbarChecksLinksAndEscapesLabels() {
        EmailRenderResult r = render(Map.of("form.u.team", "<i>Ops</i>"), new JSONObject(),
                navbar("#form.u.team#", "javascript:alert(1)", "Help", "https://"));
        assertTrue(r.getMjml().contains("&lt;i&gt;Ops&lt;/i&gt;"));
        assertFalse(r.getMjml().contains("javascript:"));
        assertTrue(r.getWarnings().contains("Navbar link \"Help\" has no link address."));
        assertTrue(r.getText().endsWith("\nHelp"));
    }

    @Test
    public void emptyNavbarIsLeftOutOfTheEmail() {
        EmailRenderResult r = render(navbar());
        assertFalse(r.getMjml().contains("mj-navbar"));
        assertTrue(r.getWarnings().contains("Navbar has no links; it was left out."));
    }

    // App Resource images: embedded as cid: when sending, linked in the builder

    static final java.io.File LOGO = new java.io.File("/tmp/app/resources/logo.png");

    static ResourceImages images(String linkBase) {
        return new ResourceImages() {
            public java.io.File file(String name) {
                return "logo.png".equals(name) || "private.png".equals(name) ? LOGO : null;
            }

            public String link(String name) {
                return linkBase == null ? null : linkBase + name;
            }

            public boolean isPublic(String name) {
                return "logo.png".equals(name);
            }

            public String path(String name) {
                return "/web/app/demo/resources/" + name;
            }
        };
    }

    static EmailRenderResult renderWithTheme(ResourceImages images, JSONObject templateProps, JSONObject... elements) {
        return TestSupport.renderer(Map.of()).withImages(images).render(template(templateProps, elements));
    }

    static JSONObject hero(String background) {
        return el(HERO, new JSONObject().put("headline", "H").put("backgroundUrl", background));
    }

    static EmailRenderResult renderWithImages(ResourceImages images, JSONObject... elements) {
        return TestSupport.renderer(Map.of()).withImages(images).render(template(new JSONObject(), elements));
    }

    static JSONObject image(String src) {
        return el(IMAGE, new JSONObject().put("src", src).put("alt", "Logo"));
    }

    @Test
    public void appResourceImagesAreEmbeddedOnceWhenSending() {
        EmailRenderResult r = renderWithImages(images(null), image("#appResource.logo.png#"), image(" #appResource.logo.png# "));
        assertTrue(r.getMjml().contains("src=\"cid:etb1-logo.png\""));
        assertEquals("the same file is attached once", Map.of("etb1-logo.png", LOGO), r.getInlineImages());
        assertTrue(r.getWarnings().isEmpty());
    }

    @Test
    public void appResourceImagesAreLinkedInTheBuilder() {
        EmailRenderResult r = renderWithImages(images("/jw/web/app/demo/resources/"), image("#appResource.logo.png#"));
        assertTrue(r.getMjml().contains("src=\"/jw/web/app/demo/resources/logo.png\""));
        assertTrue(r.getInlineImages().isEmpty());
    }

    @Test
    public void unknownOrUnreachableAppResourcesAreLeftOut() {
        EmailRenderResult missing = renderWithImages(images(null), image("#appResource.gone.png#"), image("#appResource.../../etc/passwd#"));
        assertFalse(missing.getMjml().contains("cid:"));
        assertTrue(missing.getWarnings().contains("Image \"gone.png\" is not in this app's App Resources; it was left out."));
        assertTrue(missing.getWarnings().stream().noneMatch(w -> w.contains("passwd") && w.contains("App Resources;")) || !missing.getMjml().contains("passwd\""));
        assertTrue(missing.getInlineImages().isEmpty());
        EmailRenderResult noSender = render(image("#appResource.logo.png#"));
        assertTrue(noSender.getWarnings().contains("Image \"logo.png\" is an App Resource, which only the Send Templated Email tool can embed; it was left out."));
    }

    @Test
    public void plainImageUrlsAreUnchanged() {
        EmailRenderResult r = renderWithImages(images(null), image("https://cdn.example.com/a.png"));
        assertTrue(r.getMjml().contains("src=\"https://cdn.example.com/a.png\""));
        assertTrue(r.getInlineImages().isEmpty());
    }

    // Hero backgrounds: email apps load backgrounds by URL only, so an App Resource is linked, never embedded

    @Test
    public void heroAppResourceBackgroundIsAPublicLink() {
        EmailRenderResult r = renderWithTheme(images(null), new JSONObject().put("publicUrl", "https://apps.example.com/jw/"), hero("#appResource.logo.png#"));
        assertTrue(r.getMjml().contains("background-url=\"https://apps.example.com/jw/web/app/demo/resources/logo.png\""));
        assertTrue("not embedded", r.getInlineImages().isEmpty());
        assertTrue(r.getWarnings().isEmpty());
    }

    @Test
    public void heroBackgroundNeedsAPublicFileAndAServerAddress() {
        EmailRenderResult privateFile = renderWithTheme(images(null), new JSONObject().put("publicUrl", "https://apps.example.com/jw"), hero("#appResource.private.png#"));
        assertFalse(privateFile.getMjml().contains("background-url"));
        assertTrue(privateFile.getWarnings().get(0).startsWith("Background \"private.png\" is not a public App Resource"));

        EmailRenderResult noAddress = renderWithTheme(images(null), new JSONObject(), hero("#appResource.logo.png#"));
        assertFalse(noAddress.getMjml().contains("background-url"));
        assertTrue(noAddress.getWarnings().get(0).contains("needs the template's Public server address"));

        EmailRenderResult badAddress = renderWithTheme(images(null), new JSONObject().put("publicUrl", "javascript:alert(1)"), hero("#appResource.logo.png#"));
        assertFalse(badAddress.getMjml().contains("javascript"));
    }

    @Test
    public void builderShowsTheHeroBackgroundButStillWarns() {
        EmailRenderResult r = renderWithTheme(images("/jw/web/app/demo/resources/"), new JSONObject(), hero("#appResource.logo.png#"));
        assertTrue(r.getMjml().contains("background-url=\"/jw/web/app/demo/resources/logo.png\""));
        assertTrue(r.getWarnings().get(0).contains("Public server address"));
    }

    // "Outside the card": blocks on the page background; first = above the card, last = below, middle = two cards

    static JSONObject outside(JSONObject... children) {
        return el(OUTSIDE, new JSONObject().put("padding", "24px 0px"), children);
    }

    static int count(String s, String part) {
        return s.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    public void logoAboveAndFooterBelowTheCard() {
        EmailRenderResult r = render(outside(text("<p>LOGO</p>")), text("<p>Welcome</p>"), outside(text("<p>Questions?</p>")));
        String m = r.getMjml();
        assertEquals("one card", 1, count(m, "<mj-wrapper css-class=\"etb-card\""));
        assertTrue("logo before the card", m.indexOf("LOGO") < m.indexOf("etb-card"));
        assertTrue("welcome in the card", m.indexOf("Welcome") > m.indexOf("etb-card") && m.indexOf("Welcome") < m.indexOf("Questions?"));
        int cardEnd = m.indexOf("</mj-wrapper>", m.indexOf("etb-card"));
        assertTrue("footer after the card closes", m.indexOf("Questions?") > cardEnd);
        assertEquals("no page spacers: the outside blocks give the margin", 0, count(m, "<mj-spacer height=\"24px\">"));
        assertEquals("LOGO\n\nWelcome\n\nQuestions?", r.getText());
        assertTrue(r.getHtml().contains("LOGO"));
    }

    @Test
    public void anOutsideBlockInTheMiddleSplitsTheCard() {
        String m = render(text("<p>A</p>"), outside(text("<p>between</p>")), text("<p>B</p>")).getMjml();
        assertEquals(2, count(m, "<mj-wrapper css-class=\"etb-card\""));
        assertEquals("page margin above the first card and below the last only", 2, count(m, "<mj-spacer height=\"24px\">"));
        assertTrue(m.indexOf("A</p>") < m.indexOf("between") && m.indexOf("between") < m.indexOf("B</p>"));
    }

    @Test
    public void outsideBlocksHoldRowsAndSitOnThePageBackground() {
        String m = render(outside(el(ROW, new JSONObject(), el(COLUMN, new JSONObject(), text("<p>L</p>")), el(COLUMN, new JSONObject(), text("<p>R</p>")))),
                text("<p>x</p>")).getMjml();
        int body = m.indexOf(">", m.indexOf("<mj-body")) + 1;
        String before = m.substring(body, m.indexOf("<mj-wrapper css-class=\"etb-card\""));
        assertEquals("the row's two columns", 2, count(before, "<mj-column"));
        assertTrue(before.startsWith("<mj-wrapper padding=\"24px 0px\">"));
        assertFalse("transparent: the page background shows", before.startsWith("<mj-wrapper padding=\"24px 0px\" background-color"));
    }

    @Test
    public void outsideBlocksOnlyAtTheTopLevel() {
        EmailRenderResult r = render(el(ROW, new JSONObject(), el(COLUMN, new JSONObject(), outside(text("<p>x</p>")))));
        assertTrue(r.getWarnings().contains("Outside the card cannot be placed in a column; it was skipped."));
    }

    @Test
    public void noOutsideBlocksMeansTheSameSingleCardAsBefore() {
        String m = render(text("<p>a</p>"), text("<p>b</p>")).getMjml();
        assertEquals(1, count(m, "<mj-wrapper css-class=\"etb-card\""));
        assertEquals(2, count(m, "<mj-spacer height=\"24px\">"));
    }

    @Test
    public void withNotesAndWithWarningReturnCopies() {
        EmailRenderResult r = render(text("<p>x</p>"));
        EmailRenderResult noted = r.withNotes(java.util.List.of(new EmailRenderResult.DataNote("Repeat", "L", 0, 20, java.util.List.of())));
        assertNotSame(r, noted);
        assertTrue(r.getNotes().isEmpty());
        assertEquals(1, noted.getNotes().size());
        assertEquals("a warning keeps the notes", 1, noted.withWarning("w").getNotes().size());
    }
}
