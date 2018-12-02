package com.rabbitmixer.ethmixer.web3.commands;

import com.google.gson.JsonElement;
import com.rabbitmixer.ethmixer.db.Storage;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TokenAddressHistoryCommand extends AbstractCommand {

    public TokenAddressHistoryCommand(String commandName) {
        super(commandName);
    }

    class HistoryItem {
        String type;
        String enc_amount;
        String transactionHash;
        String address;
        String timestamp;

        HistoryItem(String type, String address, BigInteger amount, String hash, BigInteger timestamp) {
            this.type               = type;
            this.address            = address;
            this.enc_amount         = amount.toString(10);
            this.transactionHash    = hash;
            this.timestamp          = timestamp.toString(10);
        }
    }

    public class RecieveHistoryItem extends HistoryItem {
        String eph_pub_x;
        String eph_pub_y;
        RecieveHistoryItem(String type, String addr, BigInteger amount, List<BigInteger> ephPub, String hash, BigInteger timestamp) {
            super(type, addr, amount, hash, timestamp);
            this.eph_pub_x = ephPub.get(0).toString(10);
            this.eph_pub_y = ephPub.get(1).toString(10);
        }
    }

    public class ResponseObject {
        String              address;
        List<HistoryItem>   history;

        ResponseObject(String address, List<HistoryItem> items) {
            this.address = address;
            this.history = items;
        }
    }

    @Override
    JsonElement execute(Session session, Web3Connector web3, Object request) throws InterruptedException, Exception {
        AddressRequestObject cmd = (AddressRequestObject) request;
        List<Storage.StorageItem> history = Storage.getHistory(cmd.address);

        if (history == null) {
            return gson.toJsonTree(new ResponseObject(cmd.address, new ArrayList<>()));
        } else {
            ResponseObject response = new ResponseObject(cmd.address, history.stream()
                    .map(i -> {
                        if (i.type.equals("RECIEVE")) return new RecieveHistoryItem(i.type, i.addr, i.amount, ((Storage.RecieveStorageItem)i).ephPub, i.transactionHash, i.timestamp);
                        else return new HistoryItem(i.type, i.addr, i.amount, i.transactionHash, i.timestamp);
                    })
                    .collect(Collectors.toList()));
            return gson.toJsonTree(response);
        }
    }
}
