package com.rabbitmixer.ethmixer.mixer;

import org.web3j.tuples.generated.Tuple3;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.SynchronousQueue;

import static java.lang.System.out;

/**
 * Main class that manages the com.rabbitmixer.ethmixer.mixer
 */
public class MixerManager {
    private static MixerManager instance;

    private final UtilityContract utilityContract;
    private final Web3Connector web3c;

    private final SynchronousQueue<StealthTransferData> queue;
    private final MixerExecutor executor;
    private final Thread mixExecutorThread;

    private MixerManager() {
        web3c = Web3Connector.getInstnace();
        utilityContract = Web3Connector.getInstnace().getUtilityContract();
        queue = new SynchronousQueue<>();

        executor = new MixerExecutor(web3c, queue);

        // Kick off the executor thread.
        mixExecutorThread = new Thread(executor);
        mixExecutorThread.start();
    }


    public static MixerManager getInstance() {
        if (instance == null) {
            instance = new MixerManager();
        }
        return instance;
    }

    /**
     * Reconfigure the mix manager. This should only be needed if the mix parameters change (usually in a test)
     */
    public static MixerManager reconfigure() {
        getInstance().mixExecutorThread.interrupt();
        instance = null;    // So that it gets re-created
        return getInstance();
    }

    private boolean pointEquals(List<BigInteger> a, List<BigInteger> b) {
        if (a.size() != b.size()) return false;
        if (a.size() != 2) return false;

        if(!a.get(0).equals(b.get(0))) return false;
        if(!a.get(1).equals(b.get(1))) return false;

        return true;
    }

    private boolean validateRangeProof(List<BigInteger> T, List<BigInteger> proof) throws Exception {
        if (!utilityContract.verifyRangeProof(proof)) return false;
        // Sum the commitmnets.
        // part 1: That the total in the proof matches the given T
        List<BigInteger> t_total = utilityContract.expandPoint(proof.get(2));
        if (!pointEquals(t_total, T)) return false;

        // part 2: That the components add up
        int N = 4;
        List<BigInteger> t_components = utilityContract.ecSumOfCompressed(proof.subList(3, 3+N-1));
        List<BigInteger> t_last_component = utilityContract.ecAdd(t_total, utilityContract.negate(t_components));
        if (!pointEquals(T, utilityContract.ecAdd(t_components, t_last_component))) return false;

        return true;
    }

    private boolean validateTransaction(StealthTransferData cmd) {
        try {
            // Check to see if this transaction's sender is currently pending a transaction. If yes,
            // then we need to refuse.
            long fromSends = MixStatusManager.getInstance().getStealthTransferStatus(cmd.from_address)
                    .stream().filter(ms -> ms.type == MixStatusManager.StatusType.SENDER)
                    .count();
            if (fromSends > 0) {
                out.println("Refusing transaction because sender already has a transaction pending");
                return false;
            }

            Tuple3<BigInteger, BigInteger, Boolean> tokenBalanceInfo = web3c.getTokenBalanceInfo(cmd.from_address);
            // Make sure sender is not locked
            if (tokenBalanceInfo.getValue3()) {
                out.println("Failed to validate: The sender address is locked");
                return false;
            }
            // Check the nonce
            if (!tokenBalanceInfo.getValue2().equals(cmd.token_nonce)) {
                out.println("Failed to validate correct nonce");
                return false;
            }

            // Check for  T = S + F
            List<BigInteger> T = utilityContract.ecAdd(cmd.S.toList(), cmd.F.toList());
            if (!pointEquals(T, cmd.T.toList())) return false;

            // Verify T range proof
            if (!validateRangeProof(cmd.T.toList(), cmd.t_range_proof)) return false;
            if (!pointEquals(T, utilityContract.expandPoint(cmd.t_range_proof.get(2)))) return false;

            // Verify new balance is what the new bal proof is for
            List<BigInteger> nT = utilityContract.negate(T);
            List<BigInteger> newBal = utilityContract.ecAdd(utilityContract.expandPoint(tokenBalanceInfo.getValue1()), nT);
            if (!pointEquals(newBal, utilityContract.expandPoint(cmd.new_bal_range_proof.get(2)))) {
                out.println("Failed to validate that the new balance is what the range proof is for.");
                return false;
            }

            // Verify new_balance range proof.
            if (!utilityContract.verifyRangeProof(cmd.new_bal_range_proof)) return false;

            // Verify the S range proof
            if(!validateRangeProof(cmd.S.toList(), cmd.s_range_proof)) return false;
            if (!pointEquals(cmd.S.toList(), utilityContract.expandPoint(cmd.s_range_proof.get(2)))) return false;

            // TODO: THESE ARE ALL DEBUG CHECKS. Not to be in prod
            // Check F = f_blinding.G + f.H
            List<BigInteger> F1 = utilityContract.ecMul(utilityContract.getG(), cmd.f_blinding_factor);
            List<BigInteger> F2 = utilityContract.ecMul(utilityContract.getH(), cmd.debug_f);
            List<BigInteger> F  = utilityContract.ecAdd(F1, F2);
            if (!pointEquals(F, cmd.F.toList())) return false;

            // Check S = s_blinding.G + s.H
            List<BigInteger> S1 = utilityContract.ecMul(utilityContract.getG(), cmd.debug_s_blinding);
            List<BigInteger> S2 = utilityContract.ecMul(utilityContract.getH(), cmd.debug_s);
            List<BigInteger> S  = utilityContract.ecAdd(S1, S2);
            if (!pointEquals(S, cmd.S.toList())) return false;

            // Check T = t_blinding.G + T.H
            List<BigInteger> T1 = utilityContract.ecMul(utilityContract.getG(), cmd.debug_t_blinding);
            List<BigInteger> T2 = utilityContract.ecMul(utilityContract.getH(), cmd.debug_t);
            List<BigInteger> Tother = utilityContract.ecAdd(T1, T2);
            if (!pointEquals(Tother, cmd.T.toList())) return false;

            // TODO: reciever hash
        } catch (Exception e) {
            e.printStackTrace();
            return  false;
        }

        return true;
    }



    /**
     * Submits a new transfer to the queue.
     *
     * This method is synchronized, because we want a single point to check if this transfer already exists, and
     * if it does, reject it.
     * If this method is allowed to be called from multiple threads, it might result in adding the same StealthTransfer
     * to the queue 2 times.
     */
    synchronized public boolean submitNewTransaction(StealthTransferData transfer) throws Exception {
        // First step is to validate the Transaction
        if (!validateTransaction(transfer)) {
            out.println("Couldn't validate transfer");
            return false;
        }

        // Add it to the queue to be handled by the executor
        queue.put(transfer);
        MixStatusManager.getInstance().newStealthTransfer(transfer);

        return true;
    }
}
