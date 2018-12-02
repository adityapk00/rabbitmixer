package com.rabbitmixer.ethmixer.tests;

import org.junit.BeforeClass;
import org.junit.Test;
import org.web3j.tuples.generated.Tuple3;
import com.rabbitmixer.ethmixer.tests.testutils.TestUtils;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;


import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RangeProofTest {

    private static Web3Connector web3c;
    private static UtilityContract uc;
    private static EthereumConfig.Server serverConfig;

    @BeforeClass
    public static void setUp() {
        serverConfig = TestUtils.testServerConfig();

        // Create a new Client ether account for our use.
        web3c = Web3Connector.initWithConfig(serverConfig);
        uc = web3c.getUtilityContract();
    }


    private List<BigInteger> generateRangeProof(BigInteger v, BigInteger blindingFactor, BigInteger pow, BigInteger offset, int N) throws Exception {
        List<BigInteger> args = new ArrayList<>();
        args.add(v);
        args.add(pow);          // Power of 10
        args.add(offset);
        args.add(blindingFactor);

        for (int i = 0; i < 5 * N - 1; i++) {
            BigInteger r = TestUtils.rndBigint256();
            args.add(r);
        }
        List<BigInteger> proof = uc.generateRangeProof(args);

        return proof;
    }

    private boolean testProof(int v, int b, int pow, int offset, int N) throws Exception {
        return testProof(BigInteger.valueOf(v), BigInteger.valueOf(b), BigInteger.valueOf(pow), BigInteger.valueOf(offset), N);
    }


    private boolean testProof(BigInteger v, BigInteger b, BigInteger pow, BigInteger offset, int N) throws Exception {
        BigInteger commitedV = v.subtract(offset).divide(BigInteger.TEN.pow(pow.intValue()));
        List<BigInteger> proof = generateRangeProof(commitedV, b, pow, offset, N);

        List<BigInteger> proofV = uc.expandPoint(proof.get(2));
        List<BigInteger> calcV = uc.ecAdd(uc.ecMul(uc.getG(), b), uc.ecMul(uc.getH(), v));

        if (!proofV.get(0).equals(calcV.get(0)) || !proofV.get(1).equals(calcV.get(1))) {
            return false;
        }

        return uc.verifyRangeProof(proof);
    }

    @Test
    public void simpleRangeProofTest() throws Exception {
        // 0: 0 should verify
        assertTrue(testProof(0, 0, 0, 0, 4));

        int v = 5;
        int b = 7;
        // 1: NORMAL proof
        assertTrue(testProof(v, b, 0, 0, 4));

        // 2: Out-of bounds proof should be false for N=4
        v = 65;
        assertFalse(testProof(v, b, 0,0, 3)); // 65 is outside N=3

        // 3: ...but true for N=5
        assertTrue(testProof(v, b, 0, 0, 4));

        // 4: ...should verify with offset for the same N=3
        assertTrue(testProof(v, b,0, 2, 3));

        // 5: Power of 10 should not verify
        v = 630;
        assertFalse(testProof(v, b, 0, 0, 3));

        // 6: ... but should work if we add in power of 10
        assertTrue(testProof(v, b, 1, 0, 3));

        // 7: Make sure power of 10 and offset work at the same time. Remember, offset is subtracted first
        v = 632;
        assertFalse(testProof(v, b, 0, 0, 3));
        assertTrue(testProof(v, b, 1, 2, 3));

        // 8: ... which means this is not valid
        v = 650;
        assertFalse(testProof(v, b, 1, 2, 3));

        // 9: ... and should be verified with offset 20 (650 - 20 = 630, then divide by 10 = 63, which fits N=3)
        assertTrue(testProof(v, b, 1, 20, 3));
    }

    private void testRangeProofParamValue(BigInteger value) {
        Tuple3<BigInteger, BigInteger, BigInteger> params = web3c.generateRangeProofParams(value);

        assertEquals(value, params.getValue3().multiply(BigInteger.TEN.pow(params.getValue1().intValue())).add(params.getValue2()));
    }

    @Test
    public void rangeProofParamsTest() {
        testRangeProofParamValue(BigInteger.ZERO);
        testRangeProofParamValue(BigInteger.valueOf(1));
        testRangeProofParamValue(BigInteger.valueOf(2));
        testRangeProofParamValue(BigInteger.valueOf(10));
        testRangeProofParamValue(BigInteger.valueOf(255));
        testRangeProofParamValue(BigInteger.valueOf(256));
        testRangeProofParamValue(BigInteger.valueOf(257));
        testRangeProofParamValue(BigInteger.TEN.pow(18).subtract(BigInteger.ONE));
        testRangeProofParamValue(BigInteger.TEN.pow(18));
        testRangeProofParamValue(BigInteger.TEN.pow(18).add(BigInteger.ONE));
        testRangeProofParamValue(BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(2)));
    }
}
