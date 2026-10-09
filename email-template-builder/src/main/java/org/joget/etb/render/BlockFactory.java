package org.joget.etb.render;

import org.joget.apps.app.service.AppUtil;
import org.joget.etb.api.EmailBlock;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;

/** Creates a fresh block instance per element. */
public interface BlockFactory {

    /** @return a new instance, or null if the class is not an installed EmailBlock */
    EmailBlock create(String className);

    /** Installed plugins, via the PluginManager (a new instance per call). */
    static BlockFactory plugins() {
        return className -> {
            PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
            Plugin plugin = pluginManager.getPlugin(className);
            return plugin instanceof EmailBlock ? (EmailBlock) plugin : null;
        };
    }

    /** Plain reflection, for tests and for classes known to this bundle. */
    static BlockFactory reflective() {
        return className -> {
            try {
                Object o = Class.forName(className).getDeclaredConstructor().newInstance();
                return o instanceof EmailBlock ? (EmailBlock) o : null;
            } catch (Exception e) {
                return null;
            }
        };
    }
}
