package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

public class TokenGenerateRangeProofCommand  extends AbstractCommand {

    public class RequestObject {
        BigInteger v;
        BigInteger pow;
        BigInteger offset;
        BigInteger blinding_factor;
    }

    public class ResponseObject {
        List<String> proofs;

        ResponseObject(List<BigInteger> result) {
            // Convert to Strings
            proofs = result.stream().map(BigInteger::toString).collect(Collectors.toList());
        }
    }



    public TokenGenerateRangeProofCommand(String commandName) {
        super(commandName);
    }

    @Override
    JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException, Exception {
        RequestObject cmd = (RequestObject) request;
        List<BigInteger> result = web3.executeSingle(
                () -> web3.generateRangeProof(cmd.v, cmd.pow, cmd.offset, cmd.blinding_factor)
        );

        ResponseObject response = new ResponseObject(result);


        return gson.toJsonTree(response);
    }

    @Override
    protected Object getRequestObject(JsonElement payload) {
        return gson.fromJson(payload, RequestObject.class);
    }

}
