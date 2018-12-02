package com.rabbitmixer.ethmixer.websockets;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.commands.AbstractCommand;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.out;

/**
 * A two-way map that can look up Session objects given an address,
 * and a list of addresses given a Session
 */
public class ClientNotifier {
    private Map<Session, List<String>> sessionToAddressListMap = new ConcurrentHashMap<>();
    private Map<String, List<Session>> addressToSessionListMap = new ConcurrentHashMap<>();
    private Gson gson = new Gson();

    private ClientNotifier(){
    }

    private static ClientNotifier instance = null;
    public static ClientNotifier getInstance() {
        if (instance == null) {
            instance = new ClientNotifier();
        }
        return instance;
    }

    /**
     * Register the given address and the session in the two way map
     */
    public void registerAddressSession(String address, Session session) {
        List<String> addresses = sessionToAddressListMap.computeIfAbsent(session, k -> new ArrayList<>());
        if (!addresses.contains(address)) {
            addresses.add(address);
        }

        List<Session> sessions = addressToSessionListMap.computeIfAbsent(address, k -> new ArrayList<>());
        if (!sessions.contains(session)) {
            sessions.add(session);
        }
    }

    public List<String> getAddressesFromSession(Session session) {
        return sessionToAddressListMap.getOrDefault(session, new ArrayList<>());
    }

    public List<Session> getSessionsFromAddress(String address) {
        return addressToSessionListMap.getOrDefault(address, new ArrayList<>());
    }

    private void sendResponse(Session session, JsonElement result) {
        if (result != null) {
            session.getRemote().sendStringByFuture(gson.toJson(new AbstractCommand.ClientResponse("", "", "OK", result)));
        } else {
            // TODO: Return an error to the client properly?
            session.getRemote().sendStringByFuture(gson.toJson(new AbstractCommand.ClientResponse("", "","Error", null)));
        }
    }

    public void removeSession(Session session) {
        if (session == null) {
            out.println("Trying to remove a NULL session!");
        }
        List<String> addresses = sessionToAddressListMap.get(session);
        if (session != null) sessionToAddressListMap.remove(session);

        addresses.forEach(address -> {
            List<Session> sessions = addressToSessionListMap.get(address);
            if (sessions.contains(session)) sessions.remove(session);
            if (addressToSessionListMap.get(address).size() == 0) {
                addressToSessionListMap.remove(address);
            }
        });
    }

    public class ClientNotification {
        String address;
        String amount;
        String notification;

        ClientNotification(String address, String amount, String notification) {
            this.address = address;
            this.amount = amount;
            this.notification = notification;
        }
    }


    public void doStealthTransferNotification(String address) {
        List<Session> sessions = getSessionsFromAddress(address);
        ClientNotification notification = new ClientNotification(address, "", "stealth_transfer_pending");
        sessions.forEach(session -> sendResponse(session, gson.toJsonTree(notification)));
    }

    public void doDepositNotification(String address, BigInteger amount) {
        List<Session> sessions = getSessionsFromAddress(address);
        ClientNotification notification = new ClientNotification(address, amount.toString(10), "deposit_complete");
        sessions.forEach(session -> sendResponse(session, gson.toJsonTree(notification)));
    }

    public void doSendNotification(String address, BigInteger amount) {
        List<Session> sessions = getSessionsFromAddress(address);
        ClientNotification notification = new ClientNotification(address, amount.toString(10), "send_complete");
        sessions.forEach(session -> sendResponse(session, gson.toJsonTree(notification)));
    }


    public void doRecieveNotification(String addr, BigInteger amount) {
        List<Session> sessions = getSessionsFromAddress(addr);
        ClientNotification notification = new ClientNotification(addr, amount.toString(10), "recieve_complete");
        sessions.forEach(session -> sendResponse(session, gson.toJsonTree(notification)));
    }

}
