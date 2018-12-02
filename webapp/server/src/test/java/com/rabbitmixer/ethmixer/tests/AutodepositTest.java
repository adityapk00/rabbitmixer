package com.rabbitmixer.ethmixer.tests;

import com.google.gson.Gson;
import com.rabbitmixer.ethmixer.db.Storage;
import com.rabbitmixer.ethmixer.mixer.MixerManager;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import com.rabbitmixer.ethmixer.tests.testutils.StealthTransfers;
import com.rabbitmixer.ethmixer.tests.testutils.TestAccount;
import com.rabbitmixer.ethmixer.tests.testutils.TestUtils;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class AutodepositTest {
    private static Gson gson;
    private static EthereumConfig.Server serverConfig;
    private static Web3Connector web3c;
    private static UtilityContract uc;
    private static MixerManager mm;

    @BeforeClass
    public static void setup() {
        gson = new Gson();
        serverConfig = TestUtils.testServerConfig();
        serverConfig.mixSize = 1;

        web3c = Web3Connector.initWithConfig(serverConfig);
        uc = web3c.getUtilityContract();

        MixerManager.reconfigure();
        mm = MixerManager.getInstance();
    }

    @Test
    public void testDeposit() throws Exception {
        TestAccount deposit = new TestAccount(web3c);
        TestAccount stealth = new TestAccount(web3c);
        deposit.deposit(11);

        // Do the first stealth transfer
        ECKey eph = new ECKey();
        BigInteger s = BigInteger.TEN;
        BigInteger f = BigInteger.ONE;

        // Test a simple, correct transfer to make sure everything is working
        StealthTransferData data = StealthTransfers.getStealthTransferData(web3c, deposit.accountKey, stealth.accountKey,
                eph, deposit.balance, deposit.nonce, deposit.blinding, s, f);
        assertTrue(mm.submitNewTransaction(data));

        // While this one is pending, there shouldn't be any more transfers, since we sent all the deposited money
        for (int i=0; i < 100; i++) {
            assertFalse(mm.submitNewTransaction(data));
            if (i % 10 == 0) Thread.sleep(100);
        }

        // Wait for event and make sure everything is OK.
        Storage.StorageItem sendItem = TestUtils.waitAndGetHistoryItem(deposit.address, 1);
        Assert.assertEquals("SEND", sendItem.type);
        Assert.assertEquals(sendItem.amount, s.add(f));
        Assert.assertEquals(sendItem.addr, deposit.address);

    }
}
