package org.joget.etb;

import static org.junit.Assert.*;

import org.junit.Test;

/** Why the optional AI Composer is off, as the log says it: each case names what to install. */
public class ActivatorTest {

    @Test
    public void noAgentBuilder() {
        String reason = Activator.composerOffReason(new NoClassDefFoundError("org/joget/ai/agent/lib/composer/AbstractComposerChat"), c -> false);
        assertTrue(reason, reason.contains("agent-builder isn't installed"));
    }

    @Test
    public void agentBuilderWithoutTheComposerExport() {
        String reason = Activator.composerOffReason(new NoClassDefFoundError("org/joget/ai/agent/lib/composer/AbstractComposerChat"),
                Activator.AGENT_BUILDER_EXPORTED_CLASS::equals);
        assertTrue(reason, reason.contains("doesn't export org.joget.ai.agent.lib.composer"));
        assertFalse("restarting alone won't help: it says what to install", reason.contains("isn't installed"));
    }

    @Test
    public void agentBuilderOfAnotherVersion() {
        String reason = Activator.composerOffReason(new AbstractMethodError("getPlatformVersion"), c -> true);
        assertTrue(reason, reason.contains("doesn't match"));
        assertTrue(reason, reason.contains("AbstractMethodError"));
    }
}
