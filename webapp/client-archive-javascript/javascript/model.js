
bn128       = require("./bn_128");

// The model is a map from address -> various things about the address
var model = {};

module.exports = {
    getDetails : function(address) {
        // Get the address details
        var details = model[address];
        if (details == null) {
            details = {};
            model[address] = details;
        }

        return details;
    },

    setAddressDetails : function(response) {
        var details = this.getDetails(response.address);

        details["ether_balance"] = response.balance;
        details["ether_nonce_hex"] = response.nonce_hex;
    },

    getEtherNonce : function(address) {
        var details = this.getDetails(address);
        if (details == null) {
            console.log("Asking nonce for unknown address: " + address);
        }
        return details.ether_nonce_hex;
    },

    setTokenDetails : function(response) {
        var details = this.getDetails(response.address);

        details["token_balance"] = {"x" : response.balancex, "y": response.balancey};
    },
    hasTokenBalance : function(address) {
        var details = this.getDetails(address);
        if (details == null) return false;

        var balance = details.token_balance;
        if (balance == null) return false;

        return true;
    },

    setTokenHistory : function(response) {
        var details = this.getDetails(response.address);

        details["token_history"] = response.history;
    },
    hasTokenHistory : function(address) {
        var details = this.getDetails(address);
        if (details == null) return false;

        var history = details.token_history;
        if (history == null) return false;

        return true;
    },

    /**
     * Verify that the tokens have the right balance. That is, the token balance 
     * matches up with history. 
     */
    verifyTokenBalance : function(address) {
        // First, add up all the history.
        if (!this.hasTokenHistory(address)) {
            console.log("Doesn't have history yet!");
            return false;
        }

        var history = this.getDetails(address).token_history;
        var total_from_history = new BN();
        history.forEach(item  => {
            total_from_history.iadd(new BN(item.amount));
        });
        console.log("Total from history: " + total_from_history);

        // See if it matches Balance
        var calc_balance = bn128.ecMul(new BN(bn128.H[0]), new BN(bn128.H[1]), total_from_history);
        var token_balance = this.getDetails(address).token_balance;
        if (calc_balance[0].eq(new BN(token_balance.x)) && calc_balance[1].eq(new BN(token_balance.y))) {
            console.log("Balance Verified!");
        } else {
            console.log("Balance NOT VERIFIED");
            console.log(calc_balance[0].toString(), calc_balance[1].toString());
            console.log(this.getDetails(address).token_balance);
        }        
    }
}