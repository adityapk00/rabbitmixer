package com.rabbitmixer.ethmixer.web3.utility;

import com.rabbitmixer.ethmixer.contract.Utilities_sol_RingCT;
import org.ethereum.crypto.cryptohash.Keccak256;
import org.ethereum.util.ByteUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static java.lang.System.out;


public class UtilityContract {

    private Utilities_sol_RingCT contract;

    public UtilityContract(Utilities_sol_RingCT contract) {
        assert contract != null;
        this.contract = contract;
    }

    public BigInteger getCurveP() {
        return new BigInteger("30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd47", 16);
    }

    private BigInteger curveN =  new BigInteger("30644e72e131a029b85045b68181585d2833e84879b9709143e1f593f0000001", 16);


    public List<BigInteger> getH() {
        return Arrays.asList(new BigInteger("17856212038068422348937662473302114032147350344021172871924595963388108456668"),
                             new BigInteger("21295818415838735026194046494954432012836335667085206402831343127503290780315"));
    }

    public List<BigInteger> getG() {
        return Arrays.asList(new BigInteger("1"), new BigInteger("2"));
    }

    public List<BigInteger> ecAdd(List<BigInteger> a, List<BigInteger> b) {
        try {
            return contract.ecAdd(a, b).send();
        } catch (Exception e) {
            e.printStackTrace();
            return null; // TODO: Handle this properly.
        }
    }

    public List<BigInteger> ecMul(List<BigInteger> p, BigInteger s) throws Exception {
        return contract.ecMul(p, s).send();
    }

    public List<BigInteger> negate(List<BigInteger> point) {
        return Arrays.asList(point.get(0), getCurveP().subtract(point.get(1)));
    }

    public BigInteger compressPoint(List<BigInteger> p) throws Exception {
        return contract.CompressPoint(p).send();
    }

    public List<BigInteger> expandPoint(BigInteger cp) throws Exception {
        return contract.ExpandPoint(cp).send();
        //return contract.ExpandPoint(cp).send();
    }

    public List<BigInteger> generateRangeProof(List<BigInteger> args) throws Exception {
        return contract.CTGenerateRangeProof(args).send();
    }

    public Boolean verifyRangeProof(List<BigInteger> proof) {
        try {
            return contract.CTVerifyTx(proof).send();
        } catch (Exception e) {
            out.println("Warning: CTVerifyTX returned exception, which means challenges will not work: " + e.getMessage());
            return false;
        }
    }

    public BigInteger modN(BigInteger v) {
        while (v.compareTo(new BigInteger("0")) < 0) { // Negative
            v = v.add(curveN);
        }
        if (v.compareTo(curveN) > 0) {
            v = v.mod(curveN);
        }

        return v;
    }

    /**
     * Return the EC Sum of a list of Compressed points
     */
    public List<BigInteger> ecSumOfCompressed(List<BigInteger> points) throws Exception {
        List<BigInteger> total = Arrays.asList(new BigInteger("0"), new BigInteger("0"));
        for(BigInteger point : points) {
            total = ecAdd(total, expandPoint(point));
        }

        return total;
    }

    public BigInteger encryptFeeAmount(BigInteger f, BigInteger eph, BigInteger feePubpoint) throws Exception {
        List<BigInteger> secret = ecMul(expandPoint(feePubpoint), eph);
        Keccak256 keccak256 = new Keccak256();
        keccak256.update(ByteUtil.bigIntegerToBytes(secret.get(0)));
        keccak256.update(ByteUtil.bigIntegerToBytes(secret.get(1)));
        BigInteger secretKey = new BigInteger(1, keccak256.digest());
        SecretKeySpec keySpec = new SecretKeySpec(ByteUtil.bigIntegerToBytesSigned(secretKey, 32), "AES");
        assert keySpec.getEncoded().length == 32;

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        final String ENCRYPTION_IV = "4e5Wa71fYoT7MFEX";
        IvParameterSpec ivspec = new IvParameterSpec(ENCRYPTION_IV.getBytes("UTF-8"));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivspec);

        /*
        out.println("Secret x=" + secret.get(0).toString(10) + " y=" + secret.get(1).toString(10));
        out.println("SecretKey(post hash)=" + secretKey.toString(10));
        out.println("EncValue=" + f_secret.toString(10));
        out.println("ephPub x=" + ephPub.get(0).toString(10) + " y=" + ephPub.get(1).toString(10));
        */

        BigInteger f_secret = new BigInteger(1, cipher.doFinal(ByteUtil.bigIntegerToBytes(f)));
        return f_secret;
    }
}
