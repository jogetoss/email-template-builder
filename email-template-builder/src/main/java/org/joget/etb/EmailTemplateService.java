package org.joget.etb;

import org.joget.apps.app.dao.BuilderDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.BuilderDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.etb.render.BlockFactory;
import org.joget.etb.render.EmailRenderResult;
import org.joget.etb.render.EmailTemplateRenderer;
import org.joget.etb.render.MergeResolver;
import org.joget.etb.render.RecordResolver;
import org.joget.etb.render.TrackingResolver;
import org.joget.workflow.model.WorkflowAssignment;

/** Loads saved templates (BuilderDefinitions of type {@value EmailTemplateBuilder#OBJECT_NAME}) and renders them. */
public final class EmailTemplateService {

    private EmailTemplateService() {
    }

    /**
     * Saved templates being rendered on this thread (app and id). #emailTemplate.X# inside X (or X and Y including each
     * other) would otherwise recurse until the stack overflows: an Error that neither Joget nor the plugin catches.
     */
    private static final ThreadLocal<java.util.Set<String>> RENDERING = ThreadLocal.withInitial(java.util.HashSet::new);

    /** A template that (directly or through another one) includes itself. */
    public static final class TemplateIncludesItself extends RuntimeException {
        public TemplateIncludesItself(String id) {
            super("Email template " + id + " includes itself (through #emailTemplate." + id + "#)");
        }
    }

    /** Runs a render of the saved template {@code id}, unless that template is already being rendered on this thread. */
    static <T> T once(String id, AppDefinition appDef, java.util.function.Supplier<T> render) {
        String key = (appDef == null ? "" : appDef.getAppId()) + ":" + id;
        java.util.Set<String> active = RENDERING.get();
        if (!active.add(key)) {
            throw new TemplateIncludesItself(id);
        }
        try {
            return render.get();
        } finally {
            active.remove(key);
        }
    }

    /** @return the template JSON, or null if there is no email template with this id in the app */
    public static String load(String id, AppDefinition appDef) {
        if (id == null || id.isEmpty() || appDef == null) {
            return null;
        }
        BuilderDefinitionDao dao = (BuilderDefinitionDao) AppUtil.getApplicationContext().getBean("builderDefinitionDao");
        BuilderDefinition def = dao.loadById(id, appDef);
        if (def == null || !EmailTemplateBuilder.OBJECT_NAME.equals(def.getType())) {
            return null;
        }
        return def.getJson();
    }

    /**
     * Render a saved template with merge fields resolved against the assignment (null outside a process).
     * App Resource images are left out (with a warning): only a sender that embeds them can use them, see
     * {@link #renderForSending}.
     */
    public static EmailRenderResult render(String id, AppDefinition appDef, WorkflowAssignment assignment) {
        String json = load(id, appDef);
        return json == null ? null : once(id, appDef, () -> render(json, appDef, assignment, false));
    }

    /** As {@link #render(String, AppDefinition, WorkflowAssignment)}, with App Resource images to embed (getInlineImages). */
    public static EmailRenderResult renderForSending(String id, AppDefinition appDef, WorkflowAssignment assignment) {
        return renderForSending(id, appDef, assignment, null);
    }

    /**
     * @param variables the template variables' values by name (e.g. Send Templated Email's mapping); a declared
     *                  variable without a value is sent empty. Never the builder's test values.
     */
    public static EmailRenderResult renderForSending(String id, AppDefinition appDef, WorkflowAssignment assignment, java.util.Map<String, String> variables) {
        String json = load(id, appDef);
        return json == null ? null : once(id, appDef, () -> renderJsonForSending(json, appDef, assignment, variables));
    }

    public static EmailRenderResult renderJsonForSending(String json, AppDefinition appDef, WorkflowAssignment assignment) {
        return renderJsonForSending(json, appDef, assignment, null);
    }

    public static EmailRenderResult renderJsonForSending(String json, AppDefinition appDef, WorkflowAssignment assignment, java.util.Map<String, String> variables) {
        return renderer(appDef, assignment, false).withImages(new JogetResourceImages(appDef, null))
                .withVariables(variables == null ? java.util.Collections.<String, String>emptyMap() : variables).render(json);
    }

    /** Sending (not preview) gets no variable values here: declared variables are empty. */
    public static EmailRenderResult render(String json, AppDefinition appDef, WorkflowAssignment assignment, boolean preview) {
        EmailTemplateRenderer r = renderer(appDef, assignment, preview);
        return (preview ? r : r.withVariables(java.util.Collections.<String, String>emptyMap())).render(json);
    }

    public static EmailTemplateRenderer renderer(AppDefinition appDef, WorkflowAssignment assignment, boolean preview) {
        return new EmailTemplateRenderer(BlockFactory.plugins(), MergeResolver.joget(assignment, appDef), preview, assignment, appDef);
    }

    /** The builder canvas: blocks with merge fields unfilled and App Resource images linked. */
    public static EmailTemplateRenderer canvasRenderer(AppDefinition appDef, String contextPath) {
        return renderer(appDef, null, true).withImages(new JogetResourceImages(appDef, contextPath));
    }

    /**
     * The builder's Preview, Send test and size meter: merge fields filled for the current user and, with a
     * test record ID, {@code #form.table.column#} from that record. Adds a warning listing merge fields that
     * came out empty.
     *
     * @param contextPath for Preview: App Resource images are linked under it. Null (Send test): embedded.
     */
    /** One element (a Repeat) as a canvas fragment, with real data as in Preview: the Test data record and variables. */
    public static EmailRenderResult renderBlockForTest(String elementJson, String templateProperties, AppDefinition appDef, String recordId, String contextPath,
            java.util.Map<String, String> testVariables) {
        TrackingResolver resolver = new TrackingResolver(RecordResolver.of(MergeResolver.joget(null, appDef), recordId));
        return new EmailTemplateRenderer(BlockFactory.plugins(), resolver, false, null, appDef)
                .withImages(new JogetResourceImages(appDef, contextPath)).withTestVariables(testVariables).renderBlock(elementJson, templateProperties);
    }

    /** @param testVariables the template variables' test values (the builder's Test data); may be null */
    public static EmailRenderResult renderForTest(String json, AppDefinition appDef, String recordId, String contextPath, java.util.Map<String, String> testVariables) {
        TrackingResolver resolver = new TrackingResolver(RecordResolver.of(MergeResolver.joget(null, appDef), recordId));
        EmailRenderResult result = new EmailTemplateRenderer(BlockFactory.plugins(), resolver, false, null, appDef)
                .withImages(new JogetResourceImages(appDef, contextPath)).withTestVariables(testVariables).render(json);
        return result.withWarning(resolver.warning());
    }
}
