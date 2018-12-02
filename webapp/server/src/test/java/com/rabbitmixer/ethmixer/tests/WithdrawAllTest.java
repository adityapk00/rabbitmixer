package com.rabbitmixer.ethmixer.tests;

import com.rabbitmixer.ethmixer.mixer.MixerManager;
import org.junit.BeforeClass;
import org.junit.Test;
import com.rabbitmixer.ethmixer.tests.testutils.TestAccount;
import com.rabbitmixer.ethmixer.tests.testutils.TestUtils;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;

public class WithdrawAllTest {

    private static EthereumConfig.Server serverConfig;
    private static Web3Connector web3c;
    private static UtilityContract uc;

    @BeforeClass
    public static void setup() {
        serverConfig = TestUtils.testServerConfig();

        // Create a new Client ether account for our use.
        web3c = Web3Connector.initWithConfig(serverConfig);
        uc = web3c.getUtilityContract();

        MixerManager.reconfigure();
    }


    @Test
    public void withdrawAllTestSimple() throws Exception {
        TestAccount acct = new TestAccount(web3c);
        acct.deposit(5);
        acct.assertStealthBalance();

        acct.withdrawAll();
    }

    @Test
    public void withdrawAllAfterTransfer() throws Exception {
        TestAccount acct1 = new TestAccount(web3c);
        acct1.deposit(100);

        TestAccount acct2 = new TestAccount(web3c);
        acct1.transfer(acct2, BigInteger.TEN);
        acct1.waitFull();

        acct1.assertStealthBalance();
        acct2.assertStealthBalance();

        // Withdraw all from account 1 and 2
        acct1.withdrawAll();
        acct2.withdrawAll();
    }

    @Test
    public void withdrawAllAfterFullTransfer() throws Exception {
        TestAccount acct1 = new TestAccount(web3c);
        acct1.deposit(11);                      // Start with 11

        TestAccount acct2 = new TestAccount(web3c);
        acct1.transfer(acct2, BigInteger.TEN);          // Transfer 10 + 1 fees
        acct1.waitFull();

        acct1.assertStealthBalance();
        acct2.assertStealthBalance();

        // Withdraw all from account 1 and 2
        acct1.withdrawAll();
        acct2.withdrawAll();
    }
}
