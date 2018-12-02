package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.mixer.MixerManager;

public class DoTransactionCommand extends AbstractCommand {

    public class ResponseObject {
        boolean success;
        public ResponseObject(boolean success) {
            this.success = success;
        }
    }

    public DoTransactionCommand(String commandName) {
        super(commandName);
    }

    @Override
    JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException, Exception {
        StealthTransferData cmd = (StealthTransferData) request;
        MixerManager.getInstance().submitNewTransaction(cmd);


        return gson.toJsonTree(new ResponseObject(true));
    }


    @Override
    protected Object getRequestObject(JsonElement payload) {
        return gson.fromJson(payload, StealthTransferData.class);
    }

}
