package com.rabbitmixer.ethmixer.websockets;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.eclipse.jetty.websocket.api.Session;
import com.rabbitmixer.ethmixer.web3.Web3Connector;
import com.rabbitmixer.ethmixer.web3.commands.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static java.lang.System.out;

public class MessageRouter {

    public class ClientCommand {
        String          command;
        String          id;
        JsonElement     params;
    }

    private HashMap<String, AbstractCommand> commandMap;
    private MessageRouter() {
        commandMap = new HashMap<>();
        initCommands();
    }

    private static MessageRouter instance = null;
    public static MessageRouter getInstnace() {
        if (instance == null) {
            instance = new MessageRouter();
        }
        return instance;
    }

    public void route(Session session, String message) {
        // Messages are parsed and routed through to the individual handlers
        ClientCommand clientCommand = new Gson().fromJson(message, ClientCommand.class);
        AbstractCommand command = commandMap.get(clientCommand.command);
        if (command != null) {
            command.handleCommand(session, clientCommand.id, clientCommand.params);
        } else {
            // Invalid command.
            out.println("Client sent invalid command:" + clientCommand.command);
            session.close();
        }
    }


    private void initCommands() {
        List<AbstractCommand> commands = new ArrayList<>(Arrays.asList(
                new EtherAddressDetailsCommand("ether_address_details"),
                new ExecuteRawTransactionCommand("execute_rawtx"),
                new ClientConfigCommand("client_config"),
                new TokenDetailsCommand("token_address_details"),
                new TokenAddressHistoryCommand("token_address_history"),
                new TokenGenerateRangeProofCommand("token_generate_range_proof"),
                new DoTransactionCommand("do_transaction"),
                new RegisterAddress("register_address"),
                new MixStatusCommand("mix_status")
        ));

        if (Web3Connector.getInstnace().getServerConfig().network.equals("dev")) {
            commands.add(new DevSendMeMoneyCommand("send_money"));
        }

        commands.forEach(c -> {
            commandMap.put(c.getCommandName(), c);
        });

        out.println("Websocket Message Router initialized");
    }
}
