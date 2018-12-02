package com.rabbitmixer.ethmixer.web3.dev;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import com.rabbitmixer.ethmixer.web3.Web3Connector;

import java.io.IOException;
import java.math.BigInteger;

import static java.lang.System.out;
/**
 * Send Ether to accounts, useful during dev and testing
 */
public class DevAccountFiller {
    public static void fillAccount(Web3Connector web3c, String address) {
        if (!web3c.getServerConfig().network.equals("dev")) {
            out.println("Not filling account " + address + " because server is not in dev mode");
        } else {
            try {
                fillAccount(web3c.getWeb3(), address);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private static void fillAccount(Web3j web3, String address) throws IOException, InterruptedException {
        if (web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance().equals(new BigInteger("0"))) {
            out.println("Sending some money to " + address);
            String defaultAccount = web3.ethAccounts().send().getAccounts().get(0);

            BigInteger nonce = web3.ethGetTransactionCount(defaultAccount, DefaultBlockParameterName.LATEST).send().getTransactionCount();

            EthSendTransaction sendTx = web3.ethSendTransaction(
                    Transaction.createEtherTransaction(
                            defaultAccount,
                            nonce,
                            new BigInteger("1"),
                            new BigInteger("21000"),
                            address,
                            new BigInteger("100000").multiply(BigInteger.TEN.pow(18)) // 100k ETHER
                    )).send();

            // Wait for the balance to actually show up, because otherwise it causes all kinds of issues.
            while (web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance().compareTo(BigInteger.valueOf(0)) <= 0) {
                Thread.sleep(100);
            }

            if (sendTx.hasError()) {
                out.println(sendTx.getError());
            }
        }
    }

}
