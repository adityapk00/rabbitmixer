package com.rabbitmixer.ethmixer.tests.testutils;

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.cryptohash.Keccak256;
import org.ethereum.util.ByteUtil;
import org.web3j.tuples.generated.Tuple3;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;
import java.util.List;

import static com.rabbitmixer.ethmixer.tests.testutils.TestUtils.assertEqualsPoint;

public class StealthTransfers {
    public static BigInteger zero   = new BigInteger("0");

    private static String paddedHex(BigInteger token_nonce) {
        String pad = "0000000000000000000000000000000000000000000000000000000000000000";
        String ans = pad + token_nonce.toString(16);
        return ans.substring(ans.length() - 64, ans.length());
    }

    public static BigInteger getTokenTBlindingFactor(ECKey fromKey, BigInteger fromNonce) {
        Keccak256 keccak256 = new Keccak256();
        keccak256.update(new Keccak256().digest(fromKey.getPrivKeyBytes()));
        keccak256.update(new Keccak256().digest("blinding_factor".getBytes()));
        keccak256.update(ByteUtil.bigIntegerToBytes(fromNonce));
        return Web3Connector.getInstnace().getUtilityContract().modN(new BigInteger(1, keccak256.digest()));
    }

    public static StealthTransferData getStealthTransferData(Web3Connector web3c, ECKey fromKey, ECKey toKey, ECKey eph,
                                                             BigInteger fromCurrentBalance, BigInteger fromNonce, BigInteger fromBlinding,
                                                             BigInteger s, BigInteger f) throws Exception {
        return createStealthTransfer(web3c, fromKey, toKey, eph, fromCurrentBalance, fromNonce, fromBlinding,
                                     s, f, false);
    }

    private static StealthTransferData createStealthTransfer(Web3Connector web3c, ECKey fromKey, ECKey toKey, ECKey eph,
                                                             BigInteger fromCurrentBalance, BigInteger fromNonce, BigInteger fromBlinding,
                                                             BigInteger s, BigInteger f, boolean isTransparent) throws Exception {
        UtilityContract uc = web3c.getUtilityContract();
        StealthTransferData transferData = new StealthTransferData();

        String from = "0x" + ByteUtil.toHexString(fromKey.getAddress());
        String to   = "0x" + ByteUtil.toHexString(toKey.getAddress());

        BigInteger t = s.add(f);

        BigInteger new_bal = fromCurrentBalance.subtract(t);

        BigInteger s_blinding_factor;
        if (isTransparent) {
            transferData.s_amount = s;
            s_blinding_factor = new BigInteger("0");
            transferData.eph_pub = new StealthTransferData.Point(zero, zero);
        } else {
            // Not a transparent transfer
            transferData.s_amount = null;
            s_blinding_factor = new BigInteger("2"); // this is from ecdh
            transferData.eph_pub = new StealthTransferData.Point(uc.ecMul(uc.getG(), eph.getPrivKey()));
        }

        BigInteger t_blinding_factor = getTokenTBlindingFactor(fromKey, fromNonce);
        BigInteger f_blinding_factor = uc.modN(t_blinding_factor.subtract(s_blinding_factor));

        List<BigInteger> T = uc.ecAdd(uc.ecMul(uc.getG(), t_blinding_factor), uc.ecMul(uc.getH(), t));
        List<BigInteger> S = uc.ecAdd(uc.ecMul(uc.getG(), s_blinding_factor), uc.ecMul(uc.getH(), s));
        List<BigInteger> F = uc.ecAdd(uc.ecMul(uc.getG(), f_blinding_factor), uc.ecMul(uc.getH(), f));

        assertEqualsPoint(T, uc.ecAdd(S, F));

        Tuple3<BigInteger, BigInteger, BigInteger> tParams = web3c.generateRangeProofParams(t);
        List<BigInteger> t_range_proof = web3c.generateRangeProof(t, tParams.getValue1(), tParams.getValue2(), t_blinding_factor);

        Tuple3<BigInteger, BigInteger, BigInteger> sParams = web3c.generateRangeProofParams(s);
        List<BigInteger> s_range_proof = web3c.generateRangeProof(s, sParams.getValue1(), sParams.getValue2(), s_blinding_factor);

        Tuple3<BigInteger, BigInteger, BigInteger> nbParams = web3c.generateRangeProofParams(new_bal);
        BigInteger new_bal_blinding = uc.modN(fromBlinding.subtract(t_blinding_factor));
        List<BigInteger> new_bal_range_proof = web3c.generateRangeProof(new_bal, nbParams.getValue1(), nbParams.getValue2(), new_bal_blinding);

        List<BigInteger> toPubPoint = uc.ecMul(uc.getG(), toKey.getPrivKey());

        transferData.S = new StealthTransferData.Point(S);
        transferData.T = new StealthTransferData.Point(T);
        transferData.F = new StealthTransferData.Point(F);

        transferData.f_blinding_factor = f_blinding_factor;

        transferData.from_address = from;
        transferData.new_bal_range_proof = new_bal_range_proof;

        // reciever_hash
        // token_nonce
        // signature

        transferData.t_range_proof = t_range_proof;
        transferData.s_range_proof = s_range_proof;

        transferData.to_address = to;

        // Don't bother with encryption, just send the values directly
        transferData.t_secret = t;
        transferData.s_secret = s;

        transferData.debug_s = s;
        transferData.debug_f = f;
        transferData.debug_s_blinding = s_blinding_factor;
        transferData.debug_new_balance = new_bal;
        transferData.debug_t_blinding = t_blinding_factor;
        transferData.debug_t = t;

        Keccak256 keccak256 = new Keccak256();
        keccak256.update(ByteUtil.hexStringToBytes(to));
        keccak256.update(ByteUtil.hexStringToBytes(paddedHex(S.get(0))));
        keccak256.update(ByteUtil.hexStringToBytes(paddedHex(S.get(1))));
        keccak256.update(ByteUtil.hexStringToBytes(paddedHex(new BigInteger("0")))); // Randomness
        byte[] recieverHash = keccak256.digest();
        transferData.reciever_hash = ByteUtil.toHexString(recieverHash);

        transferData.token_nonce = fromNonce;

        keccak256 = new Keccak256();
        keccak256.update(ByteUtil.hexStringToBytes(from));
        keccak256.update(ByteUtil.hexStringToBytes(paddedHex(transferData.token_nonce)));
        keccak256.update(ByteUtil.hexStringToBytes(paddedHex(T.get(0))));
        keccak256.update(ByteUtil.hexStringToBytes(paddedHex(T.get(1))));
        keccak256.update(recieverHash);
        byte[] msgHash = keccak256.digest();

        keccak256 = new Keccak256();
        keccak256.update( "\u0019Ethereum Signed Message:\n32".getBytes());
        keccak256.update(msgHash);
        ECKey.ECDSASignature sign = fromKey.sign(keccak256.digest());

        transferData.signature = new StealthTransferData.Signature(BigInteger.valueOf((int)sign.v), sign.r, sign.s);

        return transferData;
    }

    public static StealthTransferData getStealthWithdrawalData(Web3Connector web3c, ECKey fromKey, ECKey toKey, BigInteger
            fromCurrentBalance, BigInteger fromNonce, BigInteger fromBlinding, BigInteger s, BigInteger f) throws Exception {
        return createStealthTransfer(web3c, fromKey, toKey, null, fromCurrentBalance, fromNonce, fromBlinding,
                                     s, f, true);
    }
}
