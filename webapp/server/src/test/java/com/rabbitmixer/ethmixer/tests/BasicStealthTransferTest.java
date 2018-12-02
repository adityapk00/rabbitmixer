package com.rabbitmixer.ethmixer.tests;

import com.google.common.io.BaseEncoding;
import com.rabbitmixer.ethmixer.db.Storage;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.junit.Test;
import com.rabbitmixer.ethmixer.tests.testutils.StealthTransfers;
import com.rabbitmixer.ethmixer.tests.testutils.TestAccount;
import com.rabbitmixer.ethmixer.tests.testutils.TestUtils;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.mixer.MixerManager;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static com.rabbitmixer.ethmixer.tests.testutils.TestUtils.assertEqualsPoint;
import static com.rabbitmixer.ethmixer.tests.testutils.TestUtils.assertStealthBalance;
import static com.rabbitmixer.ethmixer.tests.testutils.StealthTransfers.zero;
import static com.rabbitmixer.ethmixer.tests.testutils.TestUtils.gasLimit;
import static com.rabbitmixer.ethmixer.tests.testutils.TestUtils.gasPrice;

public class BasicStealthTransferTest {



    @Test
    public void depositTest() throws Exception {
        EthereumConfig.Server serverConfig = TestUtils.testServerConfig();

        // Create a new Client ether account for our use.
        ECKey depositKey = new ECKey();
        String depositAddress = "0x" + ByteUtil.toHexString(depositKey.getAddress());

        Web3Connector web3c = Web3Connector.initWithConfig(serverConfig);
        UtilityContract uc = web3c.getUtilityContract();
        MixerManager.reconfigure();

        TestUtils.fundAccount(depositAddress, web3c);

        // 1: DO A DEPOSIT
        BigInteger balance = new BigInteger("5");
        Transaction tx = new Transaction(
                ByteUtil.intToBytesNoLeadZeroes(0),  // nonce
                gasPrice,
                gasLimit,
                ByteUtil.hexStringToBytes(serverConfig.contractAddress), // toAddress - the contract
                ByteUtil.bigIntegerToBytes(balance),  // value - Amount to deposit
                ByteUtil.hexStringToBytes("d0e30db0") // data - the deposit() method
        );
        tx.sign(depositKey);
        web3c.executeRawTx("0x" + BaseEncoding.base16().encode(tx.getEncoded()));

        Storage.StorageItem depositItem = TestUtils.waitAndGetHistoryItem(depositAddress, 0);
        assertEquals(depositItem.type,  "DEPOSIT");
        assertEquals(depositItem.amount, balance);
        assertEquals(depositItem.addr,   depositAddress);

        BigInteger depositBlinding = zero;  // Blinding factor for a deposit is 0
        assertStealthBalance(depositAddress, balance, depositBlinding);



        // 2: DO A STEALTH TRANSFER
        ECKey stealthKey = new ECKey();

        BigInteger s = new BigInteger("2");
        BigInteger f = new BigInteger("1");
        BigInteger t = s.add(f);

        ECKey eph = new ECKey();

        String stealthAddress   = "0x" + ByteUtil.toHexString(stealthKey.getAddress());

        assertStealthBalance(depositAddress, balance, BigInteger.ZERO);
        StealthTransferData transferData = StealthTransfers.getStealthTransferData(web3c, depositKey, stealthKey,
                eph, balance, zero, BigInteger.ZERO, s, f);

        MixerManager.getInstance().submitNewTransaction(transferData);

        BigInteger stealthBalance = s;  // Should be equal to the sent amount

        Storage.StorageItem sendItem = TestUtils.waitAndGetHistoryItem(depositAddress, 1);
        assertEquals(sendItem.type, "SEND");
        assertEquals(sendItem.amount, t);
        assertEquals(sendItem.addr, depositAddress);

        Storage.RecieveStorageItem recieveItem = TestUtils.waitAndGetHistoryItem(stealthAddress, 0);
        assertEquals(recieveItem.type, "RECIEVE");
        assertEquals(recieveItem.addr, stealthAddress);
        assertEquals(recieveItem.amount, stealthBalance);
        assertEqualsPoint(recieveItem.ephPub, uc.ecMul(uc.getG(), eph.getPrivKey()));

        depositBlinding = uc.modN(depositBlinding.subtract(StealthTransfers.getTokenTBlindingFactor(depositKey, zero)));
        BigInteger stealthBlinding = new BigInteger("2"); // TODO: Calculate this from ECDH

        assertStealthBalance(depositAddress, balance.subtract(s).subtract(f), depositBlinding);
        assertStealthBalance(stealthAddress, s, stealthBlinding);



        // 3: CREATE A WITHDRAWAL
        ECKey withdrawalKey = new ECKey();
        s = BigInteger.ONE;
        f = BigInteger.ONE;

        String withdrawlAddress = "0x" + ByteUtil.toHexString(withdrawalKey.getAddress());
        BigInteger withdrawalBalance = s;

        assertStealthBalance(stealthAddress, stealthBalance, stealthBlinding);
        StealthTransferData withdrawalData = StealthTransfers.getStealthWithdrawalData(web3c, stealthKey,
                withdrawalKey, stealthBalance, zero, stealthBlinding, s, f);
        MixerManager.getInstance().submitNewTransaction(withdrawalData);

        await().atMost(5, TimeUnit.SECONDS).until(() -> Storage.getHistory(stealthAddress).size() > 1);
        sendItem = Storage.getHistory(stealthAddress).get(1);
        assertEquals(sendItem.type, "SEND");
        assertEquals(sendItem.amount, s.add(f));

        // Assert the balance is 0.
        stealthBlinding = uc.modN(stealthBlinding.subtract(StealthTransfers.getTokenTBlindingFactor(stealthKey, zero)));
        assertStealthBalance(stealthAddress, zero, stealthBlinding);
        assertEquals(withdrawalBalance.toString(), web3c.getEtherBalance(withdrawlAddress));
    }

    @Test
    public void testOddNumbers() throws Exception {
        Web3Connector web3c = Web3Connector.getInstnace();
        TestAccount deposit = new TestAccount(web3c);
        TestAccount stealth = new TestAccount(web3c);

        Random r = new Random();
        int depositAmount = r.nextInt(Integer.MAX_VALUE);

        deposit.deposit(depositAmount);

        BigInteger transferAmount = BigInteger.valueOf(r.nextInt(depositAmount));
        deposit.transfer(stealth, transferAmount);

        deposit.waitFull();
        deposit.assertStealthBalance();
        stealth.assertStealthBalance();

        BigInteger transfer2 = BigInteger.valueOf(r.nextInt(transferAmount.intValue()));
        stealth.transfer(deposit, transfer2);

        stealth.waitFull();
        deposit.assertStealthBalance();
        stealth.assertStealthBalance();
    }

}
