package com.rabbitmixer.ethmixer.tests;

import com.rabbitmixer.ethmixer.db.Storage;
import com.rabbitmixer.ethmixer.mixer.MixStatusManager;
import com.rabbitmixer.ethmixer.mixer.MixerManager;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;
import com.rabbitmixer.ethmixer.tests.testutils.StealthTransfers;
import com.rabbitmixer.ethmixer.tests.testutils.TestUtils;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static com.rabbitmixer.ethmixer.mixer.MixStatusManager.MixStatusName.*;
import static org.awaitility.Awaitility.await;
import static com.rabbitmixer.ethmixer.tests.testutils.TestUtils.assertStealthBalance;
import static com.rabbitmixer.ethmixer.tests.testutils.StealthTransfers.zero;

/**
 * Do a Mix with multiple participants.
 */
public class MultiMixStealthTransferTest {

    @Test
    public void multiTransferTest() throws Exception {
        EthereumConfig.Server serverConfig = TestUtils.testServerConfig();
        serverConfig.mixSize = 2;

        Web3Connector web3c = Web3Connector.initWithConfig(serverConfig);
        UtilityContract uc = web3c.getUtilityContract();
        MixerManager.reconfigure();

        // Create a new Client ether account for our use.
        ECKey depositKey1 = new ECKey();
        String depositAddress1 = "0x" + ByteUtil.toHexString(depositKey1.getAddress());
        TestUtils.fundAccount(depositAddress1, web3c);

        BigInteger balance1 = BigInteger.valueOf(5);
        TestUtils.doSimpleDeposit(web3c, depositKey1, balance1.intValue());

        // DO the Stealth Transfer for deposit 1
        // 1: DO A STEALTH TRANSFER
        ECKey stealthKey = new ECKey();

        BigInteger s = new BigInteger("2");
        BigInteger f = new BigInteger("1");
        BigInteger t = s.add(f);

        ECKey eph = new ECKey();

        String stealthAddress   = "0x" + ByteUtil.toHexString(stealthKey.getAddress());

        StealthTransferData transferData = StealthTransfers.getStealthTransferData(web3c, depositKey1, stealthKey,
                eph, balance1, zero, BigInteger.ZERO, s, f);
        MixerManager.getInstance().submitNewTransaction(transferData);

        // Wait for it to (partially) execute
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            List<MixStatusManager.MixStatus> status = MixStatusManager.getInstance().getStealthTransferStatus(depositAddress1);
            if (status == null || status.size() == 0) return false;
            if (!status.get(0).statusItems.get(3).status.equals("Done")) return false;
            return true;
        });

        // At this point, the Mix Manager should have submitted the sender steps, but not actually executed it.
        assertStealthBalance(stealthAddress, BigInteger.ZERO, BigInteger.ZERO);
        List<MixStatusManager.MixStatus> status = MixStatusManager.getInstance().getStealthTransferStatus(depositAddress1);
        assertEquals(status.size(), 1);
        assertEquals(status.get(0).type, MixStatusManager.StatusType.SENDER);
        assertEquals(status.get(0).statusItems.get(0).stepName, MIX_START);
        assertEquals(status.get(0).statusItems.get(0).status, "Done");
        assertEquals(status.get(0).statusItems.get(3).stepName, SENDER_PROOF);
        assertEquals(status.get(0).statusItems.get(3).status, "Done");
        assertEquals(status.get(0).statusItems.get(7).stepName, EXECUTION);
        assertEquals(status.get(0).statusItems.get(7).status, "Waiting...");

        // Do the Stealth Transfer for deposit 2
        ECKey depositKey2 = new ECKey();
        String depositAddress2 = "0x" + ByteUtil.toHexString(depositKey2.getAddress());
        TestUtils.fundAccount(depositAddress2, web3c);

        BigInteger balance2 = BigInteger.valueOf(5);
        TestUtils.doSimpleDeposit(web3c, depositKey2, balance2.intValue());

        transferData = StealthTransfers.getStealthTransferData(web3c, depositKey2, stealthKey,
                eph, balance2, zero, BigInteger.ZERO, s, f);
        MixerManager.getInstance().submitNewTransaction(transferData);

        // Now check that the transaction is complete for both deposit addresses
        Storage.StorageItem sendItem = TestUtils.waitAndGetHistoryItem(depositAddress1, 1);
        Assert.assertEquals(sendItem.type, "SEND");
        Assert.assertEquals(sendItem.amount, t);
        Assert.assertEquals(sendItem.addr, depositAddress1);

        sendItem = TestUtils.waitAndGetHistoryItem(depositAddress2, 1);
        Assert.assertEquals(sendItem.type, "SEND");
        Assert.assertEquals(sendItem.amount, t);
        Assert.assertEquals(sendItem.addr, depositAddress2);

        // And that the stealth address recieved both stealth transfers
        BigInteger stealthBlinding = new BigInteger("2"); // TODO: Calculate this from ECDH
        assertStealthBalance(stealthAddress, s.add(s), stealthBlinding.add(stealthBlinding));
    }

}
