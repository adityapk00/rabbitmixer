package com.rabbitmixer.ethmixer.db;

import java.math.BigInteger;
import java.util.*;

import static java.lang.System.out;

/**
 * Placeholder for DB. All this should really be persisted in a DB
 */
public class Storage {

    public static class StorageItem {
        public String       type;
        public String       addr;
        public BigInteger   amount;
        public BigInteger   blockNumber;
        public String       transactionHash;
        public BigInteger   timestamp;

        StorageItem(String type, String addr, BigInteger amount, BigInteger blockNumber, String transactionHash, BigInteger timestamp) {
            this.type               = type;
            this.addr               = addr;
            this.amount             = amount;
            this.blockNumber        = blockNumber;
            this.transactionHash    = transactionHash;
            this.timestamp          = timestamp;
        }
    }

    public static class RecieveStorageItem extends StorageItem {
        public List<BigInteger> ephPub;
        RecieveStorageItem(String type, String addr, BigInteger amount, List<BigInteger> ephPub, BigInteger blockNumber, String transactionHash, BigInteger timestamp) {
            super(type, addr, amount, blockNumber, transactionHash, timestamp);
            this.ephPub = ephPub;
        }
    }

    private static Map<String, List<StorageItem>> addressHistoryStore = Collections.synchronizedMap(new HashMap<>());
    private static Set<String> allAddresses = Collections.synchronizedSet(new HashSet<>());

    public static void depositComplete(String addr, BigInteger amount, BigInteger blockNumber, String transactionHash, BigInteger timestamp) {
        //out.println("Store: StorageItem: " + addr + ":" + amount);
        List<StorageItem> history = addressHistoryStore.computeIfAbsent(addr, k -> new ArrayList<>());

        // Make sure this deposit is not already in the history
        if (history.stream().anyMatch(i -> i.transactionHash.equals(transactionHash))) {
            out.println("Recieved duplicate deposit event, skipping: " + transactionHash);
            return;
        }

        history.add(new StorageItem("DEPOSIT", addr, amount, blockNumber, transactionHash, timestamp));
        allAddresses.add(addr);
    }


    public static void sendComplete(String addr, BigInteger amount, BigInteger blockNumber, String transactionHash, BigInteger timestamp) {
        //out.println("Store: SendCompleteItem: " + addr + ":" + amount);
        List<StorageItem> history = addressHistoryStore.computeIfAbsent(addr, k -> new ArrayList<>());

        if (history.stream().anyMatch(i -> i.transactionHash.equals(transactionHash))) {
            out.println("Recieved duplicate SEND event, skipping:" + transactionHash);
            return;
        }

        history.add(new StorageItem("SEND", addr, amount, blockNumber, transactionHash, timestamp));
        allAddresses.add(addr);
    }

    public static void recieveComplete(String addr, BigInteger amount, List<BigInteger> ephPub, BigInteger blockNumber, String transactionHash, BigInteger timestamp) {
        //out.println("Store: Recieve Complete:" + addr + ":" + amount);
        List<StorageItem> history = addressHistoryStore.computeIfAbsent(addr, k -> new ArrayList<>());

        if (history.stream().anyMatch(i -> i.transactionHash.equals(transactionHash))) {
            out.println("Recieved duplicate RECIEVE event, skipping:" + transactionHash);
            return;
        }

        history.add(new RecieveStorageItem("RECIEVE", addr, amount, ephPub, blockNumber, transactionHash, timestamp));
        allAddresses.add(addr);
    }

    /**
     * Get a list of all history items for the given address.
     * @param address
     * @return If the address is unknown, return an empty list. Note that the list is a copy of the data, so it cannot
     * be modified.
     */
    public static List<StorageItem> getHistory(String address) {
        if (addressHistoryStore.get(address) == null) {
            return Collections.emptyList();
        } else {
            return new ArrayList<>(addressHistoryStore.get(address));
        }
    }

    /**
     * Get a list of all addresses that are storing a balance. We'll return a copy of the set to
     * make sure callers don't mess with it.
     */
    public static Set<String> getAllAddresses() {
        return new HashSet<>(allAddresses);
    }

}
