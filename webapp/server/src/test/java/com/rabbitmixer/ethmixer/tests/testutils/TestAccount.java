package com.rabbitmixer.ethmixer.tests.testutils;

import com.rabbitmixer.ethmixer.contract.SecureToken_sol_SecureToken;
import com.google.common.io.BaseEncoding;
import com.rabbitmixer.ethmixer.db.Storage;
import com.rabbitmixer.ethmixer.mixer.MixStatusManager;
import com.rabbitmixer.ethmixer.mixer.MixerManager;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.RawTransactionManager;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static com.rabbitmixer.ethmixer.mixer.MixStatusManager.MixStatusName.EXECUTION;
import static com.rabbitmixer.ethmixer.mixer.MixStatusManager.MixStatusName.MIX_START;
import static com.rabbitmixer.ethmixer.mixer.MixStatusManager.MixStatusName.SENDER_PROOF;
import static org.awaitility.Awaitility.await;
import static com.rabbitmixer.ethmixer.tests.testutils.TestUtils.gasLimit;
import static com.rabbitmixer.ethmixer.tests.testutils.TestUtils.gasPrice;

/**
 * An account that allows for ether and stealth ethers. It tracks ether balance, token balance, nonce and blinding
 * factor.
 *
 * Makes tests easier to write.
 */
public class TestAccount {
    public ECKey            accountKey;
    public String           address;
    public BigInteger       balance;
    public BigInteger       nonce;
    public BigInteger       blinding;
    public Web3Connector    web3c;

    private StealthTransferData lastTransfer;

    public TestAccount(Web3Connector web3c) throws Exception {
        accountKey = new ECKey();
        address = "0x" + ByteUtil.toHexString(accountKey.getAddress());

        balance = BigInteger.ZERO;
        blinding = BigInteger.ZERO;
        nonce = BigInteger.ZERO;

        this.web3c = web3c;

        TestUtils.fundAccount(address, web3c);
    }

    public void deposit(int amount) throws IOException {
        Transaction tx = new Transaction(
                ByteUtil.intToBytesNoLeadZeroes(0),  // nonce
                gasPrice,
                gasLimit,
                ByteUtil.hexStringToBytes(web3c.getServerConfig().contractAddress), // toAddress - the contract
                ByteUtil.bigIntegerToBytes(BigInteger.valueOf(amount)),  // value - Amount to deposit
                ByteUtil.hexStringToBytes("d0e30db0") // data - the deposit() method
        );
        tx.sign(accountKey);
        web3c.executeRawTx("0x" + BaseEncoding.base16().encode(tx.getEncoded()));
        balance = BigInteger.valueOf(amount);

        // Item number is nonce, because every nonce increase has a history item.
        Storage.StorageItem depositItem = TestUtils.waitAndGetHistoryItem(address, nonce.intValue());
        Assert.assertEquals(depositItem.type,  "DEPOSIT");
        Assert.assertEquals(depositItem.amount, balance);
        Assert.assertEquals(depositItem.addr,   address);
    }

    public void transfer(TestAccount target, BigInteger s) throws Exception {
        BigInteger f = BigInteger.ONE;
        BigInteger t = s.add(f);

        ECKey eph = new ECKey();
        StealthTransferData transferData = StealthTransfers.getStealthTransferData(web3c, accountKey, target.accountKey,
                eph, balance, nonce, blinding, s, f);
        MixerManager.getInstance().submitNewTransaction(transferData);

        blinding        = web3c.getUtilityContract().modN(blinding.subtract(StealthTransfers.getTokenTBlindingFactor(accountKey, nonce)));
        nonce           = nonce.add(BigInteger.ONE);
        balance         = balance.subtract(t);
        lastTransfer    = transferData;

        target.balance  = target.balance.add(s);
        target.blinding = target.blinding.add(transferData.debug_s_blinding);
    }

    public void assertStealthBalance() throws Exception {
        TestUtils.assertStealthBalance(address, balance, blinding);
    }

    public void waitPartial() {
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            List<MixStatusManager.MixStatus> status = MixStatusManager.getInstance().getStealthTransferStatus(address);
            if (status == null || status.size() == 0) return false;
            if (!status.get(0).statusItems.get(3).status.equals("Done")) return false;
            return true;
        });

        List<MixStatusManager.MixStatus> status = MixStatusManager.getInstance().getStealthTransferStatus(address);
        assertEquals(status.size(), 1);
        assertEquals(status.get(0).type, MixStatusManager.StatusType.SENDER);
        assertEquals(status.get(0).statusItems.get(0).stepName, MIX_START);
        assertEquals(status.get(0).statusItems.get(0).status, "Done");
        assertEquals(status.get(0).statusItems.get(3).stepName, SENDER_PROOF);
        assertEquals(status.get(0).statusItems.get(3).status, "Done");
        assertEquals(status.get(0).statusItems.get(7).stepName, EXECUTION);
        assertEquals(status.get(0).statusItems.get(7).status, "Waiting...");
    }

    public void waitFull() {
        Storage.StorageItem sendItem = TestUtils.waitAndGetHistoryItem(address, nonce.intValue());
        Assert.assertEquals("SEND", sendItem.type);
        Assert.assertEquals(sendItem.amount, lastTransfer.debug_t);
        Assert.assertEquals(sendItem.addr, address);
    }

    public void withdrawAll() throws Exception {
        // Local instance of contract with the account address as the credential, which will allow calling the
        // contract as the account
        SecureToken_sol_SecureToken local = SecureToken_sol_SecureToken.load(
                web3c.getContract().getContractAddress(),
                web3c.getWeb3(),
                new RawTransactionManager(
                        web3c.getWeb3(),
                        Credentials.create(accountKey.getPrivKey().toString(16)), 40, web3c.getServerConfig().web3GethPollInterval),
                new BigInteger("1"),
                new BigInteger("450000"));
        BigInteger before = new BigInteger(web3c.getEtherBalance(address));

        TransactionReceipt receipt = local.withdraw_all(balance, blinding).send();
        TestUtils.processLogs("withdraw all", receipt);


        assertEquals("0x1", receipt.getStatus());
        assertEquals(before.add(balance).subtract(receipt.getGasUsed()), new BigInteger(web3c.getEtherBalance(address)));

        assertEquals(BigInteger.ZERO, local.balances(address).send().getValue1());
        assertEquals(BigInteger.ZERO, local.balances(address).send().getValue2());
    }
}
