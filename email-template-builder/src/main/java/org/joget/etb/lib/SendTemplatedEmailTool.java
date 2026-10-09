package org.joget.etb.lib;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.joget.apps.app.lib.EmailTool;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PluginDefaultProperties;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.commons.util.StringUtil;
import org.joget.etb.Activator;
import org.joget.etb.EmailTemplateService;
import org.joget.etb.render.EmailRenderResult;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginException;
import org.joget.plugin.property.service.PropertyUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.util.WorkflowUtil;

/**
 * Sends a saved email template: subject, HTML and a plain-text alternative (multipart/alternative).
 *
 * Mirrors the built-in Email Tool (same properties, helpers and retry behaviour) and replaces its
 * Subject/Message/HTML fields with a template picker. SMTP: this tool's own fields, else the app's
 * Email Tool Plugin Default Properties, else the server's system SMTP settings (AppUtil.createEmail).
 * Attachments, embedded images (cid:) and ICS invites come from AppUtil.emailAttachment, as in the Email Tool.
 */
public class SendTemplatedEmailTool extends DefaultApplicationPlugin {

    static final String[] SMTP_KEYS = {"from", "host", "port", "security", "username", "password", "p12", "storepass", "alias", "retryCount", "retryInterval"};

    public String getName() {
        return "Send Templated Email";
    }

    public String getVersion() {
        return Activator.VERSION;
    }

    public String getDescription() {
        return "Sends an Email Template (HTML plus plain text, the template's subject) using the Email Tool's settings.";
    }

    public String getLabel() {
        return "Send Templated Email";
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/sendTemplatedEmailTool.json", null, true, Activator.MESSAGE_PATH);
    }

    @Override
    public String getPluginIcon() {
        return "<i class=\"las la-envelope-open-text\"></i>";
    }

    public Object execute(Map properties) {
        WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        AppDefinition appDef = (AppDefinition) properties.get("appDef");
        String formDataTable = (String) properties.get("formDataTable");
        String templateId = (String) properties.get("templateId");

        try {
            // the template's variables, as mapped here (merge fields resolved; inside an Iterator, {column} is already filled)
            Map<String, String> variables = variables(properties.get("variables"), formDataTable, wfAssignment);
            EmailRenderResult result = EmailTemplateService.renderForSending(templateId, appDef, wfAssignment, variables);
            if (result == null) {
                LogUtil.warn(getClassName(), "Email template \"" + templateId + "\" not found in app " + (appDef == null ? "?" : appDef.getAppId()) + "; nothing sent");
                return null;
            }
            for (String w : result.getWarnings()) {
                LogUtil.warn(getClassName(), templateId + ": " + w);
            }

            Map<String, Object> smtp = withEmailToolDefaults(properties, appDef);

            String subject = str(properties, "subject");
            subject = subject.trim().isEmpty() ? result.getSubject()
                    : fillVariables(WorkflowUtil.processVariable(subject, formDataTable, wfAssignment), variables).replaceAll("[\\r\\n]+", " ").trim();

            final HtmlEmail email = createEmail(smtp, wfAssignment, formDataTable);
            if (email == null) {
                return null;
            }

            String cc = str(properties, "cc");
            if (!cc.isEmpty()) {
                for (String address : AppUtil.getEmailList(null, cc, wfAssignment, appDef)) {
                    email.addCc(StringUtil.encodeEmail(address));
                }
            }
            String bcc = str(properties, "bcc");
            if (!bcc.isEmpty()) {
                for (String address : AppUtil.getEmailList(null, bcc, wfAssignment, appDef)) {
                    email.addBcc(StringUtil.encodeEmail(address));
                }
            }

            compose(email, subject, result);

            String toParticipantId = str(properties, "toParticipantId");
            String toSpecific = str(properties, "toSpecific");
            String to = "";
            if (!toParticipantId.trim().isEmpty() || !toSpecific.trim().isEmpty()) {
                Collection<String> tss = AppUtil.getEmailList(toParticipantId, toSpecific, wfAssignment, appDef);
                for (String address : tss) {
                    email.addTo(StringUtil.encodeEmail(address));
                    to += address + ", ";
                }
            } else {
                throw new PluginException("no email specified");
            }

            // attachments, embedded form images (rewritten to cid:), files and ICS: the Email Tool's own handling
            AppUtil.emailAttachment(properties, wfAssignment, appDef, email);

            send(email, to, str(smtp, "retryCount"), str(smtp, "retryInterval"));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Send Templated Email failed for template " + templateId);
        }
        return null;
    }

    static final java.util.regex.Pattern VAR_FIELD = java.util.regex.Pattern.compile("\\{var\\.([A-Za-z0-9_.]{1,64})(\\?raw)?\\}");

    /** The "Template variables" grid (variable, value) as name to value, merge fields resolved (not escaped: the template escapes). */
    static Map<String, String> variables(Object grid, String formDataTable, WorkflowAssignment wfAssignment) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (grid instanceof Object[]) {
            for (Object row : (Object[]) grid) {
                if (row instanceof Map) {
                    Object name = ((Map<?, ?>) row).get("name");
                    Object value = ((Map<?, ?>) row).get("value");
                    String n = name == null ? "" : name.toString().trim();
                    if (!n.isEmpty()) {
                        String v = value == null ? "" : value.toString();
                        out.put(n, wfAssignment == null && formDataTable == null ? v : WorkflowUtil.processVariable(v, formDataTable, wfAssignment));
                    }
                }
            }
        }
        return out;
    }

    /** {var.NAME} in the tool's own subject override (plain text; unknown names stay as written). */
    static String fillVariables(String text, Map<String, String> variables) {
        if (text == null || variables == null || !text.contains("{var.")) {
            return text;
        }
        java.util.regex.Matcher m = VAR_FIELD.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = variables.get(m.group(1));
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(v == null ? m.group() : v));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** An SMTP connection from these settings (hash variables resolved); null when no SMTP is configured anywhere. */
    static HtmlEmail createEmail(Map<String, Object> smtp, WorkflowAssignment wfAssignment, String formDataTable) throws EmailException {
        return AppUtil.createEmail(
                AppUtil.processHashVariable(str(smtp, "host"), wfAssignment, null, null),
                AppUtil.processHashVariable(str(smtp, "port"), wfAssignment, null, null),
                AppUtil.processHashVariable(str(smtp, "security"), wfAssignment, null, null),
                AppUtil.processHashVariable(str(smtp, "username"), wfAssignment, null, null),
                AppUtil.processHashVariable(str(smtp, "password"), wfAssignment, null, null),
                WorkflowUtil.processVariable(str(smtp, "from"), formDataTable, wfAssignment),
                str(smtp, "p12"), str(smtp, "storepass"), str(smtp, "alias"));
    }

    /**
     * The builder's "Send test": the app's Email Tool SMTP defaults, else the system SMTP. Sends in the request
     * thread (no retries) so the builder can show an SMTP error.
     */
    public static void sendTest(EmailRenderResult result, AppDefinition appDef, Collection<String> to) throws EmailException {
        HtmlEmail email = createEmail(withEmailToolDefaults(new HashMap<String, Object>(), appDef), null, null);
        if (email == null) {
            throw new EmailException("SMTP is not configured. Set the SMTP settings in the app's Email Tool default properties, or the server's system settings.");
        }
        compose(email, testSubject(result.getSubject()), result);
        for (String address : to) {
            email.addTo(StringUtil.encodeEmail(address));
        }
        email.send();
    }

    static String testSubject(String subject) {
        return "[Test] " + (subject == null || subject.trim().isEmpty() ? "(no subject)" : subject);
    }

    /** Subject, HTML and the plain-text alternative. Both bodies set means commons-email builds multipart/alternative. */
    public static void compose(HtmlEmail email, String subject, EmailRenderResult result) throws EmailException {
        email.setSubject(subject);
        email.setCharset("UTF-8");
        email.setHtmlMsg(result.getHtml());
        if (result.getText() != null && !result.getText().isEmpty()) {
            email.setTextMsg(result.getText());
        }
        // App Resource images, referenced in the HTML as cid:KEY. embed(File, cid): the DataSource overloads need
        // javax.activation, which DX 9 does not export to plugins
        for (Map.Entry<String, File> image : result.getInlineImages().entrySet()) {
            email.embed(image.getValue(), image.getKey());
        }
    }

    /** Sends on a background thread with the Email Tool's retry policy. */
    static void send(final HtmlEmail email, final String to, String retryCount, String retryInterval) {
        final int count = AppUtil.getEmailRetryCount(retryCount);
        final long interval = AppUtil.getEmailRetryInterval(retryInterval);
        final String cls = SendTemplatedEmailTool.class.getName();
        Thread thread = new PluginThread(new Runnable() {
            public void run() {
                try {
                    LogUtil.info(cls, "Sending templated email to=" + to + " subject=" + email.getSubject());
                    email.send();
                    LogUtil.info(cls, "Sending templated email completed for subject=" + email.getSubject());
                } catch (EmailException ex) {
                    LogUtil.error(cls, ex, "");
                    for (int retry = 1; retry <= count; retry++) {
                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        try {
                            LogUtil.info(cls, "Attempt " + retry + ": sending templated email to=" + to);
                            email.sendMimeMessage();
                            LogUtil.info(cls, "Attempt " + retry + ": completed for subject=" + email.getSubject());
                            return;
                        } catch (EmailException ex2) {
                            LogUtil.error(cls, ex2, "Attempt " + retry + " failed");
                        }
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /** This tool's SMTP fields, with blanks filled from the app's Email Tool Plugin Default Properties (only when host is blank). */
    static Map<String, Object> withEmailToolDefaults(Map properties, AppDefinition appDef) {
        Map<String, Object> smtp = new HashMap<>();
        for (String k : SMTP_KEYS) {
            smtp.put(k, properties.get(k));
        }
        if (str(smtp, "host").trim().isEmpty() && appDef != null) {
            PluginDefaultProperties d = AppPluginUtil.getPluginDefaultProperties(EmailTool.class.getName(), appDef);
            if (d != null && d.getPluginProperties() != null && !d.getPluginProperties().trim().isEmpty()) {
                fillBlanks(smtp, PropertyUtil.getPropertiesValueFromJson(d.getPluginProperties()));
            }
        }
        return smtp;
    }

    /** Copy SMTP values from defaults wherever the tool's own value is blank. */
    static void fillBlanks(Map<String, Object> smtp, Map<String, Object> defaults) {
        if (defaults == null) {
            return;
        }
        for (String k : SMTP_KEYS) {
            if (str(smtp, k).trim().isEmpty() && defaults.get(k) != null) {
                smtp.put(k, defaults.get(k));
            }
        }
    }

    static String str(Map m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }
}
