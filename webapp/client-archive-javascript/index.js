keythereum  = require("keythereum");
etherutil   = require("ethereumjs-util")
bn128       = require("./javascript/bn_128");
controller  = require("./javascript/controller");
view        = require("./javascript/view");


// First, initialize the controller. 
controller.init();
view.init();

/*
// synchronous ether key creation test
var params = { keyBytes: 32, ivBytes: 16 };
var dk = keythereum.create(params);

console.log("Private Key Test");
console.log("Private Key: " + dk.privateKey.toString("hex"));
console.log("Address    : " + keythereum.privateKeyToAddress(dk.privateKey));

keys.setPrimaryPrivateKey(dk.privateKey);
console.log(keys.getSheildedKey(0));

// BN_128 curve operations test.
console.log("BN_128 Curve Test");
console.log(bn128.ecMul(new BN("15078171304226679255109208385550089670084403346086625455685162834280347331551"), 
                        new BN("8881873565392307003851088394022901056176902688116352930154823093818501191314"),
                        new BN("2")));


// Ether signing. Note the funkiness around signing. We're signing a prefixed string + 32-byte hash, which is itself
// hashed again. This is due the the geth rpc "sign" method prefixing the 32-byte message with 
// '\u0019Ethereum Signed Message:\n32'
console.log(
    etherutil.ecsign(
        etherutil.hashPersonalMessage(
            etherutil.toBuffer(etherutil.sha256("Hello World!"))), 
        dk.privateKey));

*/