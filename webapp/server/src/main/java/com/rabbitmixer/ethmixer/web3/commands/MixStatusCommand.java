package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import com.rabbitmixer.ethmixer.mixer.MixStatusManager;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;

import java.util.ArrayList;
import java.util.List;

public class MixStatusCommand extends AbstractCommand {

    public class ResponseObject {
        String address;
        List<MixStatusManager.MixStatus> statuses;

        ResponseObject(String address, List<MixStatusManager.MixStatus> statuses) {
            this.address = address;
            this.statuses = new ArrayList<>();
            this.statuses.addAll(statuses);
        }
    }

    public MixStatusCommand(String commandName) {
        super(commandName);
    }

    @Override
    JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException, Exception {
        AddressRequestObject cmd = (AddressRequestObject) request;
        return gson.toJsonTree(
                new ResponseObject(cmd.address, MixStatusManager.getInstance().getStealthTransferStatus(cmd.address))
        );
    }
}
