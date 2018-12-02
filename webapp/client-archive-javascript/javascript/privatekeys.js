// Private keys management in the browser. We need this class because we're managing several private keys. 
// The entire account is based on 1 primary ethereum private key. This is the primary (deposit) address of the account. 
// Derived from this are several ethereum private keys that represent the sheilded accounts. They are derived as
//  
// pk : Primary private key
// pk_n  = keccak(keccak(pk) || keccak("derived") || keccak(n)) for the "n"th private key
//
// The ethereum address and bn_128 public key are both calculated from pk_sn

keythereum  = require("keythereum");
etherutil   = require("ethereumjs-util")
ethertx     = require("ethereumjs-tx");
etherunits  = require("ethereumjs-units");
config      = require("./config");

var account_private_key = "";

exports.setPrimaryPrivateKey = function(pk) {
    account_private_key = pk;
    account_address = keythereum.privateKeyToAddress(pk.toString(16));
}

exports.getPrimaryPKasBuffer = function() {
    return new Buffer(account_private_key.toString(16), "hex");
}

exports.getPrimaryAddress = function() {
    return account_address;
}

derive_sheilded = function(pk, n) {
    // pk : Primary private key
    // pk_n  = keccak(keccak(pk) || keccak("derived") || keccak(n)) for the "n"th private key
    let derived_pk = etherutil.sha3(
                        Buffer.concat([  etherutil.sha3(pk), 
                                        etherutil.sha3("derived"), 
                                        etherutil.sha3(n)
                                     ]));
    return derived_pk;                                        
}

/**
 * Get the sheilded key number "n". n >= 0
 * @param {Number} n 
 */
exports.getSheildedKey = function(n) {
    derived_pk = derive_sheilded(account_private_key, n);
    var ans = {
        "pk"            : derived_pk,
        "address"       : keythereum.privateKeyToAddress(derived_pk),
        "bn_128_pubkey" : bn128.privateKeyToPubkey(derived_pk)
    };

    return ans;
}

toHexWithConvert = function(value, units) {
    if (typeof value == "string")
        value = parseInt(value);

    return "0x" + new BN(etherunits.convert(value, units, "wei")).toString(16);
}

toHex = function(value) {
    return "0x" + new BN(value).toString(16);
}

/**
 *      
 * @param {Buffer} pk           The private key to use to sign the transaction
 * @param {Number} amount       Amount of Ether to deposit
 * @param {String} nonce_hex    Nonce of the account in hex
 */
exports.signDepositContractCall = function(pk, amount, nonce_hex) {
    // Create the transaction
    if (typeof nonce_hex != "string" || !nonce_hex.startsWith("0x"))
        console.log("Error. Nonce must be a hex string");

    var rawTx = {
        nonce: nonce_hex,
        gasPrice: toHexWithConvert(1, "gwei"), 
        gas: toHex(500000),
        to: config.get().contractAddress, 
        value: toHexWithConvert(amount, "wei"), 
        data: '0xd0e30db0'  // deposit() method
      }

    // hash for the deposit() method. Since no other arguments, this is the only data we need. 
    var tx = new ethertx(rawTx);
    tx.sign(pk);
    
    return "0x" + tx.serialize().toString('hex');
}