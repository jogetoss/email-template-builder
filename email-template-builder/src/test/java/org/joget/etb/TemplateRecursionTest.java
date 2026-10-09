package org.joget.etb;

import static org.junit.Assert.*;

import org.joget.apps.app.model.AppDefinition;
import org.junit.Test;

/** A template that includes itself stops at the second visit instead of overflowing the stack. */
public class TemplateRecursionTest {

    static AppDefinition app(String id) {
        AppDefinition a = new AppDefinition();
        a.setAppId(id);
        return a;
    }

    @Test
    public void aTemplateInsideItselfStopsAtTheSecondVisit() {
        AppDefinition app = app("crm");
        int[] depth = {0};
        try {
            EmailTemplateService.once("ET-001", app, () -> {
                depth[0]++;
                // ET-001's text holds #emailTemplate.ET-001#: the hash variable renders it again
                return EmailTemplateService.once("ET-001", app, () -> ++depth[0]);
            });
            fail("expected TemplateIncludesItself");
        } catch (EmailTemplateService.TemplateIncludesItself e) {
            assertEquals(1, depth[0]);
            assertTrue(e.getMessage(), e.getMessage().contains("ET-001 includes itself"));
        }
    }

    @Test
    public void twoTemplatesIncludingEachOtherStopToo() {
        AppDefinition app = app("crm");
        try {
            EmailTemplateService.once("A", app, () -> EmailTemplateService.once("B", app, () -> EmailTemplateService.once("A", app, () -> "x")));
            fail("expected TemplateIncludesItself");
        } catch (EmailTemplateService.TemplateIncludesItself e) {
            assertTrue(e.getMessage().contains("A includes itself"));
        }
    }

    @Test
    public void oneTemplateMayIncludeAnotherAndBeRenderedAgainLater() {
        AppDefinition app = app("crm");
        assertEquals("B", EmailTemplateService.once("A", app, () -> EmailTemplateService.once("B", app, () -> "B")));
        assertEquals("again", EmailTemplateService.once("A", app, () -> "again"));
        assertEquals("the same id in another app is another template", "other",
                EmailTemplateService.once("A", app, () -> EmailTemplateService.once("A", app("hr"), () -> "other")));
    }
}
