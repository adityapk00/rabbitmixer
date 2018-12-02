package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;

import java.io.IOException;

import static java.lang.System.out;

public abstract class AbstractCommand {
    public class AddressRequestObject {
        String address;
    }

    public static class ClientResponse {
        String          command;
        String          id;
        String          status;
        JsonElement     response;

        public ClientResponse(String cmd, String id, String status, JsonElement resp) {
            this.command    = cmd;
            this.id         = id;
            this.status     = status;
            this.response   = resp;
        }
    }

    Gson gson = new Gson();
    private String commandName;

    AbstractCommand(String commandName) {
        this.commandName = commandName;
    }

    public String getCommandName() {
        return commandName;
    }

    protected Object getRequestObject(JsonElement payload) {
        return gson.fromJson(payload, AddressRequestObject.class);
    }

    abstract JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException, Exception;

    public void handleCommand(Session session, String id, JsonElement payload) {
        Web3Connector web3 = Web3Connector.getInstnace();

        Object request = getRequestObject(payload);

        JsonElement result = null;
        try {
            result = execute(session, web3, request);
        } catch (Exception e) {
            out.println("Exception trying to handle command:" + getCommandName() + payload.toString());
            e.printStackTrace();
        }

        sendResponse(session, id, result);
    }

    protected void sendResponse(Session session, String id, JsonElement result) {
        if (result != null) {
            session.getRemote().sendStringByFuture(gson.toJson(new ClientResponse(this.getCommandName(), id, "OK", result)));
        } else {
            // TODO: Return an error to the client properly?
            session.getRemote().sendStringByFuture(gson.toJson(new ClientResponse(this.getCommandName(), id,"Error", null)));
        }
    }
}
