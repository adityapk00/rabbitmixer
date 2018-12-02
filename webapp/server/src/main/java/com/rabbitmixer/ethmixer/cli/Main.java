package com.rabbitmixer.ethmixer.cli;

import com.rabbitmixer.ethmixer.contract.SecureToken_sol_SecureToken;
import com.rabbitmixer.ethmixer.contract.Utilities_sol_RingCT;
import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.rabbitmixer.ethmixer.logger.SeriousErrors;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import com.rabbitmixer.ethmixer.web3.ContractDeployer;
import com.rabbitmixer.ethmixer.web3.EthereumConfig;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.websockets.WebSocketCommandHandler;

import java.util.Arrays;
import java.util.concurrent.Executors;

import static java.lang.System.err;

public class Main {

    public static class NetworkValidator implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            if (!Arrays.asList("dev", "rinkeby", "main").contains(value))
                throw new ParameterException("Unknown network: " + value);
        }
    }

    @Parameter(names = {"-n", "--network"},
            required = true,
            validateWith = NetworkValidator.class,
            description = "Ethereum Network to bind to. {dev, rinkeby, main}")
    private String network;

    @Parameter(names = {"-c", "--contractAddress"},
            description = "Contract address. If this is not specified, and the network is set to 'dev', then the contract will be created")
    private String contractAddress;

    @Parameter(names = {"-u", "--utilityContract"},
            description = "Utility Contract address. If this is not specified, and the network is set to 'dev', then the contract will be created")
    private String utilityContractAddress;


    @Parameter(names = {"-p", "--privatekey"},
            required = true,
            description = "Ethereum Private Key for the server's account. This is the account that transactions are sent from.")
    private String privateKey;

    @Parameter(names = {"-a", "--address"},
            required = true,
            description = "Ethereum Address for the server's account. This is the account that transactions are sent from.")
    private String address;

    @Parameter(names = {"-i", "--pollinterval"},
            description = "Polling interval = block time")
    private int pollInterval;

    @Parameter(names = {"-s", "--mixsize"},
            description = "Number of Stealth transfers to batch in a mix")
    private Integer mixSize = 1;

    @Parameter(names = {"--deploy"},
            description = "Only Deploy the contracts and exit")
    private boolean deployOnly = false;


    public static void main(String[] args) throws Exception {
        // Write a line in the Log file to easily distinguish app restarts
        SeriousErrors.logger.error("----------Restart------------");

        Main main = new Main();
        try {
            JCommander.newBuilder()
                    .addObject(main)
                    .build()
                    .parse(args);
            EthereumConfig.Server server = new EthereumConfig.Server();

            server.privateKey = main.privateKey;
            server.address =  main.address;
            server.network = main.network;

            int pollinterval = main.pollInterval;

            if (server.network.equals("dev") && pollinterval == 0) {
                server.web3GethPollInterval = 50; // 50 ms for dev
            } else if (pollinterval != 0) {
                server.web3GethPollInterval = pollinterval;
            } else {
                throw new ParameterException("Need Poll Interval");
            }

            if (main.deployOnly) {
                Web3j web3 = Web3j.build(
                        new HttpService(server.serverAddress),
                        server.web3GethPollInterval,
                        Executors.newScheduledThreadPool(5));  // defaults to http://localhost:8545/

                SecureToken_sol_SecureToken contract = ContractDeployer.deployContract(web3, server);
                Utilities_sol_RingCT utilityContract = ContractDeployer.deployUtilityContract(web3, server);

                System.out.println("-c " + contract.getContractAddress() + " -u " + utilityContract.getContractAddress());
                System.exit(1);
            } else {
                server.contractAddress = main.contractAddress;
                server.mixSize = main.mixSize;
                server.contractAddress = main.contractAddress;
                server.utilityContractAddress = main.utilityContractAddress;

                Web3Connector.initWithConfig(server);

                startWebsocketServer();
            }
        } catch (ParameterException pe) {
            SeriousErrors.logger.error(pe.getMessage());
            err.println(pe.getMessage());
            pe.getJCommander().usage();
            System.exit(1);
        }
    }

    private static void startWebsocketServer() throws Exception {
        Server server = new Server(18080);
        WebSocketHandler wsHandler = new WebSocketHandler() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.register(WebSocketCommandHandler.class);
            }
        };
        server.setHandler(wsHandler);
        server.start();
        server.join();
    }
}
