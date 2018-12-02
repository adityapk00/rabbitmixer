import {privatekeys}       from "./privatekey";
import {controller }       from "./controller";
import {view}              from "./view";
import {model}             from "./model";
import {BigNumber}         from "bignumber.js";

import {cookie}            from "./cookie";
import * as crypto         from "crypto";
import { accountmodal }    from "./accountmodal";

class Config {
    global_config: Object;

    public setConfig(cfg: Object) {
        this.global_config = cfg;

        view.setContractAddress(cfg["contractAddress"]);

        // Try to load the account from the cookie.
        var privKey: string = cookie.read("privatekey");
        if (privKey == null) {
            // No cookie, so generate a private key
            privatekeys.generateNewPrimaryKey();
            // And save the cookie
            cookie.write("privatekey", privatekeys.getPrimaryPrivateKeyAsHex(), 365*10);

            // Popup the account modal 
            accountmodal.showFreshAccount();
        } else {
            privatekeys.setPrimaryPrivateKey(new Buffer(privKey, "hex"));
        }

        // And if we are in dev mode, just ask the server for some money. 
        if(cfg["network"] == "dev") {
            controller.askServerForMoney(privatekeys.getPrimaryAddress());
        }

        // And save the current Fee
        model.setCurrentFee(new BigNumber(cfg["currentFee"]));
        model.setGasPrice(new BigNumber(cfg["gasPrice"]));
    }

    public get(): Object {
        return this.global_config;
    }
}

export var config: Config = new Config();