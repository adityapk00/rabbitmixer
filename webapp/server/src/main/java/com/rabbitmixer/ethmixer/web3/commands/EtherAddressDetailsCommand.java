package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;

import java.util.Arrays;
import java.util.List;

public class EtherAddressDetailsCommand extends AbstractCommand {
    public class ResponseObject {
        String balance;
        String address;
        String nonce_hex;

        ResponseObject(String addr, String nonce, String balance) {
            this.address    = addr;
            this.nonce_hex  = nonce;
            this.balance    = balance;
        }
    }

    public EtherAddressDetailsCommand(String commandName) {
        super(commandName);

    }

    @Override
    JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException {
        AddressRequestObject cmd = (AddressRequestObject)request;
        List<String> results = web3.executeParallelBatch(Arrays.asList(
                () -> web3.getNonceHex(cmd.address),
                () -> web3.getEtherBalance(cmd.address)
        ));

        return gson.toJsonTree(new ResponseObject(cmd.address, results.get(0), results.get(1)));
    }

}