package com.rabbitmixer.ethmixer.web3;

import java.math.BigInteger;

/**
 * Central place for all ether-related config. These are also sent to the client.
 */
public class EthereumConfig {
    public static class Server {
        public String serverAddress = "http://localhost:8545";
        public String privateKey = "2a592c186829f61c1efba42c5c4eba28906d4ae7f9e301efdaa2135bab879703";
        public String address = "0x20f68D36fe2F82b9a0a75f5B74ACAb62A1f1E378";
        public int    web3GethPollInterval;
        public int    mixSize = 1;

        public String       contractAddress;
        public String       utilityContractAddress;

        public String       network;
        public BigInteger   currentFee = BigInteger.TEN.pow(16); // 0.01 Ether
        public BigInteger   gasPrice = BigInteger.TEN.pow(9);
    }
}
