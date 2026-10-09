package org.joget.etb;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.felix.framework.Felix;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * Installs the packaged jar into Felix 7.0.5 (DX 9's version) with DX 9's own felix_config.properties,
 * then renders a template through classes loaded by the bundle's classloader: proves the Activator,
 * the embedded mjml4j + jfiveparse, the blocks and the exported API all resolve under Joget's OSGi setup.
 */
public class FelixBundleIT {

    static final String TEMPLATE = "{\"className\":\"org.joget.etb.EmailTemplateBuilder\",\"properties\":{\"subject\":\"OSGi\"},\"elements\":["
            + "{\"className\":\"org.joget.etb.lib.TextBlock\",\"properties\":{\"html\":\"<p>Rendered inside Felix</p>\"}},"
            + "{\"className\":\"org.joget.etb.lib.RowBlock\",\"properties\":{},\"elements\":["
            + "{\"className\":\"org.joget.etb.lib.ColumnBlock\",\"properties\":{},\"elements\":[{\"className\":\"org.joget.etb.lib.ButtonBlock\",\"properties\":{\"label\":\"Go\",\"href\":\"https://x.com\"}}]},"
            + "{\"className\":\"org.joget.etb.lib.ColumnBlock\",\"properties\":{},\"elements\":[{\"className\":\"org.joget.etb.lib.DividerBlock\",\"properties\":{}}]}]}]}";

    @Test
    public void bundleStartsAndRendersInsideJogetsOsgiSetup() throws Exception {
        File jar = new File(System.getProperty("bundle.jar"));
        assertTrue("packaged jar exists: " + jar, jar.isFile());

        Properties joget = new Properties();
        try (InputStream in = FelixBundleIT.class.getClassLoader().getResourceAsStream("felix_config.properties")) {
            assertNotNull("felix_config.properties from wflow-plugin-base", in);
            joget.load(in);
        }
        Map<String, Object> config = new HashMap<String, Object>();
        for (String key : joget.stringPropertyNames()) {
            config.put(key, joget.getProperty(key));
        }
        config.put("org.osgi.framework.storage", new File("target/felix-it-cache").getAbsolutePath());
        config.put("org.osgi.framework.storage.clean", "onFirstInit");
        config.put("felix.cache.locking", "false");
        // Felix otherwise falls back to the caller's class loader for loads started outside a bundle (this test),
        // which here has the whole classpath; in Joget such loads come from plugin code and get no fallback
        config.put("felix.bootdelegation.implicit", "false");

        Felix felix = new Felix(config);
        felix.start();
        try {
            Bundle bundle = felix.getBundleContext().installBundle(jar.toURI().toString());
            bundle.start();
            assertEquals("bundle ACTIVE (Activator registered the builder, hash variable and blocks)", Bundle.ACTIVE, bundle.getState());
            assertEquals(23, bundle.getRegisteredServices().length);

            // every plugin class must link with the packages Joget exports: a class that uses one DX 9 does not
            // export (e.g. org.springframework.web.context.request) fails only when first used, in production
            java.util.Enumeration<java.net.URL> classes = bundle.findEntries("org/joget/etb", "*.class", true);
            int linked = 0;
            while (classes.hasMoreElements()) {
                String path = classes.nextElement().getPath();
                String name = path.substring(path.indexOf("org/joget/etb"), path.length() - ".class".length()).replace('/', '.');
                if (name.startsWith("org.joget.etb.composer.")) {
                    // the optional AI Composer extends agent-builder's classes, which this Felix has not got; it
                    // registers only when they are there (Activator.registerComposer), checked below
                    continue;
                }
                Class.forName(name, true, bundle.loadClass(name).getClassLoader()).getDeclaredMethods();
                linked++;
            }
            assertTrue("plugin classes linked: " + linked, linked > 20);
            Class<?> mjml = bundle.loadClass("ch.digitalfondue.mjml4j.Mjml4j");
            assertNotSame("mjml4j comes from the embedded jar", FelixBundleIT.class.getClassLoader(), mjml.getClassLoader());
            assertNotNull("API exported", bundle.getHeaders().get("Export-Package"));
            assertTrue(bundle.getHeaders().get("Export-Package").contains("org.joget.etb.api"));

            Class<?> factories = bundle.loadClass("org.joget.etb.render.BlockFactory");
            Class<?> resolvers = bundle.loadClass("org.joget.etb.render.MergeResolver");
            Class<?> rendererClass = bundle.loadClass("org.joget.etb.render.EmailTemplateRenderer");
            Class<?> assignment = bundle.loadClass("org.joget.workflow.model.WorkflowAssignment");
            Class<?> appDef = bundle.loadClass("org.joget.apps.app.model.AppDefinition");
            Object renderer = rendererClass.getConstructor(factories, resolvers, boolean.class, assignment, appDef)
                    .newInstance(factories.getMethod("reflective").invoke(null), resolvers.getMethod("none").invoke(null), false, null, null);

            long t = System.nanoTime();
            Object result = rendererClass.getMethod("render", String.class).invoke(renderer, TEMPLATE);
            System.out.println("[etb] first render inside Felix: " + (System.nanoTime() - t) / 1_000_000 + " ms");

            String html = (String) result.getClass().getMethod("getHtml").invoke(result);
            Object warnings = result.getClass().getMethod("getWarnings").invoke(result);
            assertEquals("[]", warnings.toString());
            assertTrue(html.contains("Rendered inside Felix"));
            assertTrue(html.contains("mj-column-per-50"));
            assertTrue(html.contains("[if mso"));
        } finally {
            felix.stop();
            felix.waitForStop(10000);
        }
    }
}
