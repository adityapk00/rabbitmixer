package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;

import java.math.BigInteger;

public class ClientConfigCommand extends AbstractCommand {

    public ClientConfigCommand(String commandName) {
        super(commandName);
    }

    @Override
    JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException {
        return gson.toJsonTree(new ResponseObject(web3.getServerConfig()));
    }


    public class ResponseObject {
        String network;
        String contractAddress;
        BigInteger currentFee;
        BigInteger gasPrice;

        ResponseObject(EthereumConfig.Server serverConfig) {
            this.network = serverConfig.network;
            this.contractAddress = serverConfig.contractAddress;
            this.currentFee = serverConfig.currentFee;
            this.gasPrice = serverConfig.gasPrice;
        }
    }

}
