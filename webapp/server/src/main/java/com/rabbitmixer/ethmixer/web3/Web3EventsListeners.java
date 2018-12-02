package com.rabbitmixer.ethmixer.web3;

import com.rabbitmixer.ethmixer.contract.SecureToken_sol_SecureToken;
import com.rabbitmixer.ethmixer.db.Storage;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.Contract;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;
import com.rabbitmixer.ethmixer.websockets.ClientNotifier;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static java.lang.System.out;

public class Web3EventsListeners {

    private final Web3j web3;
    private final SecureToken_sol_SecureToken contract;
    private final UtilityContract utilityContract;

    Web3EventsListeners(Web3j web3, SecureToken_sol_SecureToken contract, UtilityContract utilityContract) {
        this.web3 = web3;
        this.contract = contract;
        this.utilityContract = utilityContract;
    }

    private interface IEventCallback {
        void op(EventValues eventValues, Log log);
    }

    private void createEventListner(String eventName,
                                    List<TypeReference<?>> typeReferences, List<TypeReference<?>> references,
                                    IEventCallback callbackfn) {
        final Event event = new Event(eventName, typeReferences, references);

        // TODO: by specifing start = EARLIST, we're going to get all the historical deposit events as well
        EthFilter filter = new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contract.getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(event));
        web3.ethLogObservable(filter).forEach(log -> {
            EventValues eventValues = Contract.staticExtractEventParameters(event, log);
            callbackfn.op(eventValues, log);
        });
    }

    public void createAllEventListeners() {

        createEventListner("BRMDepositTokenCompleteEvent",
                Arrays.asList(),
                Arrays.asList(new TypeReference<Address>() {
                }, new TypeReference<Uint256>() {
                }),
                (eventValues, log) -> {
                    String addr = (String) eventValues.getNonIndexedValues().get(0).getValue();
                    BigInteger amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();

                    out.println("Recieved Deposit Event for " + addr + ":" + amount);
                    try {
                        BigInteger timestamp = web3.ethGetBlockByHash(log.getBlockHash(), false).send().getBlock().getTimestamp();
                        Storage.depositComplete(addr, amount, log.getBlockNumber(), log.getTransactionHash(), timestamp);

                        // And then notify all the Sessions that this address got a deposit
                        ClientNotifier.getInstance().doDepositNotification(addr, amount);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        createEventListner("BRMSenderTransactionCompleteEvent",
                Arrays.asList(),
                Arrays.asList(new TypeReference<Address>() {
                }, new TypeReference<Uint256>() {
                }),
                (eventValues, log) -> {
                    String addr = (String) eventValues.getNonIndexedValues().get(0).getValue();
                    BigInteger amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();

                    out.println("Recieved Sender Event for " + addr + ":" + amount);

                    try {
                        BigInteger timestamp = web3.ethGetBlockByHash(log.getBlockHash(), false).send().getBlock().getTimestamp();
                        Storage.sendComplete(addr, amount, log.getBlockNumber(), log.getTransactionHash(), timestamp);

                        // And then notify all the Sessions that this address's send was complete
                        ClientNotifier.getInstance().doSendNotification(addr, amount);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                });

        createEventListner("BRMRecieverTransactionCompleteEvent",
                Arrays.asList(),
                Arrays.asList(new TypeReference<Address>() {
                }, new TypeReference<Uint256>() {
                }, new TypeReference<Uint256>() {
                }),
                (eventValues, log) -> {
                    String addr = (String) eventValues.getNonIndexedValues().get(0).getValue();
                    BigInteger amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                    BigInteger ephPub = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();

                    out.println("Recieved Reciever event for " + addr + ":" + amount);

                    try {
                        //List<BigInteger> ephPubPoint = UtilityContract.convertFromUint256List(contract.ExpandPoint(ephPub).send());
                        List<BigInteger> ephPubPoint = utilityContract.expandPoint(ephPub);
                        BigInteger timestamp = web3.ethGetBlockByHash(log.getBlockHash(), false).send().getBlock().getTimestamp();

                        Storage.recieveComplete(addr, amount, ephPubPoint, log.getBlockNumber(), log.getTransactionHash(), timestamp);

                        ClientNotifier.getInstance().doRecieveNotification(addr, amount);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }
}
