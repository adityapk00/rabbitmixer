package com.rabbitmixer.ethmixer.tests;

import static org.junit.Assert.assertEquals;
import static com.rabbitmixer.ethmixer.tests.testutils.TestUtils.assertEqualsPoint;

import org.junit.Test;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class UtilityTest {
    private BigInteger zero = new BigInteger("0");
    private BigInteger one  = new BigInteger("1");

    private List<BigInteger> zeroPoint = Arrays.asList(zero, zero);



    @Test
    public void basicECMath() throws Exception {
        UtilityContract uc = Web3Connector.getInstnace().getUtilityContract();

        List<BigInteger> ans = uc.ecMul(uc.getG(), zero);
        assertEqualsPoint(ans, zeroPoint);

        List<BigInteger> onePoint = uc.ecMul(uc.getG(), one);
        assertEqualsPoint(onePoint, uc.getG());


        List<BigInteger> two1 = uc.ecAdd(onePoint, onePoint);
        List<BigInteger> two2 = uc.ecMul(uc.getG(), new BigInteger("2"));
        assertEqualsPoint(two1, two2);
    }

    @Test
    public void pointCompression() throws Exception {
        UtilityContract uc = Web3Connector.getInstnace().getUtilityContract();

        assertEqualsPoint(uc.getG(), uc.expandPoint(uc.compressPoint(uc.getG())));
        assertEqualsPoint(uc.getH(), uc.expandPoint(uc.compressPoint(uc.getH())));
        assertEqualsPoint(zeroPoint, uc.expandPoint(uc.compressPoint(zeroPoint)));

        List<BigInteger> bigPoint = uc.ecMul(uc.getG(), new BigInteger("1024"));
        List<BigInteger> bigPoint2 = uc.expandPoint(uc.compressPoint(bigPoint));
        assertEqualsPoint(bigPoint, bigPoint2);

        BigInteger knownCompressedPoint = new BigInteger("5742946746831988922780203318051412492843241882443824782923333380121435753551");
        List<BigInteger> knownPoint = uc.expandPoint(knownCompressedPoint);
        assertEqualsPoint(knownPoint, Arrays.asList(
                new BigInteger("5742946746831988922780203318051412492843241882443824782923333380121435753551"),
                new BigInteger("6204641297128831320038834081327386630596332732276822536510174207082504299878")));
        assertEquals(knownCompressedPoint, uc.compressPoint(knownPoint));
    }
}
