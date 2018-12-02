import {privatekeys}        from "./privatekey";
import { controller }       from "./controller";

import * as keythereum      from "keythereum";



class AccountModal {
    realJq;
    id: string = "#account-modal";

    public init() {
        this.realJq = window["$"];
        var me = this;

        this.realJq(me.id).on("shown.bs.modal", function(data) {
            // Hide the "Import" button
            me.realJq(me.id + " .btn-primary").hide();

            me.populateModalKeys();
        });

        this.realJq(me.id).on("hidden.bs.modal", function(data) {
            me.realJq(me.id + " input.privkey").val("");
            me.realJq(me.id + " input.address").val("");
        });


        // Import button
        this.realJq(me.id + " .btn-primary").click(function() {
            var privKey: string = me.realJq(me.id + " input.privkey").val();
            privatekeys.setPrimaryPrivateKey(new Buffer(privKey, "hex"));

            me.realJq(me.id).modal('hide');
            // Reload the account
            controller.loadAllAddressDetails();
        });

        // If the account private key is modified, then reset the address and add
        // the import butotn
        this.realJq(me.id + " input.privkey").on("input", function() {
            me.realJq(me.id + " input.address").val("");
            me.realJq(me.id + " .btn-primary").hide();

            // Check if the input is a valid ether address
            try {
                var address = null;

                var pk: Buffer = new Buffer(me.realJq(me.id + " input.privkey").val(), "hex");
                if (pk.length == 32)  {
                    address = keythereum.privateKeyToAddress(pk);
                }                

                if (address != null) {
                    me.realJq(me.id + " input.address").val(address);
                    me.realJq(me.id + " .btn-primary").show();

                    // Everything is fine, return
                    return;
                }
            } catch (e) {
                // Something went wrong, just say the input is invalid
            }
                
            me.realJq(me.id + " input.address").val("<invalid private key>");
            me.realJq(me.id + " .btn-primary").hide();                
        });
    }

    /**
     * Show the new account modal with a fresh new account. Happens when there is 
     * no saved key.
     */
    public showFreshAccount() {
        this.realJq(this.id).modal();
    }

    public populateModalKeys() {
        this.realJq(this.id + " input.privkey").val(privatekeys.getPrimaryPrivateKeyAsHex());
        this.realJq(this.id + " input.address").val(privatekeys.getPrimaryAddress());
    }

}

export var accountmodal: AccountModal = new AccountModal();