package com.rabbitmixer.ethmixer.tests;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import com.rabbitmixer.ethmixer.tests.testutils.StealthTransfers;
import com.rabbitmixer.ethmixer.tests.testutils.TestUtils;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.mixer.MixerManager;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.rabbitmixer.ethmixer.tests.testutils.StealthTransfers.zero;

/**
 * Various ways to try and commit sender fraud :)
 */
public class SenderFraudTest {
    private static Gson gson;
    private static EthereumConfig.Server serverConfig;
    private static Web3Connector web3c;
    private static UtilityContract uc;

    @BeforeClass
    public static void setup() {
        gson = new Gson();
        serverConfig = TestUtils.testServerConfig();

        web3c = Web3Connector.initWithConfig(serverConfig);
        uc = web3c.getUtilityContract();
    }

    @Test
    public void testFraudRangeProof() throws Exception {

        // Create a new Client ether account for our use.
        ECKey depositKey = new ECKey();
        String depositAddress = "0x" + ByteUtil.toHexString(depositKey.getAddress());
        TestUtils.fundAccount(depositAddress, web3c);

        int amount = 5;
        TestUtils.doSimpleDeposit(web3c, depositKey, amount);

        // Next, try and create a transfer, but try to fake the T range proof
        ECKey stealthKey = new ECKey();

        BigInteger s = new BigInteger("2");
        BigInteger f = new BigInteger("1");
        BigInteger t = s.add(f);

        ECKey eph = new ECKey();

        String stealthAddress   = "0x" + ByteUtil.toHexString(stealthKey.getAddress());
        StealthTransferData transferData = StealthTransfers.getStealthTransferData(web3c, depositKey, stealthKey,
                eph, BigInteger.valueOf(amount), zero, zero, s, f);
        JsonElement savedTransferData = gson.toJsonTree(transferData);


        BigInteger mixNumber = BigInteger.valueOf(1);
        BigInteger senderNumber = BigInteger.valueOf(0);

        // TEST 1: Try and fake the total pederson commitment, which is index 2
        transferData.t_range_proof.set(2, transferData.t_range_proof.get(2).add(BigInteger.ONE));

        // Note that we have to go straight to the contract, because MixManager will validate the transaction.
        assertFalse(MixerManager.getInstance().submitNewTransaction(transferData));

        // Start the mix
        assertTrue(TestUtils.doMixStart(web3c, mixNumber));

        // Try to publish the range proof. This should fail, as T doesn't match what the range proof is for.
        TransactionReceipt receipt = web3c.getContract().transaction_publish_sender_range_proof(
                mixNumber,
                senderNumber,
                BigInteger.ZERO, // 0 is for range proof of T
                transferData.t_range_proof
        ).send();
        assertEquals("0x0", receipt.getStatus());

        // Clean up the mix
        assertEquals("0x1", web3c.getContract().mix_cancel(mixNumber).send().getStatus());

        // TEST 2: Try and fake one of the pederson commitments, which is indexes 3 ... N+1
        transferData = gson.fromJson(savedTransferData, StealthTransferData.class);

        BigInteger fakeValue = uc.compressPoint(uc.ecMul(uc.getH(), BigInteger.TEN));
        transferData.t_range_proof.set(3, fakeValue);

        assertFalse(MixerManager.getInstance().submitNewTransaction(transferData));

        // Start the mix
        assertTrue(TestUtils.doMixStart(web3c, mixNumber));

        // Publish the range proof. Note that this should succeed, because publishing a wrong proof does nothing
        assertTrue(TestUtils.doMixSenderSteps(web3c, transferData, senderNumber, mixNumber));
        assertChallengeSuccess(web3c, mixNumber, serverConfig.address, transferData.t_range_proof,
                () -> web3c.getContract().verify_sender_proof(mixNumber, senderNumber, BigInteger.valueOf(0)).send(),// T is proof number 0
                () -> web3c.getContract().challenge_sender_proof(mixNumber, senderNumber, BigInteger.valueOf(0)).send()
        );
    }

    @FunctionalInterface
    public interface CheckedFunction<R> {
        R apply() throws Exception;
    }

    private void assertChallengeSuccess(
            Web3Connector web3c, BigInteger mixNumber, String address, List<BigInteger> proof,
            CheckedFunction<Boolean> verifier,
            CheckedFunction<TransactionReceipt> challenge) throws Exception {

        // Verify that the range proof verifies to false
        Boolean verified = verifier.apply();
        assertFalse(verified);

        // ...which means we should be able to challenge it and recieve a bounty.
        // First, fake verify the mix, which will open up the challenges
        TransactionReceipt receipt1 = web3c.getContract().transaction_verify(mixNumber).send();
        TestUtils.processLogs("verify", receipt1);
        assertEquals(BigInteger.valueOf(3), web3c.getContract().transaction_get_status(mixNumber).send());

        assertEquals(Web3Connector.bounty, web3c.getContract().bounty_amount().send());

        TransactionReceipt receipt = challenge.apply();
        TestUtils.processLogs("challenge", receipt);
        assertEquals(BigInteger.ZERO, web3c.getContract().bounty_amount().send());

        // Also assert that the transaction was reverted.
        assertEquals(BigInteger.ZERO, web3c.getContract().transaction_get_status(mixNumber).send());

        // Reset the bounty now that it's been paid out.
        assertEquals("0x1", web3c.getContract().bounty_reset(web3c.getServerConfig().address, Web3Connector.bounty)
                                                            .send().getStatus());
        assertEquals(Web3Connector.bounty, web3c.getContract().bounty_amount().send());
    }



}
