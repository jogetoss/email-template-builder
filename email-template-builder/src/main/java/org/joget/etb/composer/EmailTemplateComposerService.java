package org.joget.etb.composer;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.Session;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joget.ai.agent.core.AgentFactory;
import org.joget.ai.agent.lib.composer.AbstractComposerChat;
import org.joget.ai.agent.model.AgentComponent;
import org.joget.ai.agent.model.AgentLLMAbstract;
import org.joget.ai.agent.model.AgentPromptAbstract;
import org.joget.ai.agent.model.AgentToolAbstract;
import org.joget.ai.agent.model.Response;
import org.joget.ai.agent.model.SystemAgentAbstract;
import org.joget.ai.agent.model.SystemAgentRequest;
import org.joget.ai.config.config.LLMConfig;
import org.joget.ai.config.llm.LLMConfigFactory;
import org.joget.ai.config.utils.CredentialUtil;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One chat turn of the Email Template composer: the user's request, the template open in the builder (and the
 * selected block, if any) go to agent-builder's agent engine with {@link EmailTemplateComposerTool}; the chat gets
 * the steps as they run, a preview after each change, and the finished template to apply or cancel.
 */
final class EmailTemplateComposerService {

    private EmailTemplateComposerService() {
    }

    /**
     * The task, run as this user. A {@link PluginThread} restores the submitting thread's user itself from DX 9.1
     * (wrapWithCurrentUserIdentity); before that it carries only the tenant profile, so the user is set here, and
     * cleared after, only when the thread doesn't already have it.
     */
    static Runnable asUser(WorkflowUserManager wum, String username, Runnable task) {
        return () -> {
            boolean set = username != null && !username.equals(wum.getCurrentThreadUser());
            if (set) {
                wum.setCurrentThreadUser(username);
            }
            try {
                task.run();
            } finally {
                if (set) {
                    wum.clearCurrentThreadUser();
                }
            }
        };
    }

    private static final class EmailTemplateAgent extends SystemAgentAbstract {
        @Override
        protected String getAgentName() {
            return "Email Template Composer Agent";
        }
    }

    private static WorkflowUserManager users() {
        return (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
    }

    /**
     * The composer renders with the app's data and spends the organisation's AI credits. DX lets anyone open a
     * plugin's WebSocket and agent-builder's base chat checks no role, so the chat checks every message on the
     * WebSocket's thread, which has the HTTP session's security context: no anonymous user at all.
     */
    static boolean signedIn() {
        return !users().isCurrentUserAnonymous();
    }

    /** DX's cache, in the HTTP session, of the apps the user may edit (AppUtil.isAppEditableByCurrentUser fills it). */
    static final String EDITABLE_APPS = "USER_EDITABLE_APPS";
    static final String EDITABLE_APPS_USER = "USER_EDITABLE_APPS_USERNAME";

    /**
     * Whether the user may design this app, as App Composer lets them: an admin, or an App Designer or app creator of
     * that app. DX's own check (AppUtil.isAppEditableByCurrentUser) needs an HTTP request, which a WebSocket message
     * hasn't; it caches its answer in the HTTP session when the user opens the app's builder (as they did to reach
     * this chat), and the WebSocket keeps that session: its answer for this user and this app is the one used.
     */
    static boolean mayDesign(Session session, String appId) {
        WorkflowUserManager wum = users();
        Object apps = null;
        Object cachedUser = null;
        try {
            Object http = session.getUserProperties().get(HttpSession.class.getName());
            if (http instanceof HttpSession) {
                apps = ((HttpSession) http).getAttribute(EDITABLE_APPS);
                cachedUser = ((HttpSession) http).getAttribute(EDITABLE_APPS_USER);
            }
        } catch (IllegalStateException e) {
            // the HTTP session has ended: no cached answer
        }
        return mayDesign(wum.isCurrentUserAnonymous(), wum.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN), wum.getCurrentUsername(), apps, cachedUser, appId);
    }

    /** {@link #mayDesign(Session, String)} on what it read: no anonymous user; an admin; else DX's answer for this user and app. */
    static boolean mayDesign(boolean anonymous, boolean admin, String username, Object editableApps, Object editableUser, String appId) {
        if (anonymous || username == null) {
            return false;
        }
        if (admin) {
            return true;
        }
        return appId != null && !appId.isEmpty() && username.equals(editableUser) && editableApps instanceof Map
                && Boolean.TRUE.equals(((Map<?, ?>) editableApps).get(appId));
    }

    static void init(Object caller, Session session, JSONObject message) {
        abort(session);
        if (!CredentialUtil.hasActiveAiService()) {
            ComposerSocket.error(caller, session, "No AI services configured.", AbstractComposerChat.ERROR_CODE_AI_SERVICES_NOT_CONFIGURED);
            return;
        }
        ComposerSocket.status(caller, session, "Email Template composer ready.");
        if (!message.optBoolean("reconnect", false)) {
            ComposerSocket.chat(caller, session, "Hi! Describe the email you want and I'll build it, starting from a ready-made design where one fits."
                    + " Or select a block on the canvas and tell me how to change it."
                    + " Note: your messages, any images you attach and the email template go to the AI service. Your Test data doesn't.");
        }
    }

    /** Stops the running turn at its next step (the model's current reply still completes). */
    static void abort(Session session) {
        Object run = session.getUserProperties().remove("etbRunId");
        if (run != null) {
            EmailTemplateComposerTool.ABORTED.add(run.toString());
        }
    }

    static void edit(Object caller, Session session, JSONObject incoming) {
        JSONObject json = incoming.optJSONObject("json");
        if (json == null) {
            ComposerSocket.error(caller, session, "The email template could not be read from the builder.", null);
            return;
        }
        if (!mayDesign(session, incoming.optString("appId", ""))) {
            ComposerSocket.error(caller, session, "You can't design this app, so the AI Composer can't work on it.", null);
            return;
        }
        // one run per chat: a new request stops the one before it, so two never write to the same canvas
        abort(session);
        ComposerSocket.working(caller, session, "Working on your request...\n");
        String runId = "RUN-" + UUID.randomUUID();
        session.getUserProperties().put("etbRunId", runId);
        String composerClass = AbstractComposerChat.getActiveComposerClass(EmailTemplateComposerChat.class);
        // Its own PluginThread, made here on the socket's thread, not agent-builder's pooled executor: the platform
        // carries this thread's tenant profile (and, from DX 9.1, its user) to it, which DX Cloud refuses to let
        // plugin code do on a plain pooled thread. A reused pool thread would keep whichever tenant created it.
        Thread worker = new PluginThread(asUser(users(), users().getCurrentUsername(),
                AbstractComposerChat.withComposerContext(composerClass, () -> run(caller, session, incoming, json, runId))));
        worker.setName("EmailTemplateComposer-" + runId);
        worker.start();
    }

    private static void run(Object caller, Session session, JSONObject incoming, JSONObject original, String runId) {
        EmailTemplateAgent agent = new EmailTemplateAgent();
        UUID listener = null;
        List<File> images = new ArrayList<>();
        try {
            // who may design the app was checked on the socket's thread (edit), where the HTTP session is
            AppDefinition appDef = app(incoming.optString("appId", ""), incoming.optString("appVersion", ""));
            if (appDef == null) {
                ComposerSocket.failed(caller, session, "The app could not be found.");
                return;
            }
            AppUtil.setCurrentAppDefinition(appDef);
            JSONObject working = new JSONObject(original.toString());
            EmailTemplateComposerTool.ids(EmailTemplateComposerTool.elements(working));
            EmailTemplateComposerTool.CURRENT.set(working);
            EmailTemplateComposerTool.RESULT.remove();
            EmailTemplateComposerTool.CHANGES.set(new int[1]);
            EmailTemplateComposerTool.SAMPLES.set(new java.util.LinkedHashMap<>());
            EmailTemplateComposerTool.APP.set(appDef);
            EmailTemplateComposerTool.RUN.set(runId);
            final int[] previewed = {0};

            listener = agent.subscribe(runId, payload -> {
                try {
                    JSONObject trace = new JSONObject(payload);
                    ComposerSocket.send(caller, session, new JSONObject().put("type", "agentic_trace").put("runId", runId).put("payload", trace));
                    JSONObject now = EmailTemplateComposerTool.RESULT.get();
                    int changes = EmailTemplateComposerTool.changes();
                    if (now != null && "Tool".equals(trace.optString("type")) && "SUCCESS".equals(trace.optString("status")) && changes > previewed[0]) {
                        // live preview on the canvas after each change (not after a step that only read)
                        previewed[0] = changes;
                        ComposerSocket.send(caller, session, new JSONObject().put("type", "agentic_partial_update").put("json", EmailTemplateComposerTool.forBuilder(now))
                                .put("step", trace.optString("personalizedName", trace.optString("label", "step"))));
                    }
                } catch (Exception e) {
                    LogUtil.debug(EmailTemplateComposerService.class.getName(), "Trace not forwarded: " + e.getMessage());
                }
            });

            PluginManager pm = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
            AgentLLMAbstract llm = llm(pm, incoming.optString("selectedAiService"));
            List<AgentPromptAbstract> system = new ArrayList<>();
            system.add(AgentFactory.create(pm, AgentComponent.PersonaAgentPrompt, Map.of("role", "Joget Email Template Designer", "backstory", EmailComposerPrompts.SYSTEM)));

            List<AgentPromptAbstract> task = new ArrayList<>();
            JSONArray history = incoming.optJSONArray("history");
            for (int i = 0; history != null && i < history.length(); i++) {
                JSONObject turn = history.optJSONObject(i);
                String content = turn == null ? "" : turn.optString("content", "");
                if (!content.isEmpty() && ("user".equals(turn.optString("role")) || "assistant".equals(turn.optString("role")))) {
                    task.add(text(pm, "user".equals(turn.optString("role")) ? "USER" : "ASSISTANT", content));
                }
            }
            task.addAll(imagePrompts(pm, incoming, images));
            task.add(text(pm, "USER", EmailComposerPrompts.task(incoming.optString("message"), working, selected(working, incoming.optJSONObject("selectedElement")),
                    !images.isEmpty())));

            List<AgentToolAbstract> tools = new ArrayList<>();
            tools.add(new EmailTemplateComposerTool());
            SystemAgentRequest request = new SystemAgentRequest(llm, system, task, tools, Collections.emptyList(), false);
            request.setRunId(runId);
            Response response = agent.run(request, new HashMap<>());

            if (EmailTemplateComposerTool.ABORTED.remove(runId)) {
                stopped(caller, session, original);
                return;
            }
            JSONObject result = EmailTemplateComposerTool.RESULT.get();
            String reply = response == null || response.getContent() == null ? "" : response.getContent().trim();
            if (result == null) {
                // an answer without changes (a question, or advice)
                ComposerSocket.complete(caller, session, reply.isEmpty() ? "I made no changes." : reply, original, false);
                return;
            }
            ComposerSocket.complete(caller, session, reply.isEmpty() ? "The email is updated." : reply, EmailTemplateComposerTool.forBuilder(result), !result.similar(original));
        } catch (Exception e) {
            if (EmailTemplateComposerTool.ABORTED.remove(runId)) {
                stopped(caller, session, original);
                return;
            }
            LogUtil.error(EmailTemplateComposerService.class.getName(), e, "Email Template composer run failed");
            JSONObject partial = EmailTemplateComposerTool.RESULT.get();
            if (partial != null) {
                ComposerSocket.complete(caller, session, "I couldn't finish (" + e.getMessage() + "). The changes so far are ready to apply.",
                        EmailTemplateComposerTool.forBuilder(partial), true);
            } else {
                ComposerSocket.failed(caller, session, e.getMessage());
            }
        } finally {
            EmailTemplateComposerTool.CURRENT.remove();
            EmailTemplateComposerTool.RESULT.remove();
            EmailTemplateComposerTool.CHANGES.remove();
            EmailTemplateComposerTool.SAMPLES.remove();
            EmailTemplateComposerTool.APP.remove();
            EmailTemplateComposerTool.RUN.remove();
            if (listener != null) {
                try {
                    agent.unsubscribe(runId, listener);
                } catch (Exception ignore) {
                    // the run is over either way
                }
            }
            for (File f : images) {
                f.delete();
            }
            session.getUserProperties().remove("etbRunId", runId);
            // leave no app behind on the thread
            AppUtil.setCurrentAppDefinition(null);
        }
    }

    /**
     * Stopped by the user. With changes already previewed on the canvas, they are offered as the error path offers
     * them: Apply keeps them, Cancel puts the email back as it was (the chat only keeps its pre-run copy for that).
     */
    private static void stopped(Object caller, Session session, JSONObject original) {
        JSONObject partial = EmailTemplateComposerTool.RESULT.get();
        if (partial != null && !partial.similar(original)) {
            ComposerSocket.complete(caller, session, "Stopped. The changes so far are on the canvas: apply them, or cancel to undo them.",
                    EmailTemplateComposerTool.forBuilder(partial), true);
        } else {
            ComposerSocket.aborted(caller, session);
        }
    }

    private static AgentLLMAbstract llm(PluginManager pm, String service) throws Exception {
        LLMConfig config = LLMConfigFactory.createByServiceOrActive(service == null || service.isEmpty() ? null : service);
        Map<String, Object> props = new HashMap<>();
        props.put("apiKey", config.getApiKey());
        props.put("model", config.getModelName());
        props.put("apiUrl", config.getCustomEndpointUrl() == null || config.getCustomEndpointUrl().isEmpty() ? config.getApiEndpoint() : config.getCustomEndpointUrl());
        if (config.getAdditionalConfig() != null) {
            props.putAll(config.getAdditionalConfig());
        }
        props.put("maxTokens", "16000");
        return AgentFactory.createLLM(pm, config.getProviderName(), props);
    }

    private static AgentPromptAbstract text(PluginManager pm, String role, String prompt) {
        return AgentFactory.create(pm, AgentComponent.TextPromptAgentPrompt, Map.of("msgRole", role, "prompt", prompt));
    }

    /** Uploaded images (a mock-up, a screenshot of an email to copy) as file prompts. */
    private static List<AgentPromptAbstract> imagePrompts(PluginManager pm, JSONObject incoming, List<File> files) {
        List<AgentPromptAbstract> out = new ArrayList<>();
        JSONArray list = incoming.optJSONArray("images");
        if (list == null && !incoming.optString("imageBase64").isEmpty()) {
            list = new JSONArray().put(new JSONObject().put("base64", incoming.optString("imageBase64")).put("fileName", incoming.optString("imageFileName", "image.png")));
        }
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject img = list.optJSONObject(i);
            if (img == null || img.optString("base64").isEmpty()) {
                continue;
            }
            try {
                String name = img.optString("fileName", "image" + (i + 1) + ".png").replaceAll("[^A-Za-z0-9._-]", "_");
                String data = img.getString("base64");
                int comma = data.indexOf(',');
                File f = File.createTempFile("etb-composer-", "-" + name);
                Files.write(f.toPath(), Base64.getDecoder().decode(comma >= 0 ? data.substring(comma + 1) : data));
                files.add(f);
                out.add(AgentFactory.create(pm, AgentComponent.FileAgentPrompt, Map.of("source", "system", "fileType", "IMAGE", "filePath", f.getAbsolutePath(),
                        "purpose", "Image " + (i + 1) + " (" + name + ")", "msgRole", "USER", "appResorce", "", "formDefId", "", "fieldId", "", "url", "")));
            } catch (Exception e) {
                LogUtil.warn(EmailTemplateComposerService.class.getName(), "Image " + (i + 1) + " was left out: " + e.getMessage());
            }
        }
        return out;
    }

    /** The selected block as it is in the template (the chat sends the palette defaults, not its current values). */
    static JSONObject selected(JSONObject template, JSONObject selectedElement) {
        if (selectedElement == null) {
            return null;
        }
        String id = selectedElement.optString("id", "");
        if (id.isEmpty() && selectedElement.optJSONObject("properties") != null) {
            id = selectedElement.getJSONObject("properties").optString("id", "");
        }
        JSONObject block = id.isEmpty() ? null : EmailTemplateComposerTool.find(template, id);
        return block == null || EmailTemplateComposerTool.ROOT.equals(block.optString("className")) ? null : block;
    }

    /** The request's app, or null: no app id means no app (a worker thread has no current app to fall back on). */
    private static AppDefinition app(String appId, String version) {
        if (appId.isEmpty()) {
            return null;
        }
        try {
            AppDefinitionDao dao = (AppDefinitionDao) AppUtil.getApplicationContext().getBean("appDefinitionDao");
            return dao.loadVersion(appId, Long.valueOf(version.isEmpty() ? "1" : version));
        } catch (Exception e) {
            return null;
        }
    }
}
