package org.joget.etb.composer;

import static org.junit.Assert.*;

import java.util.Map;
import org.joget.ai.agent.model.AgentException;
import org.joget.ai.agent.model.ToolExecution;
import org.joget.etb.render.BlockFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The composer tool's changes to a working copy, without an LLM. */
public class EmailTemplateComposerToolTest {

    EmailTemplateComposerTool tool = new EmailTemplateComposerTool();
    JSONObject template;

    @Before
    public void open() {
        EmailTemplateComposerTool.blocks = BlockFactory.reflective();
        template = new JSONObject("{\"className\":\"org.joget.etb.EmailTemplateBuilder\",\"properties\":{\"id\":\"ET-1\",\"name\":\"Mine\"},\"elements\":[]}");
        EmailTemplateComposerTool.CURRENT.set(template);
        EmailTemplateComposerTool.RESULT.remove();
        EmailTemplateComposerTool.SAMPLES.remove();
        EmailTemplateComposerTool.RUN.set("RUN-test");
    }

    @After
    public void close() {
        EmailTemplateComposerTool.blocks = BlockFactory.plugins();
        EmailTemplateComposerTool.CURRENT.remove();
        EmailTemplateComposerTool.RESULT.remove();
        EmailTemplateComposerTool.RUN.remove();
        EmailTemplateComposerTool.SAMPLES.remove();
        EmailTemplateComposerTool.ABORTED.clear();
    }

    JSONObject call(String name, Map<String, Object> args) throws AgentException {
        return new JSONObject(tool.execute(null, null, new ToolExecution(name, new JSONObject(args))));
    }

    String idOfTop(int i) {
        return template.getJSONArray("elements").getJSONObject(i).getJSONObject("properties").getString("id");
    }

    @Test
    public void addsBlocksWithIdsAndChecksWhereTheyGo() throws Exception {
        JSONObject r = call("add_block", Map.of("block", "{\"className\":\"TextBlock\",\"properties\":{\"html\":\"<p>Hi</p>\"}}"));
        assertTrue(r.toString(), r.getBoolean("success"));
        assertEquals("org.joget.etb.lib.TextBlock", template.getJSONArray("elements").getJSONObject(0).getString("className"));
        assertEquals(32, idOfTop(0).length());
        assertSame("the chat previews the working copy", template, EmailTemplateComposerTool.RESULT.get());

        JSONObject wrong = call("add_block", Map.of("block", "{\"className\":\"ColumnBlock\"}"));
        assertFalse(wrong.getBoolean("success"));
        assertTrue(wrong.getString("error"), wrong.getString("error").contains("can't go on the email itself"));
        JSONObject unknown = call("add_block", Map.of("block", "{\"className\":\"NopeBlock\"}"));
        assertTrue(unknown.getString("error").contains("Unknown block"));
        JSONObject noParent = call("add_block", Map.of("block", "{\"className\":\"TextBlock\"}", "parentId", "missing"));
        assertTrue(noParent.getString("error").contains("No block with id \"missing\""));
    }

    @Test
    public void aRowWithColumnsGoesInWholeAndChildrenAreChecked() throws Exception {
        String row = "{\"className\":\"RowBlock\",\"elements\":[{\"className\":\"ColumnBlock\",\"elements\":[{\"className\":\"ButtonBlock\",\"properties\":{\"label\":\"Go\"}}]}]}";
        assertTrue(call("add_block", Map.of("block", row)).getBoolean("success"));
        JSONObject column = template.getJSONArray("elements").getJSONObject(0).getJSONArray("elements").getJSONObject(0);
        assertFalse("children get ids too", column.getJSONObject("properties").getString("id").isEmpty());
        String badRow = "{\"className\":\"RowBlock\",\"elements\":[{\"className\":\"TextBlock\"}]}";
        assertTrue(call("add_block", Map.of("block", badRow)).getString("error").contains("can't go in a row"));
    }

    @Test
    public void updatesMovesAndRemoves() throws Exception {
        call("add_block", Map.of("block", "{\"className\":\"TextBlock\",\"properties\":{\"html\":\"<p>A</p>\"}}"));
        call("add_block", Map.of("block", "{\"className\":\"ButtonBlock\",\"properties\":{\"label\":\"B\"}}"));
        String a = idOfTop(0);
        String b = idOfTop(1);
        call("update_block", Map.of("id", a, "properties", "{\"html\":\"<p>A2</p>\",\"color\":null,\"id\":\"hijack\"}"));
        JSONObject props = template.getJSONArray("elements").getJSONObject(0).getJSONObject("properties");
        assertEquals("<p>A2</p>", props.getString("html"));
        assertEquals("the id never changes", a, props.getString("id"));
        call("move_block", Map.of("id", b, "index", 0));
        assertEquals(b, idOfTop(0));
        call("remove_block", Map.of("id", a));
        assertEquals(1, template.getJSONArray("elements").length());
        assertFalse(call("remove_block", Map.of("id", "gone")).getBoolean("success"));
    }

    @Test
    public void startersAndComponentsAreBuildingBlocks() throws Exception {
        assertTrue(call("use_starter", Map.of("id", "notification")).getBoolean("success"));
        assertEquals("the template keeps its id and name", "ET-1", template.getJSONObject("properties").getString("id"));
        assertEquals("Mine", template.getJSONObject("properties").getString("name"));
        assertTrue(template.getJSONObject("properties").getString("subject").contains("#form.request.title#"));
        int before = template.getJSONArray("elements").length();
        assertTrue(call("insert_component", Map.of("id", "callout-note")).getBoolean("success"));
        JSONArray els = template.getJSONArray("elements");
        assertEquals(before + 1, els.length());
        assertTrue("inside the card: before the footer under it", els.getJSONObject(els.length() - 1).getString("className").endsWith("OutsideBlock"));
        assertTrue(els.getJSONObject(els.length() - 2).getString("className").endsWith("CalloutBlock"));
        assertFalse(call("use_starter", Map.of("id", "../x")).getBoolean("success"));
    }

    @Test
    public void setBlocksAndPropertiesThenCheck() throws Exception {
        call("set_template_properties", Map.of("properties", "{\"subject\":\"Hello #currentUser.firstName#\",\"linkColor\":\"#2563eb\",\"id\":\"x\"}"));
        assertEquals("ET-1", template.getJSONObject("properties").getString("id"));
        call("set_blocks", Map.of("blocks", "[{\"className\":\"TextBlock\",\"properties\":{\"html\":\"<p>Hi {var.who}</p>\"}},"
                + "{\"className\":\"ButtonBlock\",\"properties\":{\"label\":\"Open\",\"href\":\"javascript:alert(1)\"}}]"));
        JSONObject check = call("check_email", Map.of());
        String problems = check.getJSONArray("problems").toString();
        assertTrue(problems, problems.contains("Blocked link"));
        assertTrue(problems, problems.contains("{var.who}"));
        assertTrue(check.getInt("sizeKb") >= 0);
    }

    @Test
    public void notJsonIsExplainedAndAStoppedRunEnds() throws Exception {
        assertTrue(call("add_block", Map.of("block", "{not json")).getString("error").contains("not valid JSON"));
        EmailTemplateComposerTool.ABORTED.add("RUN-test");
        try {
            call("get_template", Map.of());
            fail("a stopped run ends at its next step");
        } catch (AgentException expected) {
            assertTrue(expected.getMessage().contains("Stopped"));
        }
    }

    @Test
    public void theChatConnectsToThisComposerAndTheSelectionIsTheRealBlock() {
        String html = "<script>var o = {\"classPath\": \"" + EmailTemplateComposerChat.AGENT_BUILDER_CLASS + "\", \"x\": 1};</script>"
                + "<link href=\"/jw/plugin/" + EmailTemplateComposerChat.AGENT_BUILDER_CLASS + "/agent/feature/composer/ComposerChat.css\">";
        String out = EmailTemplateComposerChat.withOwnSocket(html, "org.joget.etb.composer.EmailTemplateComposerChat");
        assertTrue(out.contains("\"classPath\": \"org.joget.etb.composer.EmailTemplateComposerChat\""));
        assertTrue("assets still load from agent-builder", out.contains("/plugin/" + EmailTemplateComposerChat.AGENT_BUILDER_CLASS + "/agent/"));
        assertEquals("a changed template: no chat rather than a wrong socket", "", EmailTemplateComposerChat.withOwnSocket("<p>other</p>", "x"));

        JSONObject t = new JSONObject("{\"elements\":[{\"className\":\"org.joget.etb.lib.TextBlock\",\"properties\":{\"id\":\"A\",\"html\":\"<p>real</p>\"}}]}");
        JSONObject sel = EmailTemplateComposerService.selected(t, new JSONObject("{\"id\":\"A\",\"properties\":{\"html\":\"<p>default</p>\"}}"));
        assertEquals("<p>real</p>", sel.getJSONObject("properties").getString("html"));
        assertNull(EmailTemplateComposerService.selected(t, new JSONObject("{\"id\":\"nope\"}")));
        assertTrue(EmailComposerPrompts.task("Make it blue", t, sel, false).contains("SELECTED BLOCK"));
    }

    @Test
    public void aVariableUsedAsALinkIsNotReportedAsBlocked() throws Exception {
        call("set_template_properties", Map.of("properties", "{\"subject\":\"s\",\"variables\":[{\"name\":\"trackingUrl\",\"description\":\"d\"}]}"));
        call("add_block", Map.of("block", "{\"className\":\"ButtonBlock\",\"properties\":{\"label\":\"Track\",\"href\":\"{var.trackingUrl}\"}}"));
        JSONObject check = call("check_email", Map.of());
        assertEquals(check.toString(), 0, check.getJSONArray("problems").length());
    }

    @Test
    public void aLongResultStaysValidJsonInTheTrace() {
        String small = new JSONObject().put("success", true).put("message", "ok").toString();
        assertSame(small, EmailTemplateComposerTool.traceResponse(small));
        StringBuilder big = new StringBuilder("{\"success\":true,\"template\":\"");
        for (int i = 0; i < 5000; i++) {
            big.append('x');
        }
        String result = big.append("\"}").toString();
        String trace = EmailTemplateComposerTool.traceResponse(result);
        // AgentTraceEvent writes it unquoted: it must parse, or the chat loses the step and its preview
        JSONObject t = new JSONObject(trace);
        assertTrue(t.getBoolean("success"));
        assertTrue(t.getBoolean("truncated"));
        assertEquals(result.length(), t.getInt("length"));
        assertTrue(trace.length() < result.length());
        // a failure stays a failure
        assertFalse(new JSONObject(EmailTemplateComposerTool.traceResponse(result.replace("\"success\":true", "\"success\":false"))).getBoolean("success"));
    }

    @Test
    public void onlyChangesAreCounted() throws Exception {
        EmailTemplateComposerTool.CHANGES.set(new int[1]);
        try {
            call("get_template", Map.of());
            call("check_email", Map.of());
            assertEquals("reading is not a change", 0, EmailTemplateComposerTool.changes());
            call("add_block", Map.of("block", "{\"className\":\"TextBlock\",\"properties\":{\"html\":\"<p>Hi</p>\"}}"));
            call("get_template", Map.of());
            assertEquals(1, EmailTemplateComposerTool.changes());
            call("set_template_properties", Map.of("properties", "{\"subject\":\"s\"}"));
            assertEquals(2, EmailTemplateComposerTool.changes());
        } finally {
            EmailTemplateComposerTool.CHANGES.remove();
        }
        assertEquals("no run, no changes", 0, EmailTemplateComposerTool.changes());
    }

    @Test
    public void whoMayDesignTheApp() {
        Map<String, Boolean> apps = Map.of("hr", true, "crm", false);
        // no anonymous user, whatever the session says
        assertFalse(EmailTemplateComposerService.mayDesign(true, false, "roleAnonymous", apps, "roleAnonymous", "hr"));
        assertFalse(EmailTemplateComposerService.mayDesign(false, false, null, apps, null, "hr"));
        // an admin designs every app
        assertTrue(EmailTemplateComposerService.mayDesign(false, true, "admin", null, null, "anything"));
        // an App Designer: the apps DX found they may edit, for this same user
        assertTrue(EmailTemplateComposerService.mayDesign(false, false, "mei", apps, "mei", "hr"));
        assertFalse(EmailTemplateComposerService.mayDesign(false, false, "mei", apps, "mei", "crm"));
        assertFalse(EmailTemplateComposerService.mayDesign(false, false, "mei", apps, "mei", "finance"));
        // an answer cached for someone else, no answer, or no app: no
        assertFalse(EmailTemplateComposerService.mayDesign(false, false, "mei", apps, "arjun", "hr"));
        assertFalse(EmailTemplateComposerService.mayDesign(false, false, "mei", null, null, "hr"));
        assertFalse(EmailTemplateComposerService.mayDesign(false, false, "mei", apps, "mei", ""));
    }

    @Test
    public void theRunIsTheRequestersOnItsOwnThread() throws Exception {
        org.joget.workflow.model.service.WorkflowUserManager wum = new org.joget.workflow.model.service.WorkflowUserManager();
        String[] seen = new String[3];
        // DX 9.0 and 9.1.1.1: the PluginThread brings no user, so the run sets it and clears it after
        Thread fresh = new Thread(() -> {
            EmailTemplateComposerService.asUser(wum, "mei", () -> seen[0] = wum.getCurrentThreadUser()).run();
            seen[1] = String.valueOf(wum.getCurrentThreadUser());
        });
        fresh.start();
        fresh.join();
        assertEquals("mei", seen[0]);
        assertEquals("cleared after the run", "null", seen[1]);
        // DX 9.1 and later: the platform restored the user already; the run leaves it to the platform to clear
        Thread restored = new Thread(() -> {
            wum.setCurrentThreadUser("mei");
            EmailTemplateComposerService.asUser(wum, "mei", () -> { }).run();
            seen[2] = wum.getCurrentThreadUser();
            wum.clearCurrentThreadUser();
        });
        restored.start();
        restored.join();
        assertEquals("mei", seen[2]);
    }

    @Test
    public void stepsAreNamedForTheChat() {
        assertEquals("Adding the \"callout-note\" component", EmailTemplateComposerTool.stepLabel("insert_component", new JSONObject().put("id", "callout-note")));
        assertEquals("Checking the email", EmailTemplateComposerTool.stepLabel("check_email", new JSONObject()));
    }

    @Test
    public void aFieldDrivenStepsBlockIsNotAProblem() throws Exception {
        call("set_template_properties", Map.of("properties", "{\"subject\":\"s\"}"));
        call("add_block", Map.of("block", "{\"className\":\"StepsBlock\",\"properties\":{\"current\":\"#form.leave.status#\",\"previewStep\":\"2\","
                + "\"steps\":[{\"label\":\"A\",\"matches\":\"\"},{\"label\":\"B\",\"matches\":\"\"}]}}"));
        assertEquals(0, call("check_email", Map.of()).getJSONArray("problems").length());
    }

    @Test
    public void sampleValuesStayOutOfTheTemplateAndGoToTestData() throws Exception {
        call("set_template_properties", Map.of("properties", "{\"subject\":\"Results for {var.quarter}\",\"variables\":["
                + "{\"name\":\"quarter\",\"description\":\"The quarter\",\"sample\":\"Q3 2026\"},{\"name\":\"owner\",\"description\":\"No sample\"}]}"));
        JSONArray vars = template.getJSONObject("properties").getJSONArray("variables");
        assertFalse("the saved template keeps name and description only", vars.getJSONObject(0).has("sample"));
        assertEquals("The quarter", vars.getJSONObject(0).getString("description"));

        JSONObject forBuilder = EmailTemplateComposerTool.forBuilder(template);
        assertEquals("Q3 2026", forBuilder.getJSONObject(EmailTemplateComposerTool.TEST_VALUES).getString("quarter"));
        assertFalse(forBuilder.getJSONObject(EmailTemplateComposerTool.TEST_VALUES).has("owner"));
        assertFalse("the working copy itself never carries them", template.has(EmailTemplateComposerTool.TEST_VALUES));
        assertEquals("Q3 2026", call("get_template", Map.of()).getJSONObject("variableSamples").getString("quarter"));

        EmailTemplateComposerTool.SAMPLES.remove();
        assertSame("no samples: the template as it is", template, EmailTemplateComposerTool.forBuilder(template));
    }

    @Test
    public void aStarterBringsItsGallerySampleValues() throws Exception {
        call("use_starter", Map.of("id", "kpi"));
        JSONObject values = EmailTemplateComposerTool.forBuilder(template).getJSONObject(EmailTemplateComposerTool.TEST_VALUES);
        for (String name : new String[] {"monthName", "tasksDone", "onTime", "overdue"}) {
            assertFalse(name, values.optString(name).isEmpty());
        }
    }

    @Test
    public void theBridgeTakesTheSampleValuesOffBeforeTheTemplateLoads() {
        String bridge = new EmailTemplateComposerChat().getBridgeJs();
        assertTrue(bridge.contains("window.ComposerBridge"));
        assertTrue(bridge.contains("k !== '" + EmailTemplateComposerTool.TEST_VALUES + "'"));
        assertTrue(bridge.contains("EmailTemplateBuilder.useComposerValues(values)"));
        assertTrue("then loads it as agent-builder's bridge does", bridge.contains("CustomBuilder.loadJson(JSON.stringify(template), true)"));
    }

    @Test
    public void layoutsThatWontFitAPhoneAreProblems() {
        JSONObject t = new JSONObject("{\"elements\":[{\"className\":\"org.joget.etb.lib.TextBlock\",\"properties\":{\"html\":\"<p><strong>SCHEDULED<br>MAINTENANCE</strong></p>\","
                + "\"fontSize\":\"44px\",\"padding\":\"0px 32px\"}},"
                + "{\"className\":\"org.joget.etb.lib.StatTilesBlock\",\"properties\":{\"keepSideBySide\":\"true\",\"tiles\":[{\"number\":\"112%\"},{\"number\":\"$2.4M\"},{\"number\":\"38\"}]}},"
                + "{\"className\":\"org.joget.etb.lib.RowBlock\",\"properties\":{\"keepSideBySide\":\"true\"},\"elements\":["
                + "{\"className\":\"org.joget.etb.lib.ColumnBlock\",\"elements\":[{\"className\":\"org.joget.etb.lib.TextBlock\",\"properties\":{\"html\":\"<p>A sentence that is long enough</p>\"}}]},"
                + "{\"className\":\"org.joget.etb.lib.ColumnBlock\"},{\"className\":\"org.joget.etb.lib.ColumnBlock\"}]}]}");
        String problems = String.join("\n", EmailTemplateComposerTool.phoneProblems(t));
        assertTrue(problems, problems.contains("\"MAINTENANCE\""));
        assertTrue(problems, problems.contains("\"$2.4M\""));
        assertTrue(problems, problems.contains("3 columns of text"));

        t.getJSONArray("elements").getJSONObject(0).getJSONObject("properties").put("fontSize", "36px");
        assertFalse("36px fits", String.join("\n", EmailTemplateComposerTool.phoneProblems(t)).contains("MAINTENANCE"));
    }

    @Test
    public void everyStarterFitsAPhone() {
        org.joget.etb.StarterCatalog starters = org.joget.etb.StarterCatalog.starters();
        for (org.joget.etb.StarterCatalog.Starter s : starters.getStarters()) {
            assertEquals(s.id, java.util.Collections.emptyList(), EmailTemplateComposerTool.phoneProblems(new JSONObject(starters.load(s.id))));
        }
    }
}
