// EC Math operations for BN_128 curve. Since there is no native library for JS that implements the bn_128 curve,
// we use the Parity's EVM implementation of BN_128 cross-compiled from rust to JS. Note that this is (I believe)
// provided by the parity team itself, so we're not actually mucking with it. 
bn128Module = require('rustbn.js')
BN          = require("bn.js");

/**
 * BN_128 EC point addition. 
 * @param {BN} point_1x 
 * @param {BN} point_1y 
 * @param {BN} point_2x 
 * @param {BN} point_2y 
 * 
 * @return {array} Array with 2 elements, representing point_x and point_y, encoded as strings in base 10
 */
exports.ecAdd = function(point_1x, point_1y, point_2x, point_2y) {
    if (typeof point1x == "string") point1x = new BN(point_1x);
    if (typeof point1y == "string") point1y = new BN(point_1y);
    if (typeof point2x == "string") point2x = new BN(point_2x);
    if (typeof point2y == "string") point2y = new BN(point_2y);

    // First, we have to create the data. Note that the precompiles expect data as a single hex-encoded string, with 64 bytes per argument.
    // ECMul takes 3 arguments - pointx, pointy, and scalar while ECAdd takes 4 arguments - x,y for the 2 points. 
    var inputHexStr = "";
    inputHexStr     += point_1x.toString(16, 64);
    inputHexStr     += point_1y.toString(16, 64);
    inputHexStr     += point_2x.toString(16, 64);
    inputHexStr     += point_2y.toString(16, 64);

    ecAddPrecompile = bn128Module.cwrap('ec_add', 'string', ['string'])
    let result = ecAddPrecompile(inputHexStr);
    return [new BN(result.substr(0, 64), 16), new BN(result.substr(64, 64), 16)];
}

/**
 * BN_128 EC point multiplication. 
 * @param {BN} point_1x 
 * @param {BN} point_1y 
 * @param {BN} point_2x 
 * @param {BN} point_2y 
 * 
 * @return {array} Array with 2 elements, representing point_x and point_y, encoded as strings in base 10
 */
exports.ecMul = function(point_1x, point_1y, scalar) {
    if (typeof point1x == "string") point1x = new BN(point_1x);
    if (typeof point1y == "string") point1y = new BN(point_1y);
    if (typeof scalar == "string") point1x = new BN(scalar);

    // First, we have to create the data. Note that the precompiles expect data as a single hex-encoded string, with 64 bytes per argument.
    // ECMul takes 3 arguments - pointx, pointy, and scalar while ECAdd takes 4 arguments - x,y for the 2 points. 
    var inputHexStr = "";
    inputHexStr     += point_1x.toString(16, 64);
    inputHexStr     += point_1y.toString(16, 64);
    inputHexStr     += scalar.toString(16, 64);

    ecMulPrecompile = bn128Module.cwrap('ec_mul', 'string', ['string'])
    let result = ecMulPrecompile(inputHexStr);
    return [new BN(result.substr(0, 64), 16), new BN(result.substr(64, 64), 16)]
}

exports.G = ["1", "2"]; // Generator point
exports.H = ["17856212038068422348937662473302114032147350344021172871924595963388108456668", "21295818415838735026194046494954432012836335667085206402831343127503290780315"];

exports.privateKeyToPubkey = function(pk) {
    // Pubkey is the point generated by pk * G, where G is the curve's generator function. 
    return exports.ecMul(this.G[0], this.G[1], pk);
}