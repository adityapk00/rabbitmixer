package com.rabbitmixer.ethmixer.web3;

import com.rabbitmixer.ethmixer.contract.SecureToken_sol_SecureToken;
import com.rabbitmixer.ethmixer.contract.Utilities_sol_RingCT;
import com.rabbitmixer.ethmixer.logger.SeriousErrors;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.RawTransactionManager;

import java.io.IOException;
import java.math.BigInteger;

import static com.rabbitmixer.ethmixer.contract.Utilities_sol_RingCT.load;
import static java.lang.System.out;
import static com.rabbitmixer.ethmixer.web3.Web3Connector.bounty;

public class ContractDeployer {

    static Utilities_sol_RingCT loadUtilityContract(Web3j web3, EthereumConfig.Server serverConfig) {
        Utilities_sol_RingCT contract = Utilities_sol_RingCT.load(
                serverConfig.utilityContractAddress,
                web3,
                new RawTransactionManager(web3, Credentials.create(serverConfig.privateKey), 40, serverConfig.web3GethPollInterval),
                new BigInteger("1"),
                new BigInteger("4500000")
        );
        try {
            if (!contract.isValid()) {
                SeriousErrors.logger.error("Loaded Utility Contract is not valid!");
                return null;
            } else {
                out.println("Utility Contract found. loading from: " + contract.getContractAddress());
                return contract;
            }
        } catch (IOException e) {
            SeriousErrors.logger.error("Unknown exception", e);
            return null;
        }
    }

    public static Utilities_sol_RingCT deployUtilityContract(Web3j web3, EthereumConfig.Server serverConfig) {
        out.println("Creating utility contract!");
        try {
            Utilities_sol_RingCT contract = Utilities_sol_RingCT.deploy(web3,
                    new RawTransactionManager(web3, Credentials.create(serverConfig.privateKey), 40, serverConfig.web3GethPollInterval),
                    new BigInteger("1"),        // TODO: Set the gas price appropriately
                    new BigInteger("4500000")
            ).send();

            if (!contract.isValid()) {
                SeriousErrors.logger.error("Deployed contract was not valid!");
                return null;
            }

            serverConfig.utilityContractAddress = contract.getContractAddress();
            out.println("Utility Contract is at:" + serverConfig.utilityContractAddress);

            return contract;
        } catch (Exception e) {
            SeriousErrors.logger.error("Unknown exception", e);
            return null;
        }
    }

    static SecureToken_sol_SecureToken loadContract(Web3j web3, EthereumConfig.Server serverConfig) {
        // Attempt to load contract
        SecureToken_sol_SecureToken contract = SecureToken_sol_SecureToken.load(
                serverConfig.contractAddress,
                web3,
                new RawTransactionManager(web3, Credentials.create(serverConfig.privateKey), 40, serverConfig.web3GethPollInterval),
                new BigInteger("1"),
                new BigInteger("4500000")
        );
        try {
            if (!contract.isValid()) {
                SeriousErrors.logger.error("Loaded Contract is not valid!");
                return null;
            } else {
                out.println("Contract found. loading from: " + contract.getContractAddress());
                return contract;
            }
        } catch (IOException e) {
            SeriousErrors.logger.error("Unknown exception", e);
            return null;
        }
    }

    public static SecureToken_sol_SecureToken deployContract(Web3j web3, EthereumConfig.Server serverConfig) {
        out.println("Creating contract!");
        try {
            SecureToken_sol_SecureToken contract = SecureToken_sol_SecureToken.deploy(web3,
                    new RawTransactionManager(web3, Credentials.create(serverConfig.privateKey), 40, serverConfig.web3GethPollInterval),
                    new BigInteger("1"),        // TODO: Set the gas price appropriately
                    new BigInteger("4500000"),
                    bounty
            ).send();

            if (!contract.isValid()) {
                SeriousErrors.logger.error("Deployed contract was not valid!");
                return null;
            }

            if (contract.bounty_amount().send().equals(BigInteger.ZERO)) {
                SeriousErrors.logger.error("Deployed contract's bounty is not valid");
                return null;
            }

            serverConfig.contractAddress = contract.getContractAddress();
            out.println("Contract is at:" + serverConfig.contractAddress);

            return contract;
        } catch (Exception e) {
            SeriousErrors.logger.error("Unknown exception", e);
            return null;
        }
    }

}
