package com.rabbitmixer.ethmixer.mixer;

import com.rabbitmixer.ethmixer.logger.SeriousErrors;
import com.rabbitmixer.ethmixer.mixer.MixerExecutor.MixTransaction;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class actually executes the Tx's that make up a mix on the Ethereum blockchain.
 * It takes care of serializing/parallelizing the Txs, waiting for them to confirm and
 * retries etc...
 */
public class MixTxExecutor {
    private static MixTxExecutor instance;

    /** Create via Builder */
    private MixTxExecutor() {
    }

    public static MixTxExecutor getInstance() {
        if (instance == null) {
            instance = new MixTxExecutor();
        }
        return instance;
    }

    public void submitTxSet(TxSet txSet) {
        TxStep step = txSet.steps.get(0);
        step.doStep();
    }




    public static class Builder {
        private final TxSet txSet;

        public Builder() {
            txSet = new TxSet();
        }

        public Builder addStep(List<RemoteCall<TransactionReceipt>> txs, MixTransaction data,
                               BiFunction<TransactionReceipt, MixTransaction, Boolean> successCallback) {
            assert txs.size() == 1;
            TxStep step = new TxStep();
            step.data = data;
            step.txs = txs;
            step.successCallback = successCallback;
            step.set = txSet;

            if (!txSet.steps.isEmpty()) {
                txSet.steps.get(txSet.steps.size() - 1).nextStep = step;
            }
            txSet.steps.add(step);

            return this;
        }

        public Builder addVerificationStep(Supplier<Boolean> verifier, MixTransaction data,
                                           BiFunction<TransactionReceipt, MixTransaction, Boolean> successCallback) {
            TxVerificationStep step = new TxVerificationStep();
            step.data = data;
            step.verifier = verifier;
            step.successCallback = successCallback;
            step.set = txSet;

            return this;
        }


        public Builder addSuccessCallback(Function<MixTransaction, Boolean> callback) {
            txSet.successCallback = callback;
            return this;
        }


        public Builder addFailureCallback(BiFunction<Throwable, MixTransaction, Boolean> callback) {
            txSet.failureCallback = callback;
            return this;
        }

        public TxSet build() {
            return txSet;
        }
    }

    public static class TxSet {
        List<TxStep> steps;

        TxSet() {
            steps = new ArrayList<>();
        }

        Function<MixTransaction, Boolean> successCallback;
        BiFunction<Throwable, MixTransaction, Boolean> failureCallback;
    }



    public static class TxStep {
        List<RemoteCall<TransactionReceipt>> txs;
        MixTransaction data;

        TxStep nextStep;
        BiFunction<TransactionReceipt, MixTransaction, Boolean> successCallback;

        TxSet set;

        TxStep() {
            txs = new ArrayList<>();
        }

        void proceedToNextStep() {
            if (nextStep != null) {
                nextStep.doStep();
            } else {
                if (set.successCallback != null)
                    set.successCallback.apply(data);
            }
        }

        void doStep() {
            assert txs.size() == 1;
            txs.get(0).observable().subscribe(
                    receipt -> {
                        // Process Logs first
                        if (receipt.getStatus().equals("0x0")) {
                            // Failed, so call the set's failure callback.
                            SeriousErrors.logger.error("Step failed for mix: " + data.mixNumber);
                            if (set.failureCallback != null)
                                set.failureCallback.apply(null, data);
                            else
                                SeriousErrors.logger.error("Couldn't find the TxSet's failure callback. Mix probably needs to be cleaned up");
                        } else {
                            try {
                                successCallback.apply(receipt, data);
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                            proceedToNextStep();
                        }
                    },
                    error -> {
                        if (set.failureCallback != null)
                            set.failureCallback.apply(error, data);
                    }
            );
        }
    }

    public static class TxVerificationStep extends TxStep {
        Supplier<Boolean> verifier;
        TxVerificationStep() {
        }

        void doStep() {
            // Call all the verifier to see if we should proceed
            if (!verifier.get()) {
                // Failure, call the failure callback
                if (set.failureCallback != null)
                    set.failureCallback.apply(new Exception("Verifier Error"), data);
            } else {
                // All verifier passed, so go to the next step.
                successCallback.apply(null, data);
                proceedToNextStep();
            }
        }
    }
}
