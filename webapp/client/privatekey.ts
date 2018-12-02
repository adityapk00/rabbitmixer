// Private keys management in the browser. We need this class because we're managing several private keys. 
// The entire account is based on 1 primary ethereum private key. This is the primary (deposit) address of the account. 
// Derived from this are several ethereum private keys that represent the stealth accounts. They are derived as
//  
// pk : Primary private key
// pk_n  = keccak(keccak(pk) || keccak("derived") || keccak(n)) for the "n"th private key
//
// The ethereum address and bn_128 public key are both calculated from pk_sn
import {BigNumber}      from "bignumber.js"

import * as crypto      from "crypto";
import * as keythereum  from "keythereum";
import * as etherutil   from "ethereumjs-util";
import * as ethertx     from "ethereumjs-tx";
import * as etherunits  from "ethereumjs-units";

import {config    }     from "./config";
import {model     }     from "./model";
import {bn128     }     from "./bn_128";
import {cookie}         from "./cookie";


class Account {
    privatekey: Buffer;
    address: string;
    isPrimary: boolean;
    bn128_pubpoint: BigNumber[];

    public constructor(pk: Buffer, addr: string, primary: boolean) {
        this.privatekey = pk;
        this.address = addr;
        this.isPrimary = primary;
        this.bn128_pubpoint = bn128.privateKeyToPubkey(pk);
    }
}

class PrivateKey {
    DEFAULT_NUM_HIDDEN_ACCOUNTS = 3;

    primaryAccount: Account;
    stealthAccounts: Account[];

    public generateNewPrimaryKey() {
        privatekeys.setPrimaryPrivateKey(crypto.randomBytes(256/8));
    }

    public setPrimaryPrivateKey(pk: Buffer) {
        this.primaryAccount  = new Account(pk, keythereum.privateKeyToAddress(pk), true);
        this.setNumStealthAccounts(this.DEFAULT_NUM_HIDDEN_ACCOUNTS);

        cookie.write("privatekey", privatekeys.getPrimaryPrivateKeyAsHex(), 365*10);
    }
    
    private getPrimaryPK(): Buffer {
        return this.primaryAccount.privatekey;
    }

    public getAccountFromAddress(address: string): Account {
        if (address == this.getPrimaryAddress()) {
            return this.primaryAccount;
        } else {
            // Find the right hidden account.
            
            var account = this.stealthAccounts.filter(account => {if (account.address == address) return true; else return false;});
            // If not found, then error
            if (account == null)
                throw new Error("Addresss " + address + " not found");
            return account[0];
        }
    }
    
    public getPrimaryAddress(): string {
        return this.primaryAccount.address;
    }
    

    public getPrimaryPrivateKeyAsHex(): string {
        return this.getPrimaryPK().toString("hex");
    }


    private deriveStealth(n: Number): Buffer {
        // pk : Primary private key
        // pk_n  = keccak(keccak(pk) || keccak("derived") || keccak(n)) for the "n"th private key
        let derived_pk = etherutil.sha3(
                            Buffer.concat([ etherutil.sha3(this.primaryAccount.privatekey), 
                                            etherutil.sha3("derived"), 
                                            etherutil.sha3(n)
                                         ]));
        return derived_pk;                                        
    }
    
    public setNumStealthAccounts(count: number) {
        this.stealthAccounts = [];
        for(var i=0; i < count; i++) {
            var pk: Buffer = this.deriveStealth(i)
            this.stealthAccounts.push(new Account(pk, keythereum.privateKeyToAddress(pk), false));
        }
    }

    public getNumStealthAccounts(): number {
        return this.stealthAccounts.length;
    }

    /**
     * Get the stealth key number "n". n >= 0
     */
    public getStealthKey(n: number): Account {
        if (n < 0 || n >= this.stealthAccounts.length) throw new Error("Not enough hidden accounts!");
        return this.stealthAccounts[n];
    }

    /**
     * Get the blinding factor for sending at the given nonce. 
     */
    public getTokenTBlindingFactorForNonce(address: string, nonce: number): BigNumber {
        // Derive the pederson commitment. We'll use the private key of the address to do so.
        var account = this.getAccountFromAddress(address);

        var b: Buffer = etherutil.sha3(
            Buffer.concat([ etherutil.sha3(account.privatekey), 
                            etherutil.sha3("blinding_factor"), 
                            etherutil.sha3(nonce)
                         ]));
        return bn128.modN(new BigNumber(b.toString("hex"), 16));
    }

    /*
    public getSharedSecret(pk: Buffer, pointx: BigNumber, pointy: BigNumber): Buffer {
        return bn128.ecdh(pk, [pointx, pointy]);
    }
    */

    /**
     *  Sign the specific deposit mmethod
     */
    public signDepositContractCall(address: string, amount: BigNumber, nonce: BigNumber) {
        // Create the transaction
        var nonce_hex = "0x" + nonce.toString(16);

        var rawTx = {
            nonce   : nonce_hex,
            gasPrice: this.toHexWithConvert(model.getGasPrice(), "wei"),
            gas     : this.toHex(186090),            // Gas cost of deposit transaction
            to      : config.get()["contractAddress"], 
            value   : this.toHexWithConvert(amount, "wei"), 
            data    : '0xd0e30db0'  // deposit() method
        }
    
        // hash for the deposit() method. Since no other arguments, this is the only data we need. 
        var tx = new ethertx(rawTx);
        tx.sign(this.getAccountFromAddress(address).privatekey);
        
        return "0x" + tx.serialize().toString('hex');
    }

    /**
     * Sign a message in the geth style. That is, 
     * msg_to_sign  = "<whatever>"
     * msg_hash     = keccack256(msg_to_sign);
     * hash_to_sign = keccak256("\u0019Ethereum Signed Message:\n32" + msg_hash)
     * return sign(hash2)
     * 
     * That is to say this function expects the first hash (msg_hash) as input
     */
    public signPersonalMessage(address: string, msg_hash: Buffer) {
        var pk = this.getAccountFromAddress(address).privatekey;

        if (msg_hash.length != 32) throw new Error("Will only sign a hash");
        var hash_to_sign = etherutil.hashPersonalMessage(msg_hash);
        return etherutil.ecsign(hash_to_sign, pk);
    }

    private iv = '4e5Wa71fYoT7MFEX';
    private decipher(data: Buffer, key: Buffer): Buffer {
        if (key.length != 32) throw Error("Keylength should be 32");
        var decipher = crypto.createDecipheriv("aes-256-cbc", key, this.iv);
        var ans = Buffer.concat([decipher.update(data), decipher.final()]);
        console.log(`deciphered ${data.toString("hex")} to ${ans.toString("hex")}`);
        return ans;
    }
    
    private cipher(data: Buffer, key: Buffer): Buffer {
        if (key.length != 32) throw Error("Keylength should be 32");
        var cipher = crypto.createCipheriv("aes-256-cbc", key, this.iv);
        var enc = Buffer.concat([cipher.update(data), cipher.final()]);

        if (!this.decipher(enc, key).equals(data)) throw Error("cipher-decipher failed");
        console.log(`ciphered ${data.toString("hex")} to ${enc.toString("hex")}`);
        return enc;
    }


    public encrypt(value: BigNumber, secret: BigNumber): BigNumber {
        var keyBuffer = new Buffer(bn128.hexWithPad(secret), "hex");
        var encvalue  = bn128.BigNumberFromBuffer(this.cipher(bn128.BufferFromBigNumber(value), keyBuffer));
        console.log(`Encrypted ${value.toString(16)} to ${encvalue.toString(16)}`);

        // Just make sure we can decrypt it
        var ddecrpt = this.decrypt(encvalue, secret);
        if (!ddecrpt.isEqualTo(value)) {
            throw Error("Couldn't decrypt what we just encrypted");
        }

        return encvalue;
    }

    public decrypt(encvalue: BigNumber, secret: BigNumber): BigNumber {
        var keyBuffer = new Buffer(bn128.hexWithPad(secret), "hex");
        
        var decvalue = bn128.BigNumberFromBuffer(this.decipher(bn128.BufferFromBigNumber(encvalue), keyBuffer));
        console.log(`Decrypted ${encvalue.toString(16)} to ${decvalue.toString(16)}`);
        return decvalue;
    }
    
    private toHexWithConvert(value: BigNumber, units: String) {
        return "0x" + new BigNumber(etherunits.convert(value.toString(10), units, "wei")).toString(16);
    }
    
    private toHex(value: Number) {
        return "0x" + new BigNumber(value.toString(10)).toString(16);
    }
}

export var privatekeys: PrivateKey = new PrivateKey();