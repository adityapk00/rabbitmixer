import * as crypto        from "crypto";

import {websocket}      from "./websocket";
import {config   }      from "./config";
import {model    }      from "./model";
import {view     }      from "./view";
import {privatekeys}    from "./privatekey";
import {bn128    }      from "./bn_128";

import {BigNumber}      from "bignumber.js";

/**
 * Controller for the UI to talk to the Web service. 
 */
class Controller {
    
    public init() : void {
        websocket.connect();
    }

    public connected() : void {
        // Connected. Now, get the client config
        var me = this;


        websocket.send(
            {"command": "client_config", "params": {"config": ""}},
            function(data) {
                //console.log("Setting Config");
                config.setConfig(data["response"]);
                me.registerAllAddresses(3);
                me.loadAllAddressDetails(3);
            }
        );
    }

    /**
     * DEV MODE ONLY
     * Ask the server for some money to make testing easier. 
     */
    public askServerForMoney(address: string): void {
        var me = this;
        websocket.send(
            {"command": "send_money", "params": {"address": address}},
            function(data) {
                console.log("Server sent us some money! Yay!");
            }
        );
    }

    public registerAllAddresses(count: number = 3): void {        
        // We'll also register for notifications for each of the addresses
        var addresses = [privatekeys.getPrimaryAddress()];
        for (var i=0; i < count; i++) {
            addresses = addresses.concat(privatekeys.getStealthKey(i)["address"]);
        } 

        addresses.forEach(address => {
            websocket.send(
                {"command": "register_address", "params": {"address": address}},
                function(data) {
                    // Ignore
                }
            )    
        });        
    }

    public postLoadCalls = [];
    public loadAllAddressDetails(count: number = 3) : void {
        // Set up the callbacks so that when all the addresses have been loaded, we'll 
        // update the view
        var current_count = 0;
        var me = this;

        var finishedCallback = function(address) {
            current_count++;
            if (current_count == count + 1) {   // Sheilded + Primary
                view.updateTable();
                me.postLoadCalls.forEach(c => c());
                me.postLoadCalls = [];
            }
        };

        this.loadAddressDetails(privatekeys.getPrimaryAddress(), finishedCallback);
        for (var i=0; i < count; i++) {
            var address = privatekeys.getStealthKey(i)["address"];
            this.loadAddressDetails(address, finishedCallback);
        }

    }

    /**
     * Make parallel calls to the websocket, collect all the responses, 
     * and then call the callback.
     * TODO: Also add an error callback
     */
    private multiCall(params: Object[], callback: (data_list: Object[]) => void): void {
        var total_calls     = params.length;
        var returned_calls  = 0;
        var responses       = [];

        var id_mappings = {};
        
        var handler = function(data) {
            returned_calls++;
            responses[id_mappings[data.id]] = data;

            if (returned_calls == total_calls) {
                callback(responses);
            }
        }

        for (var i=0; i < params.length; i++) {
            var idused = websocket.send(params[i], handler);
            id_mappings[idused.toString()] = i;
        };
    }

    public loadAddressDetails(address: string = "", callback: (address: string)  => void) : void {
        if (address == null || address == "") throw "loadAddress() needs an address";

        var calls = [
            {"command": "ether_address_details", "params": {"address": address}},
            {"command": "token_address_history", "params": {"address": address}},
            {"command": "token_address_details", "params": {"address": address}},
            {"command": "mix_status",            "params": {"address": address}}
        ];

        this.multiCall(calls, function(data_list: Object[]) {
            model.setEtherDetails(data_list[0]["response"]);
            model.setTokenHistory(data_list[1]["response"]);
            model.setTokenDetails(data_list[2]["response"]);       
            model.setMixStatus(data_list[3]["response"]);

            // Make sure to verify the balances for this address
            model.verifyTokenBalance(address);

            callback(address);
        });
    }

    /**
     * Deposit the given amount from the address into the same address. 
     * @param amount in Wei. 
     */
    public deposit(fromAddress: string, amount: BigNumber) : void {        
        var rawTx = privatekeys.signDepositContractCall(fromAddress, amount, model.getAccountDetails(fromAddress).ether_nonce);

        var me = this;
        websocket.send(
            {"command": "execute_rawtx", "params": {"rawtx": rawTx}},
            function(data) {
                console.log("Deposited. Waiting for server to send confirmation...");
            }
        );
    }

    public notification(response: Object): void {
        if (response["notification"] == "deposit_complete") {
            // Reload to show the new balances
            this.loadAllAddressDetails();
        } else if (response["notification"] == "send_complete") {
            // Reload to show the new balances
            this.loadAllAddressDetails();
        } else if (response["notification"] == "recieve_complete") {
            this.loadAllAddressDetails();
        } else if (response["notification"] == "stealth_transfer_pending") {
            this.loadAllAddressDetails();
        }
    }

    public getRangeProof(amount: BigNumber, blinding_factor: BigNumber) : void {
        websocket.send(
            {"command": "token_generate_range_proof", params: {"v": amount.toString(), "blinding_factor": blinding_factor.toString(10)}},
            function(data) {
                console.log("Range proof: " + data["response"]);
            }
        );
    }

    public tokenTransferTransparent(fromaddr: string, toaddr: string, sending_amount: BigNumber): void {
        this.tokenTransfer(fromaddr, toaddr, sending_amount, true);
    }

    private getRangeProofParams(amount: BigNumber, blinding: BigNumber) {
        // For a given N, calculate pow and offset.
        var N = 4; // TODO: Should be adjustable.
        var nStr = new BigNumber(4).pow(N).toString(10);
        
        var amtStr = amount.toString(10);
        // Try with as many significant bits as nStr
        var sigDigits = new BigNumber(amtStr.substr(0, Math.min(amtStr.length, nStr.length)));
        if (sigDigits.isGreaterThanOrEqualTo(new BigNumber(nStr))) {
            // Try with one less
            sigDigits = new BigNumber(amtStr.substr(0, Math.min(amtStr.length, nStr.length - 1)));
        }
        var offsetStr = amount.toString(10).substr(sigDigits.toString(10).length);
        var pow       = offsetStr.length;
        var offset    = offsetStr == "" ? 0 : new BigNumber(offsetStr);

        // Then make sure it all lines up
        if (!sigDigits.multipliedBy(new BigNumber(10).pow(pow)).plus(offset).eq(amount)) {
            throw new Error("Assertion error while calculating pow, offset");
        }

        var ans= {"v": amount.toString(), 
                "blinding_factor": blinding.toString(10), 
                "pow": pow.toString(10), 
                "offset": offset.toString(10)};
        return ans;
    }

    /**
     * Auto token transfer from the deposit address to a sheilded address. Its the same as a token transfer,
     * but we need to check if one is already pending before firing it off.     
     */
    public autoTokenTransfer(fromaddr: string, toaddr: string, sending_amount: BigNumber, transparent: boolean = false): void {
        if (model.getAccountDetails(fromaddr).mix_status != null && 
            model.getAccountDetails(fromaddr).mix_status["length"] > 0 && 
            model.getAccountDetails(fromaddr).mix_status[0].type == "SENDER") {
                console.log("Already has a pending transfer, waiting");
                return;
        }

        this.tokenTransfer(fromaddr, toaddr, sending_amount, transparent);
    }

    public tokenTransfer(fromaddr: string, toaddr: string, sending_amount: BigNumber, transparent: boolean = false): void {
        // We need to create several objects to begin a transfer transaction.
        var me = this;

        var s_amount = sending_amount;
        var f_amount = model.getCurrentFee();        
        var t_amount = s_amount.plus(f_amount);

        if (!f_amount.plus(s_amount).eq(t_amount)) throw new Error("f + s != t");

        // Step 1: Generate a new ephemeral private key that we will pack in the transaction. 
        var eph_pk: Buffer          = crypto.randomBytes(256/8);
        var eph_pub: BigNumber[]    = bn128.privateKeyToPubkey(eph_pk);

        // Step 2: Generate ECDH secret key with the toaddr's public key
        // TODO : What if the address is from somewhere else, and not known to us?
        var to_pubkey              = bn128.privateKeyToPubkey(privatekeys.getAccountFromAddress(toaddr).privatekey);
        var secret_key: BigNumber  = new BigNumber(bn128.ecdh(eph_pk, to_pubkey).toString('hex'), 16);

        // Step 3: The blinding factor for S will be the ecdh secret_key, so that the to address can also derive it
       var t_blinding_factor   = bn128.modN(privatekeys.getTokenTBlindingFactorForNonce(fromaddr, model.getAccountDetails(fromaddr).token_nonce.toNumber()));
       var s_blinding_factor;
       if (transparent == false) {
           s_blinding_factor = bn128.modN(secret_key);
       } else {
           s_blinding_factor = new BigNumber(0);
       }

       var f_blinding_factor   = bn128.modN(t_blinding_factor.minus(s_blinding_factor));

       var new_blinding_factor = bn128.modN(model.getAccountDetails(fromaddr).token_blinding_factor.minus(t_blinding_factor));

        // Step 4: Generate the pederson blinding_factors for T and F. F's will need to be shared with the mixer.
        if (!s_blinding_factor.plus(f_blinding_factor).mod(bn128.curveN).eq(t_blinding_factor)) throw new Error("s_b + f_b != t_b");
        if (!t_blinding_factor.plus(new_blinding_factor).mod(bn128.curveN).eq(model.getAccountDetails(fromaddr).token_blinding_factor)) throw new Error("new_b + t_b != old_b");

        // Step 5: Calculate T
        var T                   = bn128.getPedersonCommitment(t_amount, t_blinding_factor);
        var F                   = bn128.getPedersonCommitment(f_amount, f_blinding_factor);

        // Step 6: Calculate S
        var S                   = bn128.getPedersonCommitment(s_amount, s_blinding_factor);
        
        var SF = bn128.ecAdd(S[0], S[1], F[0], F[1]);
        if (!SF[0].eq(T[0]) || !SF[1].eq(T[1])) throw new Error("S + F != T");

        // Step 7: Calculate new balance
        var cur_balance         = model.getAccountDetails(fromaddr).token_balance;
        var new_balance         = cur_balance.minus(t_amount);
        if (new_balance.isNegative()) throw new Error("Not enought balance to send tokens from " + fromaddr);

        var new_balance_commitment = bn128.getPedersonCommitment(new_balance, new_blinding_factor);
        var cur_commitment = model.getAccountDetails(fromaddr).token_total_commitment;        
        
        var CT = bn128.ecAdd(cur_commitment[0], cur_commitment[1], T[0], bn128.curveP.minus(T[1]));
        if (!CT[0].eq(new_balance_commitment[0]) || !CT[1].eq(new_balance_commitment[1])) {
           throw new Error("New Balance doesn't add up");
        }

        // Step 8: Get range proof for t and new_balance
        var calls = [
            {"command": "token_generate_range_proof", params: me.getRangeProofParams(t_amount, t_blinding_factor)},
            {"command": "token_generate_range_proof", params: me.getRangeProofParams(new_balance, new_blinding_factor)},
            {"command": "token_generate_range_proof", params: me.getRangeProofParams(s_amount, s_blinding_factor)},
        ]

        // TODO: We're asking the server to generate the range proofs, but
        // we should really be calculating it here. 
        this.multiCall(calls, function(data_list) {
            var t_range_proof       = data_list[0]["response"]["proofs"];
            var new_bal_range_proof = data_list[1]["response"]["proofs"];
            var s_range_proof       = data_list[2]["response"]["proofs"];
            
            // Step 9: Produce the signature.
            // Step 9a: hash the reciever
            // The reciever hash needs some randomness, otherwise observers will be able to figure out which sender goes to which reciever
            // Save this randomness, because we'll need it to challenge the mixer if it messes with our transaction
            var reciever_randomness: Buffer =  crypto.randomBytes(256/8);
            var reciever_hash: Buffer = bn128.keccak256(Buffer.concat(
                          [new Buffer(toaddr.substr(2), "hex"), 
                           new Buffer(bn128.hexWithPad(S[0]), "hex"),
                           new Buffer(bn128.hexWithPad(S[1]), "hex"),
                           reciever_randomness                              
                          ]));

            // Step 9b: Hash the message to be signed
            var msg_hash: Buffer = bn128.keccak256(Buffer.concat([
                new Buffer(fromaddr.substr(2), "hex"),
                new Buffer(bn128.hexWithPad(model.getAccountDetails(fromaddr).token_nonce), "hex"), // TODO: Use the token nonce
                new Buffer(bn128.hexWithPad(T[0]), "hex"),
                new Buffer(bn128.hexWithPad(T[1]), "hex"),
                reciever_hash
            ]));    

            // Step 9c: Sign it, geth style
            var signature = privatekeys.signPersonalMessage(fromaddr, msg_hash);

            // Step 10, Send everything to the mixer
            var trans = {
                "eph_pub"               : { "x": eph_pub[0].toString(10),   "y": eph_pub[1].toString(10)},
                "T"                     : { "x": T[0].toString(10),         "y": T[1].toString(10)},
                "t_secret"              : privatekeys.encrypt(t_amount, t_blinding_factor).toString(10),
                "s_secret"              : privatekeys.encrypt(s_amount, secret_key).toString(10),
                "F"                     : { "x": F[0].toString(10),         "y": F[1].toString(10)},
                "t_range_proof"         : t_range_proof,
                "s_range_proof"         : s_range_proof,
                "f_blinding_factor"     : f_blinding_factor.toString(10),
                "S"                     : { "x": S[0].toString(10),         "y": S[1].toString(10)},
                "new_bal_range_proof"   : new_bal_range_proof,
                "from_address"          : fromaddr,
                "to_address"            : toaddr,
                "reciever_hash"         : reciever_hash.toString("hex"),
                "token_nonce"           : model.getAccountDetails(fromaddr).token_nonce.toString(10),
                "signature"             : {
                    "r"                     : new BigNumber(signature["r"].toString("hex"), 16).toString(10),
                    "s"                     : new BigNumber(signature["s"].toString("hex"), 16).toString(10),
                    "v"                     : signature["v"].toString()       // v is a number, not a Buffer.
                                         },
                // Everything below this shouldn't be sent, but is being sent for debugging purposes.
                "debug_s"               : s_amount.toString(10),             // For the reciever to see how much money he got. Should be encrypted
                "debug_f"               : f_amount.toString(10),             // The mixer should already know this.  
                "debug_s_blinding"      : s_blinding_factor.toString(10),    // For the reciever. Should be derivable from eph_pub * pk
                "debug_new_balance"     : new_balance.toString(10),          // For sender's own history. Should be encrypted
                "debug_t_blinding"      : t_blinding_factor.toString(10),
                "debug_t"               : t_amount.toString(10)
            }

            // If transparent, send the actual s amount. 
            if (transparent) {
                trans["s_amount"] = s_amount;
            }

            console.log(trans);

            websocket.send(
                {"command": "do_transaction", params : trans},
                function(data) {
                    console.log("Do transaction success" + JSON.stringify(data["response"]));
                    //controller.loadAllAddressDetails();
                }
            );
       });
    }
}

export var controller = new Controller();