package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import org.web3j.tuples.generated.Tuple2;
import org.web3j.tuples.generated.Tuple3;
import com.rabbitmixer.ethmixer.web3.Web3Connector;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class TokenDetailsCommand extends AbstractCommand {

    public TokenDetailsCommand(String commandName) {
        super(commandName);
    }

    public class ResponseObject {
        String address;
        String balancex;
        String balancey;
        String nonce;

        ResponseObject(String address, String pointx, String pointy, String nonce) {
            this.address    = address;
            this.balancex   = pointx;
            this.balancey   = pointy;
            this.nonce      = nonce;
        }
    }

    @Override
    JsonElement execute(Session session, Web3Connector web3, Object request) throws Exception {
        AddressRequestObject cmd = (AddressRequestObject) request;
        Tuple3<BigInteger, BigInteger, Boolean> results = web3.executeSingle(
                () -> web3.getTokenBalanceInfo(cmd.address)
        );

        List<BigInteger> balancePoint = web3.getUtilityContract().expandPoint(results.getValue1());
        return gson.toJsonTree(
                new ResponseObject(
                        cmd.address,
                        balancePoint.get(0).toString(10),
                        balancePoint.get(1).toString(10),
                        results.getValue2().toString(10)
                ));
    }

}
