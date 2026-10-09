package org.joget.etb.composer;

import jakarta.websocket.Session;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Messages to agent-builder's chat UI, in the format its ComposerChat.js reads. agent-builder's own sender
 * (org.joget.ai.agent.utils.WebsocketUtil) is not exported, so this mirrors the few messages the email composer sends.
 */
final class ComposerSocket {

    private ComposerSocket() {
    }

    static void send(Object caller, Session session, JSONObject message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                session.getBasicRemote().sendText(message.toString());
            } catch (Exception e) {
                // the browser already closed the connection (tab closed, network gone)
                LogUtil.debug(caller.getClass().getName(), "WebSocket send skipped for session " + session.getId() + ": " + e.getMessage());
            }
        }
    }

    static void status(Object caller, Session session, String message) {
        send(caller, session, new JSONObject().put("type", "status").put("message", message));
    }

    static void chat(Object caller, Session session, String content) {
        send(caller, session, new JSONObject().put("type", "chat_response").put("content", content));
    }

    static void error(Object caller, Session session, String message, String errorCode) {
        JSONObject out = new JSONObject().put("type", "error").put("message", message == null ? "Something went wrong." : message);
        if (errorCode != null) {
            out.put("errorCode", errorCode);
        }
        send(caller, session, out);
    }

    static void working(Object caller, Session session, String text) {
        send(caller, session, new JSONObject().put("type", "agentic_text_delta").put("text", text));
    }

    /** The result: the chat shows the message and, when the design changed, Apply / Cancel. */
    static void complete(Object caller, Session session, String message, JSONObject json, boolean changed) {
        JSONObject out = new JSONObject().put("type", "agentic_complete").put("message", message == null ? "" : message)
                .put("json", json == null ? new JSONObject() : json);
        if (changed) {
            out.put("actions", new JSONArray().put("apply").put("cancel"));
        }
        send(caller, session, out);
    }

    static void aborted(Object caller, Session session) {
        send(caller, session, new JSONObject().put("type", "agentic_aborted"));
    }

    static void failed(Object caller, Session session, String message) {
        send(caller, session, new JSONObject().put("type", "agentic_error").put("message", message == null ? "The edit failed." : message));
    }
}
