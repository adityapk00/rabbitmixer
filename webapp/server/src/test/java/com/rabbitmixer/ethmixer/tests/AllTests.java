package com.rabbitmixer.ethmixer.tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        BasicStealthTransferTest.class,
        MultiMixStealthTransferTest.class,
        WithdrawAllTest.class,
        MultiReceiverTest.class,
        AutodepositTest.class,
        StealthTransferVerificationTest.class,
        SenderFraudTest.class,
        RangeProofTest.class,
        UtilityTest.class
})

public class AllTests {
}
