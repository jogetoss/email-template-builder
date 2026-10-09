package org.joget.etb;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import org.joget.commons.util.LogUtil;
import org.joget.etb.lib.ButtonBlock;
import org.joget.etb.lib.TimelineBlock;
import org.joget.etb.lib.FactsBlock;
import org.joget.etb.lib.StatTilesBlock;
import org.joget.etb.lib.CalloutBlock;
import org.joget.etb.lib.BadgeBlock;
import org.joget.etb.lib.ColumnBlock;
import org.joget.etb.lib.DatalistTableBlock;
import org.joget.etb.lib.DividerBlock;
import org.joget.etb.lib.EmailTemplateHashVariable;
import org.joget.etb.lib.HtmlBlock;
import org.joget.etb.lib.HeroBlock;
import org.joget.etb.lib.ImageBlock;
import org.joget.etb.lib.NavbarBlock;
import org.joget.etb.lib.OutsideBlock;
import org.joget.etb.lib.ProgressBarsBlock;
import org.joget.etb.lib.RepeatBlock;
import org.joget.etb.lib.RowBlock;
import org.joget.etb.lib.SendTemplatedEmailTool;
import org.joget.etb.lib.SpacerBlock;
import org.joget.etb.lib.StepsBlock;
import org.joget.etb.lib.TextBlock;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    public static final String MESSAGE_PATH = "messages/emailTemplateBuilder";
    /**
     * The version of every plugin in this bundle: the POM's project version, filtered into the messages file at build
     * time (etb.version=${project.version}). Read from the bundled file, not through AppPluginUtil, so it also works
     * outside Joget (tests, the builder script's cache key).
     */
    public static final String VERSION = readVersion();

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(EmailTemplateBuilder.class.getName(), new EmailTemplateBuilder(), null));
        registrationList.add(context.registerService(EmailTemplateHashVariable.class.getName(), new EmailTemplateHashVariable(), null));
        registrationList.add(context.registerService(TextBlock.class.getName(), new TextBlock(), null));
        registrationList.add(context.registerService(ButtonBlock.class.getName(), new ButtonBlock(), null));
        registrationList.add(context.registerService(DividerBlock.class.getName(), new DividerBlock(), null));
        registrationList.add(context.registerService(SpacerBlock.class.getName(), new SpacerBlock(), null));
        registrationList.add(context.registerService(RowBlock.class.getName(), new RowBlock(), null));
        registrationList.add(context.registerService(ColumnBlock.class.getName(), new ColumnBlock(), null));
        registrationList.add(context.registerService(HtmlBlock.class.getName(), new HtmlBlock(), null));
        registrationList.add(context.registerService(ImageBlock.class.getName(), new ImageBlock(), null));
        registrationList.add(context.registerService(HeroBlock.class.getName(), new HeroBlock(), null));
        registrationList.add(context.registerService(NavbarBlock.class.getName(), new NavbarBlock(), null));
        registrationList.add(context.registerService(DatalistTableBlock.class.getName(), new DatalistTableBlock(), null));
        registrationList.add(context.registerService(RepeatBlock.class.getName(), new RepeatBlock(), null));
        registrationList.add(context.registerService(OutsideBlock.class.getName(), new OutsideBlock(), null));
        registrationList.add(context.registerService(StepsBlock.class.getName(), new StepsBlock(), null));
        registrationList.add(context.registerService(ProgressBarsBlock.class.getName(), new ProgressBarsBlock(), null));
        registrationList.add(context.registerService(BadgeBlock.class.getName(), new BadgeBlock(), null));
        registrationList.add(context.registerService(CalloutBlock.class.getName(), new CalloutBlock(), null));
        registrationList.add(context.registerService(StatTilesBlock.class.getName(), new StatTilesBlock(), null));
        registrationList.add(context.registerService(FactsBlock.class.getName(), new FactsBlock(), null));
        registrationList.add(context.registerService(TimelineBlock.class.getName(), new TimelineBlock(), null));
        registrationList.add(context.registerService(SendTemplatedEmailTool.class.getName(), new SendTemplatedEmailTool(), null));
        registerComposer(context);
    }

    /**
     * The optional AI Composer (org.joget.etb.composer): only in a build with the ai-composer profile, and only when
     * agent-builder is installed and exports its composer classes. Otherwise it is skipped; the builder is unaffected.
     */
    void registerComposer(BundleContext context) {
        try {
            Class.forName("org.joget.etb.composer.ComposerRegistration")
                    .getMethod("register", BundleContext.class, Collection.class).invoke(null, context, registrationList);
        } catch (ClassNotFoundException e) {
            // built without the composer
        } catch (java.lang.reflect.InvocationTargetException | LinkageError e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
            LogUtil.info(Activator.class.getName(), composerOffReason(cause, Activator::loadable));
        } catch (Exception e) {
            LogUtil.error(Activator.class.getName(), e, "AI Composer for email templates could not be registered");
        }
    }

    /** A class of agent-builder's that every release exports, to tell "not installed" from "installed without the composer export". */
    static final String AGENT_BUILDER_EXPORTED_CLASS = "org.joget.ai.agent.model.AgentToolAbstract";
    static final String COMPOSER_PACKAGE = "org.joget.ai.agent.lib.composer";

    /**
     * Why the composer is off, and what to do: agent-builder isn't installed; it is, but a build that doesn't export
     * its composer package (released versions don't yet); or it is a version whose composer classes don't match.
     */
    static String composerOffReason(Throwable cause, java.util.function.Predicate<String> loadable) {
        String prefix = "AI Composer for email templates is off: ";
        if (!loadable.test(AGENT_BUILDER_EXPORTED_CLASS)) {
            return prefix + "agent-builder isn't installed. Install it, then restart this plugin.";
        }
        String missing = cause == null || cause.getMessage() == null ? "" : cause.getMessage().replace('/', '.');
        if ((cause instanceof NoClassDefFoundError || cause instanceof ClassNotFoundException) && missing.contains(COMPOSER_PACKAGE + ".")) {
            return prefix + "the installed agent-builder doesn't export " + COMPOSER_PACKAGE
                    + ". Install a build that exports it, then restart this plugin.";
        }
        return prefix + "the installed agent-builder doesn't match this plugin's composer (" + cause
                + "). It needs agent-builder built from 8.2-SNAPSHOT with " + COMPOSER_PACKAGE + " exported.";
    }

    private static boolean loadable(String className) {
        try {
            Class.forName(className, false, Activator.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    static String readVersion() {
        try (InputStream in = Activator.class.getResourceAsStream("/" + MESSAGE_PATH + ".properties")) {
            Properties p = new Properties();
            if (in != null) {
                p.load(in);
            }
            String v = p.getProperty("etb.version", "").trim();
            // unfiltered (a build without resource filtering): say so rather than show the placeholder
            return v.isEmpty() || v.startsWith("${") ? "unknown" : v;
        } catch (IOException e) {
            return "unknown";
        }
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
