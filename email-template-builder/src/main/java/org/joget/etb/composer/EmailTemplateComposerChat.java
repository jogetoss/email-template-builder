package org.joget.etb.composer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import java.util.HashMap;
import java.util.Map;
import org.joget.ai.agent.lib.composer.AbstractComposerChat;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.etb.Activator;
import org.joget.plugin.base.PluginManager;
import org.json.JSONObject;

/**
 * AI Composer for the Email Template Builder: agent-builder's own chat (same UI as its Form, List, UI and Theme
 * composers), injected into the email builder's design page, talking to this class over its WebSocket.
 *
 * <p>Needs agent-builder to export {@code org.joget.ai.agent.lib.composer}. The name ends in "ComposerChat", which
 * agent-builder's JS and discovery rely on.</p>
 */
public class EmailTemplateComposerChat extends AbstractComposerChat {

    /** Stable id of this composer (agent-builder keys its session and on/off settings by it). */
    public static final String COMPOSER_UUID = "0f1e9a52-6c1b-4d8e-9a57-e7a1b2c3d4e5";
    /** A plugin of the agent-builder bundle: its chat template, CSS and JS are served under this class's path. */
    static final String AGENT_BUILDER_CLASS = "org.joget.ai.agent.AgentBuilder";
    /** agent-builder's message bundle (composer labels, the "no AI service" card). */
    static final String AGENT_BUILDER_MESSAGES = "agent/messages/agentPlugins";

    public String getName() {
        return "AI Composer - Email Template Agent";
    }

    public String getVersion() {
        return Activator.VERSION;
    }

    public String getDescription() {
        return "Chat window for AI Composer in the Email Template Builder";
    }

    public String getLabel() {
        return "AI Composer - Email Template";
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return "[]";
    }

    public String getComposerUuid() {
        return COMPOSER_UUID;
    }

    public String[] getInjectUrlPatterns() {
        return new String[] {"/web/console/app/*/*/cbuilder/emailtemplate/design/*"};
    }

    protected String getPlatformVersion() {
        return "9";
    }

    protected String getMessagePath() {
        return AGENT_BUILDER_MESSAGES;
    }

    protected String getAgentBuilderClassName() {
        return AGENT_BUILDER_CLASS;
    }

    /** agent-builder names each composer from its message bundle, which has no key for this one. */
    @Override
    public String getAgentName() {
        return "Email Template Composer";
    }

    @Override
    protected String getBuilderLabel() {
        return "email template";
    }

    @Override
    protected String getRootElementClassName() {
        return "org.joget.etb.EmailTemplateBuilder";
    }

    @Override
    protected String getInputPlaceholder() {
        return "Describe the email to create, or select a block and say how to change it.";
    }

    /** Not offered to agent-builder's orchestrator yet: its composer lists are hard-coded. */
    @Override
    public boolean isOrchestratorEnabled() {
        return false;
    }

    /**
     * agent-builder's bridge between the chat and the builder, plus one thing: the sample values that come beside the
     * template ({@link EmailTemplateComposerTool#TEST_VALUES}) are taken off before it loads, and go to the builder's
     * Test data, as a picked starter's do. So Preview shows a realistic email, and the saved template stays clean.
     */
    @Override
    protected String getBridgeJs() {
        return "window.ComposerBridge = {\n"
                + "  getJson: function() {\n"
                + "    if (typeof CustomBuilder !== 'undefined' && typeof CustomBuilder.getJson === 'function') {\n"
                + "      try { return JSON.parse(CustomBuilder.getJson()); } catch(e) { return null; }\n"
                + "    }\n"
                + "    return null;\n"
                + "  },\n"
                + "  loadJson: function(json) {\n"
                + "    if (typeof CustomBuilder === 'undefined' || typeof CustomBuilder.loadJson !== 'function') { return; }\n"
                + "    var values = json ? json['" + EmailTemplateComposerTool.TEST_VALUES + "'] : null, template = json;\n"
                + "    if (values) {\n"
                + "      template = {};\n"
                + "      for (var k in json) { if (k !== '" + EmailTemplateComposerTool.TEST_VALUES + "') { template[k] = json[k]; } }\n"
                + "    }\n"
                + "    CustomBuilder.loadJson(JSON.stringify(template), true);\n"
                + "    if (values && window.EmailTemplateBuilder && typeof EmailTemplateBuilder.useComposerValues === 'function') {\n"
                + "      EmailTemplateBuilder.useComposerValues(values);\n"
                + "    }\n"
                + "  },\n"
                + "  getPaletteElements: function() {\n"
                + "    if (typeof CustomBuilder !== 'undefined' && CustomBuilder.paletteElements) { return CustomBuilder.paletteElements; }\n"
                + "    return null;\n"
                + "  }\n"
                + "};";
    }

    /**
     * The base class renders the chat template and loads its assets under this plugin's own class path, which
     * exists only in agent-builder's bundle. So: the same template data, rendered through agent-builder's class
     * (template, CSS and JS load from agent-builder), then the chat's own classPath, its WebSocket address, set
     * back to this class. Keep the data in step with {@code AbstractComposerChat.getHtml()}.
     */
    @Override
    public String getHtml(HttpServletRequest request) {
        if (!isComposerFeatureEnabled()) {
            return "";
        }
        PluginManager pm = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        Map<String, Object> data = new HashMap<>();
        data.put("contextPath", request.getContextPath());
        data.put("debug", "true".equalsIgnoreCase(System.getenv("debug")));
        data.put("platformVersion", getPlatformVersion());
        data.put("agentName", getAgentName());
        data.put("classPath", AGENT_BUILDER_CLASS);
        data.put("composerUuid", getComposerUuid());
        data.put("placeholder", getInputPlaceholder());
        data.put("builderLabel", getBuilderLabel());
        data.put("rootElementClassName", getRootElementClassName());
        data.put("bridgeJs", getBridgeJs());
        data.put("composerLabel", getComposerLabel());
        data.put("noAiServiceTitle", pm.processPluginTranslation("@@composer.noAiService.title@@", AGENT_BUILDER_CLASS, getMessagePath()));
        data.put("noAiServiceDescription", pm.processPluginTranslation("@@composer.noAiService.description@@", AGENT_BUILDER_CLASS, getMessagePath()));
        data.put("noAiServiceButton", pm.processPluginTranslation("@@composer.noAiService.button@@", AGENT_BUILDER_CLASS, getMessagePath()));
        data.put("isOrchestrator", String.valueOf(isOrchestrator()));
        data.put("showSelectedBar", String.valueOf(showSelectedElementBar()));
        String html = pm.getPluginFreeMarkerTemplate(data, AGENT_BUILDER_CLASS, getTemplatePath(), getMessagePath());
        return withOwnSocket(html, getClassName());
    }

    /** The template's one {@code "classPath": "…"} setting (its WebSocket address) pointed at {@code socketClass}. */
    static String withOwnSocket(String html, String socketClass) {
        if (html == null) {
            return "";
        }
        String from = "\"classPath\": \"" + AGENT_BUILDER_CLASS + "\"";
        if (!html.contains(from)) {
            LogUtil.warn(EmailTemplateComposerChat.class.getName(), "agent-builder's chat template changed: its \"classPath\" setting was not found,"
                    + " so the Email Template composer can't connect; the chat is left out.");
            return "";
        }
        return html.replace(from, "\"classPath\": \"" + socketClass + "\"");
    }

    @Override
    protected void handleMessage(String message, Session session) {
        try {
            JSONObject incoming = new JSONObject(message);
            String type = incoming.optString("type", "agentic_edit");
            // anyone may open a plugin's socket: a signed-in user only (each edit then needs the right to design its app)
            if (!EmailTemplateComposerService.signedIn()) {
                ComposerSocket.error(this, session, "Sign in to use the AI Composer.", null);
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Sign in first"));
                } catch (java.io.IOException e) {
                    LogUtil.debug(getClassName(), "Composer socket not closed: " + e.getMessage());
                }
                return;
            }
            switch (type) {
                case "init":
                    EmailTemplateComposerService.init(this, session, incoming);
                    break;
                case "abort_run":
                    EmailTemplateComposerService.abort(session);
                    break;
                case "agentic_edit":
                default:
                    if (ensureAiServiceConfigured(session, type)) {
                        EmailTemplateComposerService.edit(this, session, incoming, executorService);
                    }
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Email Template composer message failed");
            ComposerSocket.error(this, session, e.getMessage(), null);
        }
    }
}
