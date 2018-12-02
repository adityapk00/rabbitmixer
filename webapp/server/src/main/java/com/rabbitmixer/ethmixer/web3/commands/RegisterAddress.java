package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.websockets.ClientNotifier;

public class RegisterAddress extends  AbstractCommand {
    public RegisterAddress(String commandName) {
        super(commandName);
    }

    public class ResponseObject {
        String status;
        ResponseObject(String status) {
            this.status = status;
        }
    }

    @Override
    JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException, Exception {
        AddressRequestObject cmd = (AddressRequestObject)request;
        ClientNotifier.getInstance().registerAddressSession(cmd.address, session);

        return gson.toJsonTree(new ResponseObject("OK"));
    }
}
