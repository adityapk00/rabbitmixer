package com.rabbitmixer.ethmixer.tests;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.rabbitmixer.ethmixer.mixer.MixerManager;
import org.ethereum.crypto.ECKey;
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
import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

/**
 * Test to verify the stealth transfers are rejected if stuff appears funny.
 */
public class StealthTransferVerificationTest {
    private static Gson gson;
    private static EthereumConfig.Server serverConfig;
    private static Web3Connector web3c;
    private static UtilityContract uc;
    private static MixerManager mm;

    @BeforeClass
    public static void setup() {
        gson = new Gson();
        serverConfig = TestUtils.testServerConfig();
        serverConfig.mixSize = 2;

        web3c = Web3Connector.initWithConfig(serverConfig);
        uc = web3c.getUtilityContract();

        MixerManager.reconfigure();
        mm = MixerManager.getInstance();
    }


    @Test
    public void testBadTransfers() throws Exception {
        TestAccount deposit = new TestAccount(web3c);
        TestAccount stealth = new TestAccount(web3c);
        deposit.deposit(100);

        ECKey eph = new ECKey();
        BigInteger s = BigInteger.TEN;
        BigInteger f = BigInteger.ONE;

        // Test a simple, correct transfer to make sure everything is working
        StealthTransferData data = StealthTransfers.getStealthTransferData(web3c, deposit.accountKey, stealth.accountKey,
                eph, deposit.balance, deposit.nonce, deposit.blinding, s, f);
        assertTrue(mm.submitNewTransaction(data));
        deposit.waitPartial();

        // Start the negative tests
        // ** Send the transaction again, this time it will fail because the sender already has a transaction pending
        assertFalse(mm.submitNewTransaction(data));

        // Get a new deposit address because the previous one is now fucked
        deposit = new TestAccount(web3c);
        stealth = new TestAccount(web3c);
        deposit.deposit(100);
        data = StealthTransfers.getStealthTransferData(web3c, deposit.accountKey, stealth.accountKey,
                eph, deposit.balance, deposit.nonce, deposit.blinding, s, f);
        // Save the values to easily restore them later
        JsonElement orig = gson.toJsonTree(data);

        // ** Incorrect range proof for all the fields
        // Try and fuck will every one of the params to make sure that it fails
        assertRangeProofComponents(orig, "t_range_proof");
        assertRangeProofComponents(orig, "s_range_proof");
        assertRangeProofComponents(orig, "new_bal_range_proof");

        // ** Change the value that the range proofs refer to
        data = gson.fromJson(orig, StealthTransferData.class);
        data.S = data.F;
        assertFalse(mm.submitNewTransaction(data));

        data = gson.fromJson(orig, StealthTransferData.class);
        data.T = data.F;
        assertFalse(mm.submitNewTransaction(data));

        data = gson.fromJson(orig, StealthTransferData.class);
        data.F = data.T;
        assertFalse(mm.submitNewTransaction(data));
    }

    private void assertRangeProofComponents(JsonElement orig, String fieldName) throws Exception {
        StealthTransferData data = gson.fromJson(orig, StealthTransferData.class);
        List<BigInteger> rangeProof = (List<BigInteger>) data.getClass().getField(fieldName).get(data);

        for(int i=0; i < rangeProof.size(); i++) {
            data = gson.fromJson(orig, StealthTransferData.class);
            List<BigInteger> modifiedRangeProof = (List<BigInteger>) data.getClass().getField(fieldName).get(data);
            modifiedRangeProof.add(i, modifiedRangeProof.get(i).add(BigInteger.ONE));
            data.getClass().getField(fieldName).set(data, modifiedRangeProof);
            assertFalse(mm.submitNewTransaction(data));
        }
    }
}


































