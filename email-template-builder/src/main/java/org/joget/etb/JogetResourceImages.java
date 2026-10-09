package org.joget.etb;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.joget.apps.app.dao.AppResourceDao;
import org.joget.apps.app.model.AppResource;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppResourceUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.etb.render.ResourceImages;

/**
 * App Resources of the template's own app. Only names registered as resources of that app are used
 * (as Joget's #appResource# hash variable does), so a crafted name can't reach other files.
 */
final class JogetResourceImages implements ResourceImages {

    private final AppDefinition appDef;
    private final String linkBase;

    /**
     * @param contextPath the web context path (e.g. "/jw") for builder links, or null to embed (sending)
     */
    JogetResourceImages(AppDefinition appDef, String contextPath) {
        this.appDef = appDef;
        // with the version being edited: without one Joget serves the PUBLISHED version's file, so a logo uploaded to a
        // new, unpublished version showed the old one (or nothing) on the canvas and in Preview
        this.linkBase = contextPath == null || appDef == null ? null
                : contextPath + "/web/app/" + appDef.getAppId() + (appDef.getVersion() == null ? "" : "/" + appDef.getVersion()) + "/resources/";
    }

    public File file(String name) {
        if (appDef == null) {
            return null;
        }
        AppResourceDao dao = (AppResourceDao) AppUtil.getApplicationContext().getBean("appResourceDao");
        if (dao.loadById(name, appDef) == null) {
            return null;
        }
        return AppResourceUtil.getFile(appDef.getAppId(), appDef.getVersion().toString(), name);
    }

    public String link(String name) {
        return linkBase == null ? null : linkBase + encode(name);
    }

    public boolean isPublic(String name) {
        if (appDef == null) {
            return false;
        }
        AppResourceDao dao = (AppResourceDao) AppUtil.getApplicationContext().getBean("appResourceDao");
        AppResource r = dao.loadById(name, appDef);
        // Joget marks a public resource with an empty permission class (AppResourceUtil.storeFile)
        return r != null && (r.getPermissionClass() == null || r.getPermissionClass().isEmpty());
    }

    /** Version-less on purpose: a sent email's background keeps working after a newer version is published. */
    public String path(String name) {
        return "/web/app/" + appDef.getAppId() + "/resources/" + encode(name);
    }

    static String encode(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
