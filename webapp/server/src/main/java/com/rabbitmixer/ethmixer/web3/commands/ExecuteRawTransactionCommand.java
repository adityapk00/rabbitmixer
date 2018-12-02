package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;

public class ExecuteRawTransactionCommand extends AbstractCommand {

    public class RequestObject {
        String rawtx;
    }

    public class ResponseObject {
        String transactionHash;

        ResponseObject(String transactionHash) {
            this.transactionHash = transactionHash;
        }
    }

    public ExecuteRawTransactionCommand(String commandName) {
        super(commandName);
    }


    @Override
    JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException {
        RequestObject cmd = (RequestObject) request;
        String result = web3.executeSingle(
                () -> web3.executeRawTx(cmd.rawtx)
        );

        // Note that the deposit notification happens when the deposit event is recieved.
        return gson.toJsonTree(new ResponseObject(result));
    }

    @Override
    protected Object getRequestObject(JsonElement payload) {
        return gson.fromJson(payload, RequestObject.class);
    }

}
