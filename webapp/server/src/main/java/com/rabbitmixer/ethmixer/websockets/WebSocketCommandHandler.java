package com.rabbitmixer.ethmixer.websockets;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

@WebSocket
public class WebSocketCommandHandler {

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        ClientNotifier.getInstance().removeSession(session);
        System.out.println("Close: statusCode=" + statusCode + ", reason=" + reason);
    }

    @OnWebSocketError
    public void onError(Throwable t) {
        System.out.println("Error: " + t.getMessage());
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.println("Connect: " + session.getRemoteAddress().getAddress());

    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        //System.out.println("Message: " + message);
        MessageRouter.getInstnace().route(session, message);
    }
}