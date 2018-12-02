package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.dev.DevAccountFiller;

/**
 * Command used only during development, where we send the client some money just to make testing easier.
 */
public class DevSendMeMoneyCommand extends AbstractCommand {

    public DevSendMeMoneyCommand(String commandName) {
        super(commandName);
    }

    @Override
    protected JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException, Exception {
        AddressRequestObject cmd = (AddressRequestObject)request;
        DevAccountFiller.fillAccount(Web3Connector.getInstnace(), cmd.address);

        return gson.toJsonTree("Success");
    }
}
