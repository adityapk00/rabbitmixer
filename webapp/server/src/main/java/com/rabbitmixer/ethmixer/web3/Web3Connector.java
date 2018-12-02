package com.rabbitmixer.ethmixer.web3;

import com.rabbitmixer.ethmixer.contract.SecureToken_sol_SecureToken;
import com.rabbitmixer.ethmixer.logger.SeriousErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tuples.generated.Tuple3;
import com.rabbitmixer.ethmixer.web3.dev.DevAccountFiller;
import com.rabbitmixer.ethmixer.web3.utility.UtilityContract;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static java.lang.System.out;

public class Web3Connector {
    private static Logger logger = LoggerFactory.getLogger(Web3Connector.class);

    private final EthereumConfig.Server serverConfig;

    private Web3EventsListeners eventListener;

    private static Web3Connector instance;
    public static Web3Connector getInstnace() {
        if (instance == null) {
            throw new RuntimeException("Web3Connector not initialized");
        }
        return instance;
    }

    private Web3j web3;
    private ExecutorService pool;

    private SecureToken_sol_SecureToken contract = null;
    private UtilityContract utilityContract;

    public static final BigInteger bounty = BigInteger.valueOf(10).pow(18).multiply(BigInteger.TEN); // 10 ether

    private Web3Connector(EthereumConfig.Server serverConfig) {
        this.pool = Executors.newCachedThreadPool();
        this.web3 = Web3j.build(
                new HttpService(serverConfig.serverAddress),
                serverConfig.web3GethPollInterval,
                Executors.newScheduledThreadPool(5));  // defaults to http://localhost:8545/
        try {
            Web3ClientVersion version = web3.web3ClientVersion().send();
            logger.info("Connected to Ethereum Node with Version : " + version.getWeb3ClientVersion());
        } catch (IOException e) {
            SeriousErrors.logger.error("Could not connect to Ether Node. Exiting.", e);
            System.exit(1);
        }


        this.serverConfig = serverConfig;
        if(serverConfig.network.equals("dev")) {
            DevAccountFiller.fillAccount(this, serverConfig.address);
        }

        // First, create the Utility Contract
        //utilityContract = new UtilityContract(com.rabbitmixer.ethmixer.web3, serverConfig);
        if (serverConfig.utilityContractAddress != null) {
            utilityContract = new UtilityContract(ContractDeployer.loadUtilityContract(web3, serverConfig));
        }
        if (utilityContract  == null && serverConfig.network.equals("dev")) {
            utilityContract = new UtilityContract(ContractDeployer.deployUtilityContract(web3, serverConfig));
        }

        if (serverConfig.contractAddress != null) {
            contract = ContractDeployer.loadContract(web3, serverConfig);
        }
        if (contract == null && serverConfig.network.equals("dev")) {
            contract = ContractDeployer.deployContract(web3, serverConfig);
        }

        if (contract == null || utilityContract == null) {
            SeriousErrors.logger.error("All Contracts could be found or created. Exiting.");
            System.exit(1);
        }

        // And set the server's config to the contract address.
        serverConfig.contractAddress = contract.getContractAddress();
        this.eventListener = new Web3EventsListeners(web3, contract, utilityContract);
        this.eventListener.createAllEventListeners();

        logger.info("Web3Connector configured successfully");
    }

    public UtilityContract getUtilityContract() {
        return utilityContract;
    }

    public EthereumConfig.Server getServerConfig() {
        return serverConfig;
    }


    private static Random random = new Random();

    private BigInteger rndBigint256() {
        BigInteger r;
        do {
            // TODO: 250 bits because of some overflow issues when using 256 bits
            r = new BigInteger(250, random);
        } while (r.compareTo(BigInteger.ZERO) <= 0);
        return r;
    }

    public Tuple3<BigInteger, BigInteger, BigInteger> generateRangeProofParams(BigInteger amount) {
        // For a given N, calculate pow and offset.
        int N = 4; // TODO: N should come from the client, maybe?
        String nStr = BigInteger.valueOf(4).pow(N).toString(10);

        String amtStr = amount.toString(10);
        // Try with as many significant bits as nStr
        BigInteger sigDigits = new BigInteger(amtStr.substring(0, Math.min(amtStr.length(), nStr.length())));
        if (sigDigits.compareTo(new BigInteger(nStr)) >= 0) {
            // Try with one less
            sigDigits = new BigInteger(amtStr.substring(0, Math.min(amtStr.length(), nStr.length() - 1)));
        }
        String offsetStr  = amount.toString(10).substring(sigDigits.toString(10).length());
        int pow    = offsetStr.length();
        BigInteger offset = offsetStr.isEmpty() ? BigInteger.ZERO : new BigInteger(offsetStr);

        // Then make sure it all lines up
        assert sigDigits.multiply(BigInteger.TEN.pow(pow)).add(offset).equals(amount);

        return new Tuple3<>(BigInteger.valueOf(pow), offset, sigDigits);
    }

    public List<BigInteger> generateRangeProof(BigInteger v, BigInteger pow, BigInteger offset, BigInteger blindingFactor) throws Exception {
        List<BigInteger> args = new ArrayList<>();

        // The v should be adjusted based on offset and pow.
        BigInteger adjV = v.subtract(offset).divide(BigInteger.TEN.pow(pow.intValue()));
        if (! adjV.multiply(BigInteger.TEN.pow(pow.intValue())).add(offset).equals(v)) {
            throw new Exception("Pow and Offset didn't adjust correctly");
        }
        if (adjV.compareTo(BigInteger.ZERO) < 0) {
            throw new Exception("Negative adjusted V");
        }

        int N = 4;  // The number of base-4 bits
        if (adjV.compareTo(new BigInteger("4").pow(N)) >= 0) {
            throw new Exception("Number is too big to fit in N=" + N);
        }

        args.add(adjV);
        args.add(pow);
        args.add(offset);
        args.add(blindingFactor);
        for (int i = 0; i < 5 * N - 1; i++) {
            BigInteger r = rndBigint256();
            args.add(r);
        }
        List<BigInteger> proof = utilityContract.generateRangeProof(args);

        // Before we return the proof, just make sure that it verifies as well
        Boolean verified = contract.CTVerifyTx(proof).send();
        if (!verified) {
            throw new Exception("Generated proof didn't verify!");
        }

        UtilityContract uc = getUtilityContract();
        List<BigInteger> proofV = uc.expandPoint(proof.get(2));
        List<BigInteger> calcV = uc.ecAdd(uc.ecMul(uc.getG(), blindingFactor), uc.ecMul(uc.getH(), v));
        if (!proofV.get(0).equals(calcV.get(0)) || !proofV.get(1).equals(calcV.get(1))) {
            throw new Exception("Generated proof is proving a different value");
        }

        return proof;
    }

    public String getNonceHex(String address) throws IOException {
        EthGetTransactionCount resp = web3.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).send();
        if (resp.hasError()) {
            out.println("Error while calling transaction count");
            throw new ConnectException("Error connecting to com.rabbitmixer.ethmixer.web3 provider:" + resp.getError().getMessage());
        }
        return "0x" + resp.getTransactionCount().toString(16);
    }

    public String getEtherBalance(String address) throws IOException {
        EthGetBalance resp = web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
        if (resp.hasError()) {
            out.println("Error while calling transaction count");
            throw new ConnectException("Error connecting to com.rabbitmixer.ethmixer.web3 provider:" + resp.getError().getMessage());
        }
        return resp.getBalance().toString(10);
    }


    public Tuple3<BigInteger, BigInteger, Boolean> getTokenBalanceInfo(String addr) throws ConnectException {
        try {
            return contract.balances(addr).send();
        } catch (Exception e) {
            out.println("Error while calling getTokenBalance" + e.getMessage());
            throw new ConnectException("Error while calling getTokenBalance" + e.getMessage());
        }
    }

    public String executeRawTx(String rawTx) throws IOException {
        EthSendTransaction resp = web3.ethSendRawTransaction(rawTx).send();
        if (resp.hasError()) {
            out.println("Error while sending raw TX");
            throw new ConnectException("Error executing rawTX:" + resp.getError().getMessage());
        }

        return resp.getTransactionHash();
    }

    public <T> T executeSingle(Callable<T> callable) throws InterruptedException {
        return executeParallelBatch(Collections.singletonList(callable)).get(0);
    }

    public <T> List<T> executeParallelBatch(List<Callable<T>> callables) throws InterruptedException {
        return pool.invokeAll(callables).stream()
                .map(x -> {
                    try {
                        return x.get();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }).collect(Collectors.toList());
    }

    /**
     * Initialize the com.rabbitmixer.ethmixer.web3 connector with a server config.
     */
    public static Web3Connector initWithConfig(EthereumConfig.Server serverConfig) {
        instance = new Web3Connector(serverConfig);
        return instance;
    }

    public Web3j getWeb3() {
        return web3;
    }

    public SecureToken_sol_SecureToken getContract() {
        return contract;
    }
}
