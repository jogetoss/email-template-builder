package org.joget.etb.lib;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.mail.HtmlEmail;
import org.joget.etb.render.EmailRenderResult;
import org.junit.Test;

public class SendTemplatedEmailToolTest {

    /** The whole point of this tool over the stock Email Tool: a real multipart/alternative with both parts. */
    @Test
    public void composeBuildsMultipartAlternative() throws Exception {
        HtmlEmail email = new HtmlEmail();
        email.setHostName("localhost");
        email.setFrom("sender@example.com");
        email.addTo("to@example.com");
        EmailRenderResult r = new EmailRenderResult("Approved: New laptop", "<html><body><p>Hi <b>Alex</b></p></body></html>",
                "Hi Alex", "<mjml/>", 50, List.of());
        SendTemplatedEmailTool.compose(email, "Approved: New laptop", r);
        email.buildMimeMessage();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        email.getMimeMessage().writeTo(out);
        String mime = out.toString("UTF-8");
        assertTrue(mime, mime.contains("multipart/alternative"));
        assertTrue(mime.contains("text/plain"));
        assertTrue(mime.contains("text/html"));
        assertTrue(mime.contains("Hi Alex"));
        assertTrue(mime.contains("Subject: Approved: New laptop"));
    }

    @Test
    public void emptyTextPartIsSkipped() throws Exception {
        HtmlEmail email = new HtmlEmail();
        email.setHostName("localhost");
        email.setFrom("sender@example.com");
        email.addTo("to@example.com");
        SendTemplatedEmailTool.compose(email, "S", new EmailRenderResult("S", "<p>x</p>", "", "", 8, List.of()));
        email.buildMimeMessage();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        email.getMimeMessage().writeTo(out);
        assertFalse(out.toString("UTF-8").contains("text/plain"));
    }

    @Test
    public void smtpBlanksComeFromEmailToolDefaultsOwnValuesWin() {
        Map<String, Object> smtp = new HashMap<>();
        smtp.put("host", "");
        smtp.put("from", "own@example.com");
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("host", "smtp.example.com");
        defaults.put("port", "587");
        defaults.put("from", "default@example.com");
        defaults.put("message", "not an SMTP key");
        SendTemplatedEmailTool.fillBlanks(smtp, defaults);
        assertEquals("smtp.example.com", smtp.get("host"));
        assertEquals("587", smtp.get("port"));
        assertEquals("own@example.com", smtp.get("from"));
        assertFalse(smtp.containsKey("message"));
    }

    @Test
    public void testSubjectIsMarked() {
        assertEquals("[Test] Approved", SendTemplatedEmailTool.testSubject("Approved"));
        assertEquals("[Test] (no subject)", SendTemplatedEmailTool.testSubject(" "));
    }

    @Test
    public void appResourceImagesAreEmbeddedUnderTheirCid() throws Exception {
        java.io.File png = java.io.File.createTempFile("etb-logo", ".png");
        png.deleteOnExit();
        java.nio.file.Files.write(png.toPath(), new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
        HtmlEmail email = new HtmlEmail();
        email.setHostName("localhost");
        email.setFrom("sender@example.com");
        email.addTo("to@example.com");
        EmailRenderResult r = new EmailRenderResult("S", "<img src=\"cid:etb1-logo.png\">", "S", "", 30, List.of(), Map.of("etb1-logo.png", png));
        SendTemplatedEmailTool.compose(email, "S", r);
        email.buildMimeMessage();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        email.getMimeMessage().writeTo(out);
        String mime = out.toString("UTF-8");
        assertTrue(mime, mime.contains("multipart/related"));
        assertTrue(mime.contains("Content-ID: <etb1-logo.png>"));
        assertTrue(mime.contains("image/png"));
        assertTrue("text and html still alternatives", mime.contains("multipart/alternative"));
    }

}
