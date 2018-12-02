package com.rabbitmixer.ethmixer.mixer;

import com.rabbitmixer.ethmixer.contract.SecureToken_sol_SecureToken;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple3;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.data.StealthTransferData;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.SynchronousQueue;
import java.util.stream.Collectors;

import static java.lang.System.out;

public class MixerExecutor implements Runnable {

    private final SynchronousQueue<StealthTransferData> queue;

    // The transactions are in a stack, because we always want to use the same mixNumber, to make
    // as much savings as possible on gas executions.
    private final BlockingDeque<MixTransaction> mixTransactionStack = new LinkedBlockingDeque<>();

    private final UtilityContract utilityContract;
    private final Web3Connector web3c;

    MixerExecutor(Web3Connector web3c, SynchronousQueue<StealthTransferData> q) {
        this.queue = q;
        this.web3c = web3c;
        this.utilityContract = web3c.getUtilityContract();

        // Prepare the mixTransactions by adding the number of parallel mix transactions to the stack.
        mixTransactionStack.offer(new MixTransaction(BigInteger.valueOf(1), web3c.getServerConfig().mixSize));
    }

    private byte[] bytes32(BigInteger num) {
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
            throw new RuntimeException("Array size > 32");
        }
    }

    private void processLogs(String stepname, TransactionReceipt receipt) {
        out.println("\nStep: " + stepname);
        out.println("Debug Logs:");
        out.println("Status=" + receipt.getStatus() + " Gas Consumed=" + receipt.getGasUsed() + "Hash=" + receipt.getTransactionHash());
    }


    @Override
    public void run() {
        SecureToken_sol_SecureToken contract = web3c.getContract();

        while (true) {
            try {
                // Wake up to see if there are any transfers pending in the queue.
                StealthTransferData transaction = queue.take();
                if (contract.balances(transaction.from_address).send().getValue3()) {
                    // Locked, so we're going to do nothing.
                    out.println("Address " + transaction.from_address + " appears to be locked. Skipping");
                    // TODO: Notify the client that the transaction failed?
                    continue;
                }

                // We need to get hold of a mixNumber from the stack to participate in it.
                final MixTransaction currentMix = mixTransactionStack.take();

                // OK, some transfer has been initiated. We need to
                MixTxExecutor.Builder builder = new MixTxExecutor.Builder();
                if (!currentMix.isStarted()) {
                    // doMixStart()
                    builder.addStep(Collections.singletonList(
                                        contract.transaction_start_preparing(currentMix.mixNumber)),
                            currentMix,
                            (receipt, data) -> {
                                    MixStatusManager.getInstance().updateStealthTransfer(transaction, MixStatusManager.MixStatusName.MIX_START, "Done", receipt.getTransactionHash());
                                    currentMix.startTxHash = receipt.getTransactionHash();
                                    processLogs("start mix", receipt);
                                    return true;
                                }
                            );
                } else {
                    // The mix has already started, so set the status to done
                    MixStatusManager.getInstance().updateStealthTransfer(transaction, MixStatusManager.MixStatusName.MIX_START, "Done", currentMix.startTxHash);
                }

                builder.addStep(Collections.singletonList(
                                    contract.transaction_publish_sender_proof(
                                        transaction.from_address,
                                        currentMix.mixNumber,
                                        transaction.T.x, transaction.T.y,
                                        transaction.token_nonce,
                                        bytes32(new BigInteger(transaction.reciever_hash, 16)),
                                        transaction.signature.v,
                                        bytes32(transaction.signature.r),
                                        bytes32(transaction.signature.s))),
                        currentMix,
                        (receipt, data) -> {
                                MixStatusManager.getInstance().updateStealthTransfer(transaction, MixStatusManager.MixStatusName.SENDER_PROOF, "Done", receipt.getTransactionHash());

                                processLogs("sender proof", receipt);
                                return true;
                        }
                );

                builder.addStep(Collections.singletonList(
                        contract.transaction_publish_sender_range_proof(
                                currentMix.mixNumber,
                                BigInteger.valueOf(currentMix.mixParticipants.size()), // SenderNumber
                                BigInteger.ZERO,    // Proof number. 0 = T range proof
                                transaction.t_range_proof
                        )),
                        currentMix,
                        (receipt, data) -> {
                            MixStatusManager.getInstance().updateStealthTransfer(transaction, MixStatusManager.MixStatusName.T_RANGE_PROOF, "Done", receipt.getTransactionHash());
                            processLogs("range proof T", receipt);
                            return true;
                        }

                );
                builder.addStep(Collections.singletonList(
                        contract.transaction_publish_sender_range_proof(
                                currentMix.mixNumber,
                                BigInteger.valueOf(currentMix.mixParticipants.size()), // SenderNumber
                                BigInteger.ONE,    // Proof number. 1 = new_balance range proof
                                transaction.new_bal_range_proof
                        )),
                        currentMix,
                        (receipt, data) -> {
                            MixStatusManager.getInstance().updateStealthTransfer(transaction, MixStatusManager.MixStatusName.NEW_BAL_RANGE_PROOF, "Done", receipt.getTransactionHash());

                            processLogs("range proof new_bal", receipt);
                            return true;
                        }
                );


                //web3c.doMixSenderSteps();
                currentMix.mixParticipants.add(transaction);

                if (currentMix.reachedCapacity()) {
                    // TODO: The MixManager should calculate all the Lists that are in these functions and then shuffle and
                    // send them, otherwise they are linkable.
                    currentMix.sortCurrentMix();

                    for(int i = 0; i < currentMix.recieverRangeProofs.size()-1; i++) {
                        final int rangeProofNumber = i;
                        builder.addStep(Collections.singletonList(
                                contract.transaction_publish_reciever_range_proof(
                                        currentMix.mixNumber,
                                        new BigInteger(Integer.toString(i)),     // Reciever range proof number
                                        currentMix.recieverRangeProofs.get(i))),
                                currentMix,
                                (receipt, data) -> {
                                    // TODO This will set the reciever range proof to the last txHash. Is that weird?
                                    // Because, it may not belong to the sender, and all senders will get the same
                                    // last txHash.
                                    currentMix.mixParticipants.forEach(tdata ->
                                            MixStatusManager.getInstance().updateStealthTransfer(
                                                    tdata,
                                                    MixStatusManager.MixStatusName.RECIEVER_RANGE_PROOF,
                                                    "Done",
                                                    receipt.getTransactionHash()));

                                    processLogs("reciever range proof " + rangeProofNumber, receipt);
                                    return true;
                                }
                        );
                    }

                    builder.addStep(Collections.singletonList(
                            contract.transaction_publish_reciever_proof(
                                    currentMix.mixNumber,
                                    currentMix.toAddresses,
                                    currentMix.Svalues,
                                    currentMix.transparentValues)),
                            currentMix,
                            (receipt, data) -> {
                                currentMix.mixParticipants.forEach(tdata ->
                                        MixStatusManager.getInstance().updateStealthTransfer(
                                                tdata, MixStatusManager.MixStatusName.RECIEVER_PROOF, "Done", receipt.getTransactionHash())
                                );

                                processLogs("reciever proof", receipt);
                                return true;
                            }
                    );

                    for (int i= 0; i < currentMix.mixParticipants.size(); i++) {
                        final int mixTx = i;
                        builder.addVerificationStep(() -> {
                                try {
                                    if (!contract.verify_sender_proof(currentMix.mixNumber, BigInteger.valueOf(mixTx), BigInteger.valueOf(0)).send()) return false;
                                    if (!contract.verify_sender_proof(currentMix.mixNumber, BigInteger.valueOf(mixTx), BigInteger.valueOf(1)).send()) return false;;
                                    if (!contract.verify_reciever_proof(currentMix.mixNumber, BigInteger.valueOf(mixTx)).send()) return false;;
                                    if (!contract.verify_mix_amounts(currentMix.mixNumber).send()) return false;
                                    return true;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    return false;
                                }
                            },
                            currentMix,
                            (receipt, data) -> {
                                out.println("Verified potential challengers");
                                return true;
                            }
                        );
                    }

                    builder.addStep(Collections.singletonList(
                            contract.transaction_verify(currentMix.mixNumber)),
                            currentMix,
                            (receipt, data) -> {
                                currentMix.mixParticipants.forEach(tdata ->
                                        MixStatusManager.getInstance().updateStealthTransfer(
                                                tdata, MixStatusManager.MixStatusName.VERIFICATION, "Done", receipt.getTransactionHash())
                                );

                                processLogs("transaction verify", receipt);
                                return true;
                            }
                    );

                    builder.addStep(Collections.singletonList(
                                        contract.transaction_execute(
                                                currentMix.mixNumber,
                                                currentMix.senderSentAmounts,
                                                currentMix.recieverAmounts,
                                                currentMix.recieverEphPubs)),
                            currentMix,
                            (receipt, data) -> {
                                currentMix.mixParticipants.forEach(tdata ->
                                        MixStatusManager.getInstance().removeStealthTransfer(tdata)
                                );
                                currentMix.reset();

                                processLogs("transaction execute", receipt);
                                return true;
                            }
                    );
                }

                // Add a final success step to return the mix to the pool so it can execute the next transaction
                builder.addSuccessCallback(mixTransactionStack::offer);

                // TODO: Handle failure better
                builder.addFailureCallback( (t, mtx) -> {
                    out.println("MixTransaction failed, trying to rollback");
                    try {
                        TransactionReceipt receipt = contract.mix_cancel(mtx.mixNumber).send();
                        processLogs("transaction cancel", receipt);
                        currentMix.mixParticipants.forEach(tdata ->
                                MixStatusManager.getInstance().removeStealthTransfer(tdata)
                        );
                        currentMix.reset();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    mixTransactionStack.offer(mtx);
                    return true;
                });
                MixTxExecutor.getInstance().submitTxSet(builder.build());

            } catch (InterruptedException e) {
                out.println("Thread was interrupted, shutting down");
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class MixTransaction {
        BigInteger                                  mixNumber;
        List<StealthTransferData>                   mixParticipants;

        List<String>            toAddresses;
        List<BigInteger>        Svalues;
        List<BigInteger>        transparentValues;
        List<List<BigInteger>>  recieverRangeProofs;
        List<BigInteger>        senderSentAmounts;
        List<BigInteger>        recieverAmounts;
        List<BigInteger>        recieverEphPubs;

        final int capacity;
        String startTxHash;

        MixTransaction(BigInteger mixNumber, int capacity) {
            this.mixNumber = mixNumber;
            this.capacity = capacity;

            reset();
        }

        /**
         * Organize the current list of participants
         */
        private void sortCurrentMix() throws Exception {
            // Build up the data structures.
            // TODO: Fee Address
            String feeAddress = "0xc61c8891b7e9432f04cdcc2bd15883f6348016db";
            BigInteger feePubpoint = new BigInteger("8751429586501367185178460627595453415037756910476150211890545322259188945241");

            // Collect the stealth transfers first
            for (StealthTransferData transaction: this.mixParticipants) {
                if (!transaction.isTransparent()) {
                    toAddresses.add(transaction.to_address);
                    Svalues.add(utilityContract.compressPoint(transaction.S.toList()));
                    recieverRangeProofs.add(transaction.s_range_proof);
                    recieverAmounts.add(transaction.s_secret);
                    recieverEphPubs.add(utilityContract.compressPoint(transaction.eph_pub.toList()));
                }
            }

            // Fee will be a stealth transfer
            List<BigInteger> totalF = this.mixParticipants.stream().map(t -> t.F.toList()).reduce(
                    Arrays.asList(BigInteger.ZERO, BigInteger.ZERO),
                    utilityContract::ecAdd);
            BigInteger total_f_blinding = utilityContract.modN(this.mixParticipants.stream().map(t -> t.f_blinding_factor).reduce(
                    BigInteger.ZERO, BigInteger::add));
            BigInteger total_f = this.mixParticipants.stream().map(t -> t.debug_f).reduce(
                    BigInteger.ZERO, BigInteger::add);

             // TODO: assert totalF = G.blinding + H.f

            toAddresses.add(feeAddress);
            Svalues.add(utilityContract.compressPoint(totalF));

            byte[] rndBytes = new byte[32];
            new Random().nextBytes(rndBytes);
            BigInteger eph = utilityContract.modN(new BigInteger(1, rndBytes));
            List<BigInteger> ephPub = utilityContract.ecMul(utilityContract.getG(), eph);
            BigInteger f_secret = utilityContract.encryptFeeAmount(total_f, eph, feePubpoint);

            Tuple3<BigInteger, BigInteger, BigInteger> rangeProofParams = web3c.generateRangeProofParams(total_f);
            recieverRangeProofs.add(Web3Connector.getInstnace().generateRangeProof(
                    total_f,
                    rangeProofParams.getValue1(),
                    rangeProofParams.getValue2(),
                    total_f_blinding));
            recieverAmounts.add(f_secret);
            recieverEphPubs.add(utilityContract.compressPoint(ephPub));
            // TODO: Save total_f_blinding somewhere

            // Then, add the transparent transfers
            // TODO: There's some issue with Solidity where we can't pass 0-length arrays. So, if there's no transparent
            // transaction in a mix, this errors out. So we add a dummy 0 at the start (even if there are transparent
            // transactions). Horrible
            transparentValues.add(BigInteger.ZERO);
            for (StealthTransferData transaction: this.mixParticipants) {
                if (transaction.isTransparent()) {
                    toAddresses.add(transaction.to_address);
                    transparentValues.add(transaction.s_amount);
                }
            }

            // The sender amounts are in the same order as the mix participants, so no reordering necessary
            senderSentAmounts = this.mixParticipants.stream().map(t -> t.t_secret).collect(Collectors.toList());

            // TODO: Do we need to randomize here?
        }

        boolean reachedCapacity() {
            return mixParticipants.size() >= capacity;
        }

        /**
         * Called after a mix transaction is complete to reset all the Lists.
         */
        void reset() {
            mixParticipants            = new ArrayList<>();
            toAddresses                = new ArrayList<>();
            Svalues                    = new ArrayList<>();
            transparentValues          = new ArrayList<>();
            recieverRangeProofs        = new ArrayList<>();
            senderSentAmounts          = new ArrayList<>();
            recieverAmounts            = new ArrayList<>();
            recieverEphPubs            = new ArrayList<>();
        }

        boolean isStarted() {
            return mixParticipants.size() > 0;
        }
    }

}
