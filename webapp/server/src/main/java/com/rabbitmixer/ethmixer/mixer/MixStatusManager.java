package com.rabbitmixer.ethmixer.mixer;

import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.websockets.ClientNotifier;

import java.math.BigInteger;
import java.util.*;

import static java.lang.System.out;

public class MixStatusManager {
    private Map<String, List<MixStatus>> statusMap = Collections.synchronizedMap(new HashMap<>());
    private MixStatusManager() {

    }

    private static MixStatusManager instance;
    public static MixStatusManager getInstance() {
        if (instance == null) {
            instance = new MixStatusManager();
        }
        return instance;
    }

    public void newStealthTransfer(StealthTransferData data) {
        // Create for sender
        List<MixStatus> mixStatus = statusMap.computeIfAbsent(data.from_address, s -> new ArrayList<>());
        mixStatus.add(MixStatus.newInitial(data.from_address, data, StatusType.SENDER));

        mixStatus = statusMap.computeIfAbsent(data.to_address, s -> new ArrayList<>());
        mixStatus.add(MixStatus.newInitial(data.to_address, data, StatusType.RECIEVER));
    }

    public void updateStealthTransfer(StealthTransferData data, MixStatusName statusName, String newStatus, String txHash) {
        // Update for Sender
        MixStatus mixStatus = statusMap.get(data.from_address).stream().filter(s -> s.data.hash().equals(data.hash())).findFirst().get();
        MixStatusItem mixStatusItem = mixStatus.statusItems.stream().filter(s -> s.stepName.equals(statusName)).findFirst().get();
        mixStatusItem.status = newStatus;
        mixStatusItem.txHash = txHash;
        out.println("Updating " + statusName + " to " + newStatus);
        ClientNotifier.getInstance().doStealthTransferNotification(data.from_address);

        // Update for Reciever
        mixStatus = statusMap.get(data.to_address).stream().filter(s -> s.data.hash().equals(data.hash())).findFirst().get();
        mixStatusItem = mixStatus.statusItems.stream().filter(s -> s.stepName.equals(statusName)).findFirst().get();
        mixStatusItem.status = newStatus;
        mixStatusItem.txHash = txHash;
    }

    public List<MixStatus> getStealthTransferStatus(String address) {
        List<MixStatus> mixStatuses = statusMap.get(address);
        if (mixStatuses == null) {
            return Collections.emptyList();
        } else {
            return mixStatuses;
        }

        // If the status is all done, we should just remove it.
    }

    public void removeStealthTransfer(StealthTransferData data) {
        // Remove from Sender
        List<MixStatus> mixStatuses = statusMap.get(data.from_address);
        if (mixStatuses.size() == 1) {
            statusMap.remove(data.from_address);
        } else {
            mixStatuses.remove(mixStatuses.stream().filter(d -> d.data.hash().equals(data.hash())).findFirst().get());
        }
        ClientNotifier.getInstance().doStealthTransferNotification(data.from_address);

        // Remove from Reciever
        mixStatuses = statusMap.get(data.to_address);
        if (mixStatuses.size() == 1) {
            statusMap.remove(data.to_address);
        } else {
            mixStatuses.remove(mixStatuses.stream().filter(d -> d.data.hash().equals(data.hash())).findFirst().get());
        }
    }

    public enum MixStatusName {
        MIX_START(0, "Mix Start"),
        SENDER_PROOF(1, "Sender Proof"),
        T_RANGE_PROOF(2, "T Range Proof"),
        NEW_BAL_RANGE_PROOF(3, "New Bal Range Proof"),
        RECIEVER_RANGE_PROOF(4, "Reciever Range Proof"),
        RECIEVER_PROOF(5, "Reciever Proof"),
        VERIFICATION(6, "Verification"),
        EXECUTION(7, "Execution");

        private final String name;
        private final int sortOrder;

        MixStatusName(int sortOrder, String name) {
            this.name = name;
            this.sortOrder = sortOrder;
        }
    }

    public enum StatusType {
        SENDER("Sender"),
        RECIEVER("Reciever");

        private final String type;

        StatusType(String type) {
            this.type = type;
        }
    }

    public static class MixStatus {
        transient StealthTransferData data; // Don't serialize this.
        public String address;
        public StatusType type;
        public BigInteger nonce;
        public List<MixStatusItem> statusItems;

        private MixStatus(String address, StealthTransferData data, StatusType type) {
            this.address = address;
            this.data = data;
            this.type = type;
            this.nonce = data.token_nonce;

            this.statusItems = new ArrayList<>();
        }

        public static MixStatus newInitial(String address, StealthTransferData data, StatusType type) {
            MixStatus status = new MixStatus(address, data, type);
            status.statusItems.add(new MixStatusItem(MixStatusName.MIX_START));
            status.statusItems.add(new MixStatusItem(MixStatusName.T_RANGE_PROOF));
            status.statusItems.add(new MixStatusItem(MixStatusName.NEW_BAL_RANGE_PROOF));
            status.statusItems.add(new MixStatusItem(MixStatusName.SENDER_PROOF));
            status.statusItems.add(new MixStatusItem(MixStatusName.RECIEVER_RANGE_PROOF));
            status.statusItems.add(new MixStatusItem(MixStatusName.RECIEVER_PROOF));
            status.statusItems.add(new MixStatusItem(MixStatusName.VERIFICATION));
            status.statusItems.add(new MixStatusItem(MixStatusName.EXECUTION));

            return status;
        }
    }

    public static class MixStatusItem {
        public MixStatusName stepName;
        public String status;
        public String txHash;

        public MixStatusItem(MixStatusName stepName) {
            this.stepName = stepName;
            this.status = "Waiting...";
            this.txHash = null;
        }
    }

}
