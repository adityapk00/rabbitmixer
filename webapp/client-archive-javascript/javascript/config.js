var global_config;

keys        = require("./privatekeys");

exports.setConfig = function(cfg) {
    global_config = cfg;

    keys.setPrimaryPrivateKey(new BN(cfg.privateKey, "hex"));
}

exports.get = function() {
    return global_config;
} 