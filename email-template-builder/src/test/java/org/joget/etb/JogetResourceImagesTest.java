package org.joget.etb;

import static org.junit.Assert.*;

import org.joget.apps.app.model.AppDefinition;
import org.junit.Test;

public class JogetResourceImagesTest {

    static AppDefinition app(String id, Long version) {
        AppDefinition a = new AppDefinition();
        a.setAppId(id);
        a.setVersion(version);
        return a;
    }

    @Test
    public void builderLinksNameTheVersionBeingEdited() {
        // without the version Joget serves the published version's file
        assertEquals("/jw/web/app/crm/3/resources/my%20logo.png", new JogetResourceImages(app("crm", 3L), "/jw").link("my logo.png"));
        assertEquals("/jw/web/app/crm/resources/logo.png", new JogetResourceImages(app("crm", null), "/jw").link("logo.png"));
    }

    @Test
    public void sendingEmbedsAndTheBackgroundUrlStaysVersionless() {
        JogetResourceImages sending = new JogetResourceImages(app("crm", 3L), null);
        assertNull("no link: the file is embedded", sending.link("logo.png"));
        assertEquals("/web/app/crm/resources/hero.jpg", sending.path("hero.jpg"));
    }
}
