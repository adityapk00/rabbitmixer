import * as crypto        from "crypto";

import {BigNumber}      from "bignumber.js"

import {privatekeys}    from "./privatekey";
import {bn128    }      from "./bn_128";
import {controller }    from "./controller";
import {view }          from "./view";


class HistoryItem {
    type        : string;
    amount      : BigNumber;
    enc_amount  : BigNumber;
    eph_pub_x   : BigNumber;
    eph_pub_y   : BigNumber;
    timestamp   : BigNumber;

    public constructor(type: string, enc_amount: BigNumber,  timestamp: BigNumber = null,
        eph_pub_x: BigNumber = null, eph_pub_y: BigNumber = null) {
        this.type       = type;
        this.enc_amount = enc_amount;
        this.eph_pub_x  = eph_pub_x;
        this.eph_pub_y  = eph_pub_y;
        this.timestamp  = timestamp;
    }
}

class AccountDetails {
    address                 : string;
    ether_nonce             : BigNumber;
    ether_balance           : BigNumber;

    token_balance           : BigNumber;
    token_balance_verified  : boolean;
    token_total_commitment  : BigNumber[];
    token_blinding_factor   : BigNumber;
    token_history           : HistoryItem[];
    token_nonce             : BigNumber;

    mix_status              : Object;
}

class Model {

    // The model is a map from address -> various things about the address
    private addressToDetailsMap: Object = {}; // Map<string, AccountDetails>

    private currentFee: BigNumber;
    public getCurrentFee(): BigNumber {
        return this.currentFee;
    }

    public setCurrentFee(fee: BigNumber) {
        this.currentFee = fee;
    }
    
    private gasPrice: BigNumber;
    public setGasPrice(price: BigNumber) {
        this.gasPrice = price;
    }

    public getGasPrice(): BigNumber {
        return this.gasPrice;
    }

    private getOrCreateAccountDetails(address: string): AccountDetails {
        var details = this.addressToDetailsMap[address];
        if (details == null) {
            details = new AccountDetails();
            this.addressToDetailsMap[address] = details;
        }
        
        return details;
    }

    public getAccountDetails(address: string): AccountDetails {
        // Get the address details
        var details = this.addressToDetailsMap[address];
        if (details == null) throw new Error("Account not found:" + address);
        
        return details;
    }

    public setEtherDetails(response: Object) {
        var details = this.getOrCreateAccountDetails(response["address"]);

        details.address             = response["address"];
        details.ether_balance       = new BigNumber(response["balance"]);
        details.ether_nonce         = new BigNumber(response["nonce_hex"], 16);
    }

    public setTokenDetails(response: Object) {
        var details = this.getOrCreateAccountDetails(response["address"]);

        details.token_total_commitment = [new BigNumber(response["balancex"]), new BigNumber(response["balancey"])];
        details.token_nonce = new BigNumber(response["nonce"]);
    }

    public setTokenHistory(response: Object) {
        var details = this.getOrCreateAccountDetails(response["address"]);

        details.token_history = [];
        response["history"].forEach(item => {
            if (item["type"] == "RECIEVE") {
                details.token_history.push(new HistoryItem(
                    item["type"], 
                    new BigNumber(item["enc_amount"]), 
                    new BigNumber(item["timestamp"]),
                    new BigNumber(item["eph_pub_x"]), 
                    new BigNumber(item["eph_pub_y"])
                ));
            } else {
                details.token_history.push(new HistoryItem(
                    item["type"], 
                    new BigNumber(item["enc_amount"]),
                    new BigNumber(item["timestamp"])
                ));
            }            
        });

        this.processTokenHistory(response["address"]);
    }

    private processTokenHistory(address: string): void {        
        var details = this.getAccountDetails(address);
        
        // Initialize
        details.token_balance = new BigNumber(0);
        details.token_blinding_factor = new BigNumber(0);
        details.token_nonce = new BigNumber(0);

        var history = this.getAccountDetails(address).token_history;
        if (history == null || history.length == 0) {
            return;
        }

        var total_from_history = new BigNumber(0);
        var nonce = 0;

        // The starting blinding factor is 0
        details.token_blinding_factor = new BigNumber(0);
        history.forEach(item  => {
            if (item.type == "DEPOSIT") {
                total_from_history = total_from_history.plus(item.enc_amount);
                item.amount = item.enc_amount;
            } else if (item.type == "SEND") {
                // Update the blinding factor.
                var t_blinding_factor = bn128.modN(privatekeys.getTokenTBlindingFactorForNonce(address, nonce));
                details.token_blinding_factor = bn128.modN(details.token_blinding_factor.minus(t_blinding_factor));
                nonce++;

                // Decrypt Amount
                item.amount = privatekeys.decrypt(item.enc_amount, t_blinding_factor);
                total_from_history = total_from_history.minus(item.amount);
            } else if (item.type == "RECIEVE") {
                var pk = privatekeys.getAccountFromAddress(address).privatekey;
                var secret_key  = new BigNumber(bn128.ecdh(pk, [item.eph_pub_x, item.eph_pub_y]).toString("hex"), 16);
                details.token_blinding_factor = bn128.modN(details.token_blinding_factor.plus(secret_key));

                // Decrypt Amount
                item.amount = privatekeys.decrypt(item.enc_amount, secret_key);
                total_from_history = total_from_history.plus(item.amount);
            }
        });
        details.token_balance = total_from_history;

        // AUTO transfer to a sheilded address if the primary address has a token balance. 
        if (details.address == privatekeys.getPrimaryAddress() &&
            details.token_balance.isGreaterThan(this.getCurrentFee())) {
                var fee = this.getCurrentFee();
                controller.postLoadCalls.push(function() {
                    console.log("AUTO Sheilded Token Transfer from Primary to Sheilded address");
                    controller.autoTokenTransfer(details.address, privatekeys.getStealthKey(0).address, details.token_balance.minus(fee));
                });
        }
    }

    public setMixStatus(response: Object) {
        var address: string = response["address"];
        var details = this.getAccountDetails(address);
        if (response["statuses"].length > 0) {            
            details.mix_status = response["statuses"];

            // Show a bubble on the view to say that this address has a pending status
            view.showPendingStealthTransferBubble(address);
        } else {
            // Remove any pending transactions
            details.mix_status = [];
        }
    }

    /**
     * Verify that the tokens have the right balance. That is, the token balance 
     * matches up with history. 
     */
    public verifyTokenBalance(address: string) {
        var details = this.getAccountDetails(address);
        console.log("Total from history: " + details.token_balance);

        // See if it matches Balance
        try {
            var balance_commitment  = bn128.ecMul(new BigNumber(bn128.H[0]), new BigNumber(bn128.H[1]), details.token_balance);
            var blinding_commitment = bn128.ecMul(new BigNumber(bn128.G[0]), new BigNumber(bn128.G[1]), details.token_blinding_factor);
            var calc_commitment     = bn128.ecAdd(balance_commitment[0], balance_commitment[1], blinding_commitment[0], blinding_commitment[1]);
            
            if (details.token_total_commitment[0].eq(calc_commitment[0]) && details.token_total_commitment[1].eq(calc_commitment[1])) {
                details.token_balance_verified = true;
                console.log("Balance Verified!");
            }
            
            return;
        } catch (e) {
            console.log(e);
            details.token_balance_verified = false;
            console.log("Balance NOT VERIFIED");
            console.log("Calculated: " + calc_commitment);
            console.log("Recieved:" + details.token_total_commitment);
        }
    }    
}

export var model: Model = new Model();