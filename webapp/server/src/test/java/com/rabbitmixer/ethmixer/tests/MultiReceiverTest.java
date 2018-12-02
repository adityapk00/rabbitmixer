package com.rabbitmixer.ethmixer.tests;

import com.google.gson.Gson;
import com.rabbitmixer.ethmixer.mixer.MixerManager;
import org.junit.Test;
import com.rabbitmixer.ethmixer.tests.testutils.TestAccount;
import com.rabbitmixer.ethmixer.tests.testutils.TestUtils;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;

import static junit.framework.TestCase.fail;

public class MultiReceiverTest {
    private static Gson gson;
    private static EthereumConfig.Server serverConfig;
    private static Web3Connector web3c;
    private static UtilityContract uc;
    private static MixerManager mm;

    public void configureWithMixSize(int mixsize) {
        gson = new Gson();
        serverConfig = TestUtils.testServerConfig();
        serverConfig.mixSize = mixsize;

        web3c = Web3Connector.initWithConfig(serverConfig);
        uc = web3c.getUtilityContract();

        MixerManager.reconfigure();
        mm = MixerManager.getInstance();
    }

    @Test
    public void testWithMixsizes() throws Exception {
        configureWithMixSize(1);
        doTest();

        configureWithMixSize(2);
        doTest();

        configureWithMixSize(4);
        doTest();
    }

    private void doTest() throws Exception {
        ArrayList<TestAccount> deposits = new ArrayList<>();
        for (int i=0; i < 4; i++) {
            TestAccount deposit = new TestAccount(web3c);
            deposits.add(deposit);
        }

        // Send all the deposits at once
        deposits.forEach(deposit -> {
            try {
                deposit.deposit(100);
            } catch (IOException e) {
                fail("Some exception");
            }
        });

        TestAccount stealth = new TestAccount(web3c);
        //  And then, transfer money to the target account
        deposits.forEach(deposit -> {
            try {
                deposit.transfer(stealth, BigInteger.ONE);
            } catch (Exception e) {
                fail("Some Exception");
            }
        });


        // And then wait for all the deposits
        deposits.forEach(TestAccount::waitFull);

        deposits.forEach(testAccount -> {
            try {
                testAccount.assertStealthBalance();
            } catch (Exception e) {
                fail("Some Exception");
            }
        });
        stealth.assertStealthBalance();
    }
}
