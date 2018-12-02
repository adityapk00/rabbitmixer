package com.rabbitmixer.ethmixer.tests.testutils;

import com.rabbitmixer.ethmixer.contract.SecureToken_sol_SecureToken;
import com.google.common.io.BaseEncoding;
import com.rabbitmixer.ethmixer.db.Storage;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple3;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.web3.dev.DevAccountFiller;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.lang.System.out;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.rabbitmixer.ethmixer.tests.testutils.StealthTransfers.zero;

public class TestUtils {
    public static byte[] gasPrice = ByteUtil.intToBytesNoLeadZeroes(1);
    public static byte[] gasLimit = ByteUtil.intToBytesNoLeadZeroes(4500000);

    public static void doSimpleDeposit(Web3Connector web3c, ECKey depositKey, int amount) throws Exception {

        // Create a new Client ether account for our use.
        String depositAddress = "0x" + ByteUtil.toHexString(depositKey.getAddress());

        String nonceHex = web3c.getNonceHex(depositAddress);

        UtilityContract uc = web3c.getUtilityContract();
        BigInteger balance = BigInteger.valueOf(amount);
        Transaction tx = new Transaction(
                ByteUtil.hexStringToBytes(nonceHex),
                gasPrice,
                gasLimit,
                ByteUtil.hexStringToBytes(web3c.getServerConfig().contractAddress), // toAddress - the contract
                ByteUtil.bigIntegerToBytes(balance),  // value - Amount to deposit
                ByteUtil.hexStringToBytes("d0e30db0") // data - the deposit() method
        );
        tx.sign(depositKey);
        web3c.executeRawTx("0x" + BaseEncoding.base16().encode(tx.getEncoded()));

        await().atMost(5, TimeUnit.SECONDS).until(() -> Storage.getHistory(depositAddress).size()> 0);

        assertEquals(Storage.getHistory(depositAddress).get(0).type,  "DEPOSIT");
        assertEquals(Storage.getHistory(depositAddress).get(0).amount, balance);
        assertEquals(Storage.getHistory(depositAddress).get(0).addr,   depositAddress);

        BigInteger depositBlinding = zero;  // Blinding factor for a deposit is 0
        assertStealthBalance(depositAddress, balance, depositBlinding);
    }

    public static void fundAccount(String depositAddress, Web3Connector web3c) throws Exception {
        DevAccountFiller.fillAccount(web3c, depositAddress);
        await().atMost(5, TimeUnit.SECONDS).until(() -> !web3c.getEtherBalance(depositAddress).equals("0"));

    }

    public static void processLogs(String stepname, TransactionReceipt receipt) {
        out.println("\nStep: " + stepname);
        out.println("Debug Logs:");
        out.println("Status=" + receipt.getStatus() + " Gas Consumed=" + receipt.getGasUsed() + "Hash=" + receipt.getTransactionHash());
    }

    public static <T extends Storage.StorageItem> T waitAndGetHistoryItem(String address, int itemNumber) {
        await().atMost(5, TimeUnit.SECONDS).until(() -> Storage.getHistory(address).size() > itemNumber);
        return (T) Storage.getHistory(address).get(itemNumber);
    }

    private static Random random = new Random();
    public static BigInteger rndBigint256() {
        BigInteger r;
        do {
            // TODO: 250 bits because of some overflow issues when using 256 bits
            r = new BigInteger(250, random);
        } while (r.compareTo(zero) <= 0);
        return r;
    }

    public static EthereumConfig.Server testServerConfig() {
        EthereumConfig.Server server = new EthereumConfig.Server();
        server.serverAddress = "http://localhost:8545";
        server.privateKey = "2a592c186829f61c1efba42c5c4eba28906d4ae7f9e301efdaa2135bab879703";
        server.network = "dev";
        server.web3GethPollInterval = 25;
        server.mixSize = 1; // Mix size is 1 by default, override if needed.

        return server;
    }

    public static void assertEqualsPoint(List<BigInteger> a, List<BigInteger> b) {
        assertEquals(a.get(0), b.get(0));
        assertEquals(a.get(1), b.get(1));
    }

    public static void assertNotEqualsPoint(List<BigInteger> a, List<BigInteger> b) {
        assertTrue(!a.get(0).equals(b.get(0)) || !a.get(1).equals(b.get(1)));
    }

    public static void assertStealthBalance(String address, BigInteger balance, BigInteger blinding) throws Exception {
        UtilityContract uc = Web3Connector.getInstnace().getUtilityContract();

        List<BigInteger> calcBalance = uc.ecAdd(uc.ecMul(uc.getG(), blinding), uc.ecMul(uc.getH(), balance));
        Tuple3<BigInteger, BigInteger, Boolean> contractBalance = Web3Connector.getInstnace().getTokenBalanceInfo(address);

        assertEqualsPoint(calcBalance,
                uc.expandPoint(contractBalance.getValue1()));
    }

    public static BigInteger CompressPoint(List<BigInteger> point) {
        //Store x value
        BigInteger Pout = point.get(0);
        BigInteger ECSignMask = new BigInteger("8000000000000000000000000000000000000000000000000000000000000000", 16);

        //Determine Sign
        if (point.get(1).testBit(0)) {
            Pout = Pout.or(ECSignMask);
        }

        return Pout;
    }


    public static boolean doMixStart(Web3Connector web3c, BigInteger transactionNumber) {
        SecureToken_sol_SecureToken contract = web3c.getContract();
        try {
            // Step 1: Start the transcation
            TransactionReceipt receipt = contract.transaction_start_preparing(transactionNumber).send();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }


    public static boolean doMixSenderSteps(Web3Connector web3c, StealthTransferData cmd, BigInteger senderNumber, BigInteger transaction_number) throws Exception {
        SecureToken_sol_SecureToken contract = web3c.getContract();

        // Send the sender info
        contract.transaction_publish_sender_proof(
                cmd.from_address,
                transaction_number,
                cmd.T.x, cmd.T.y,
                cmd.token_nonce,
                bytes32(new BigInteger(cmd.reciever_hash, 16)),
                cmd.signature.v,
                bytes32(cmd.signature.r),
                bytes32(cmd.signature.s)
        ).send();

        // Step 3: Publish the two range proofs
        contract.transaction_publish_sender_range_proof(
                transaction_number,
                senderNumber,
                new BigInteger("0"),
                cmd.t_range_proof
        ).send();

        contract.transaction_publish_sender_range_proof(
                transaction_number,
                senderNumber,
                new BigInteger("1"),
                cmd.new_bal_range_proof
        ).send();

        return true;
    }


    public static byte[] bytes32(BigInteger num) throws Exception {
        byte[] byteArray = num.toByteArray();
        if (byteArray.length == 32) {
            return byteArray;
        } else if (byteArray.length > 32 && byteArray[0] == 0) {
            // BigInteger.toByteArray() annoyingly returns an extra 0 byte at the start for some numbers.
            return Arrays.copyOfRange(byteArray, 1, 33);
        } else if (byteArray.length < 32) {
            // Pad with 0s
            byte[] ans = new byte[32];
            int padsize = 32 - byteArray.length;
            Arrays.fill(ans, 0, padsize, (byte) 0);
            System.arraycopy(byteArray, 0, ans, padsize, byteArray.length);
            return ans;
        } else {
            throw new Exception("Array size > 32");
        }
    }

}
