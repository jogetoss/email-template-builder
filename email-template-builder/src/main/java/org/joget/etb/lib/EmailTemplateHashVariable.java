package org.joget.etb.lib;

import java.util.ArrayList;
import java.util.Collection;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DefaultHashVariablePlugin;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.etb.Activator;
import org.joget.etb.EmailTemplateService;
import org.joget.etb.render.EmailRenderResult;
import org.joget.workflow.model.WorkflowAssignment;

/**
 * Renders a saved email template into any hash-variable field, typically the stock Email Tool's message.
 *
 * <ul>
 * <li>{@code #emailTemplate.ET-001?noescape#}: the HTML body. {@code ?noescape} is required: without a
 * format Joget sanitises the value and strips the Outlook markup, table attributes and head styles.
 * Tick the Email Tool's "HTML" option.</li>
 * <li>{@code #emailTemplate.ET-001.subject#}: the template's subject, for the Email Tool's subject field.</li>
 * <li>{@code #emailTemplate.ET-001.text#}: the plain-text version.</li>
 * </ul>
 * Merge fields inside the template are resolved here, with the process context Joget passes to the plugin.
 */
public class EmailTemplateHashVariable extends DefaultHashVariablePlugin {

    public String processHashVariable(String variableKey) {
        String id = variableKey;
        String part = "html";
        for (String p : new String[] {"subject", "text"}) {
            if (variableKey.endsWith("." + p)) {
                id = variableKey.substring(0, variableKey.length() - p.length() - 1);
                part = p;
            }
        }

        AppDefinition appDef = (AppDefinition) getProperty("appDefinition");
        if (appDef == null) {
            appDef = AppUtil.getCurrentAppDefinition();
        }
        WorkflowAssignment assignment = (WorkflowAssignment) getProperty("workflowAssignment");
        try {
            EmailRenderResult result = EmailTemplateService.render(id, appDef, assignment);
            if (result == null) {
                LogUtil.warn(getClassName(), "No email template \"" + id + "\" in app " + (appDef == null ? "?" : appDef.getAppId()));
                return null;
            }
            for (String w : result.getWarnings()) {
                LogUtil.warn(getClassName(), id + ": " + w);
            }
            if ("subject".equals(part)) {
                return result.getSubject();
            } else if ("text".equals(part)) {
                return result.getText();
            }
            return result.getHtml();
        } catch (EmailTemplateService.TemplateIncludesItself e) {
            // the inner reference only: the template around it still renders
            LogUtil.warn(getClassName(), e.getMessage() + "; that reference was left empty.");
            return "";
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Email template " + id + " failed to render");
            return null;
        }
    }

    public Collection<String> availableSyntax() {
        Collection<String> list = new ArrayList<String>();
        list.add(getPrefix() + ".TEMPLATE_ID?noescape");
        list.add(getPrefix() + ".TEMPLATE_ID.subject");
        list.add(getPrefix() + ".TEMPLATE_ID.text");
        return list;
    }

    public String getPrefix() {
        return "emailTemplate";
    }

    public String getName() {
        return "Email Template Hash Variable";
    }

    public String getLabel() {
        return "Email Template";
    }

    public String getDescription() {
        return "Renders a saved email template. Use #emailTemplate.ID?noescape# in the Email Tool message.";
    }

    public String getVersion() {
        return Activator.VERSION;
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return "";
    }
}
