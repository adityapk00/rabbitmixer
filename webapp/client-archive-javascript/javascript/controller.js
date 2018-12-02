var BN      = require("bn.js");
var crypto  = require("crypto");

/**
 * Controller for the UI to talk to the Web service. 
 */

websocket   = require("./websocket");
config      = require("./config");
model       = require("./model");
keys        = require("./privatekeys");

var deposit_limit = 0;

module.exports = {
    init : function() {
        websocket.connect();
    },

    connected : function() {
        // Connected. Now, get the client config
        var me = this;

        websocket.send(
            {"command": "client_config", "params": {"config": ""}},
            function(data) {
                //console.log("Setting Config");
                config.setConfig(data.response);
                me.loadAllAddressDetails();
            }
        );
    },

    loadAllAddressDetails : function(count = 3) {
        this.loadAddressDetails(keys.getPrimaryAddress());
        for (i =0; i < count; i++) {
            var address = keys.getSheildedKey(i).address;
            this.loadAddressDetails(address);
        }
    },

    loadAddressDetails : function(address = "") {
        if (address == null || address == "") throw "loadAddress() needs an address";

        var calls = [
            {"command": "ether_address_details", "params": {"address": address}},
            {"command": "token_address_history", "params": {"address": address}},
            {"command": "token_address_details", "params": {"address": address}}
        ];

        var total           = calls.length;
        var returned_calls  = 0;

        var me = this;
        handler = function(data) {            
            returned_calls++;

            switch (data.command) {
                case "ether_address_details": 
                    model.setAddressDetails(data.response);
                    break;
                case "token_address_details":
                    model.setTokenDetails(data.response);
                    break;
                case "token_address_history":
                    model.setTokenHistory(data.response);
                    break;
            }

            if (returned_calls == total) {
                model.verifyTokenBalance(data.response.address);
            }
        };

        calls.forEach(cmd => {
          websocket.send(cmd , handler);
        });
    },

    deposit : function(amount) {          
        var rawTx = keys.signDepositContractCall(keys.getPrimaryPKasBuffer(), amount, model.getEtherNonce(keys.getPrimaryAddress()));

        var me = this;
        websocket.send(
            {"command": "execute_rawtx", "params": {"rawtx": rawTx}},
            function(data) {
                console.log("Deposited. Waiting for server to send confirmation...");
            }
        );
    },

    notification: function(response) {
        if (response.notification == "deposit_complete") {
            // Reload to show the new balances
            this.loadAllAddressDetails();
        }
    },

    getRangeProof : function(amount) {
        var blinding_factor = new BN(crypto.randomBytes(256/8).toString("hex"), 16);
        websocket.send(
            {"command": "token_generate_range_proof", params: {"v": amount, "blinding_factor": blinding_factor.toString()}},
            function(data) {
                console.log("Range proof: " + data.response);
            }
        );
    }

};
