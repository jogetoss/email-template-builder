package org.joget.etb.composer;

import java.util.Collection;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Registers the AI Composer's chat and tool. Called by the plugin's Activator through reflection, so a build without
 * the composer, or a server without agent-builder (whose classes these extend), just skips it.
 */
public final class ComposerRegistration {

    private ComposerRegistration() {
    }

    public static void register(BundleContext context, Collection<ServiceRegistration> registrations) {
        // AI Composer is an Enterprise feature: agent-builder registers its own composers only there too
        if (!AppUtil.isEnterprise()) {
            LogUtil.info(ComposerRegistration.class.getName(), "AI Composer for email templates is off: it needs the Enterprise edition.");
            return;
        }
        registrations.add(context.registerService(EmailTemplateComposerChat.class.getName(), new EmailTemplateComposerChat(), null));
        registrations.add(context.registerService(EmailTemplateComposerTool.class.getName(), new EmailTemplateComposerTool(), null));
        LogUtil.info(ComposerRegistration.class.getName(), "AI Composer for email templates is on.");
    }
}
