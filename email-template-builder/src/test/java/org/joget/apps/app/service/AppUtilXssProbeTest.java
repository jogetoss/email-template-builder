package org.joget.apps.app.service;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Pins the DX 9 behaviour the hash-variable integration depends on: with no format, AppUtil sanitises the
 * value (stripping Outlook markup), so the token must be #emailTemplate.ID?noescape#. If Joget changes
 * this, the docs and the copy-token button need revisiting.
 */
public class AppUtilXssProbeTest {

    @Test
    public void noFormatIsSanitised() {
        assertTrue(AppUtil.requiredXssPrevention(""));
    }

    @Test
    public void noescapeIsNot() {
        assertFalse(AppUtil.requiredXssPrevention("noescape"));
    }
}
