package org.joget.etb.render;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

/**
 * Golden snapshots: each src/test/resources/golden/NAME.json renders to NAME.mjml, NAME.html and
 * NAME.txt. Any change to a block's output, or an mjml4j upgrade, shows up as a diff here.
 * Regenerate after reviewing the change: mvn test -Dgolden.update=true
 */
public class GoldenTest {

    static final File DIR = new File("src/test/resources/golden");
    static final Map<String, String> VALUES = Map.of(
            "currentUser.firstName", "Alex",
            "form.request.title", "New laptop",
            "form.request.id", "REQ-1042",
            "form.request.note", "<b>urgent</b> & \"asap\"",
            "planner.boardName", "Cloud Board",
            "planner.date", "23 Sep 2026",
            // app-generated HTML, inserted by the Custom HTML block
            "planner.digest", "<p><a href=\"https://example.com/planner/card/42\" style=\"font-weight:bold;text-decoration:none;\">Decide on a Idea Now</a></p>"
                    + "<hr style=\"border:0;border-top:1px solid #999999;\"><p><strong>Admin Admin</strong> on <strong>23 Sep 2026 09:21 MYT:</strong></p>"
                    + "<ol><li><strong>Description</strong> from<p>Project Moon Lunar</p><p>to</p><p>Project Moon Lunar this is the content</p></li></ol>");

    @Test
    public void snapshots() throws Exception {
        boolean update = Boolean.getBoolean("golden.update");
        File[] fixtures = DIR.listFiles((d, n) -> n.endsWith(".json"));
        assertNotNull(fixtures);
        assertTrue("fixtures present", fixtures.length > 0);
        Arrays.sort(fixtures);
        for (File f : fixtures) {
            String name = f.getName().replace(".json", "");
            EmailRenderResult r = TestSupport.renderer(VALUES).render(read(f));
            assertTrue(name + " renders without warnings: " + r.getWarnings(), r.getWarnings().isEmpty());
            check(name + ".mjml", r.getMjml(), update);
            check(name + ".html", r.getHtml(), update);
            check(name + ".txt", "Subject: " + r.getSubject() + "\n\n" + r.getText() + "\n", update);
        }
    }

    static void check(String file, String actual, boolean update) throws Exception {
        File f = new File(DIR, file);
        if (update || !f.exists()) {
            Files.write(f.toPath(), actual.getBytes(StandardCharsets.UTF_8));
            if (!update) {
                fail("Created missing snapshot " + file + "; review it and re-run");
            }
            return;
        }
        assertEquals("snapshot " + file + " (regenerate with -Dgolden.update=true after review)", read(f), actual);
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
