var expect = require('chai').expect;

BN      = require("bn.js");

bn128   = require("../javascript/bn_128");

describe('EC Mul', function() {
    it(" should perform EC multiplication on BN 128 curve", function() {
        var pointx = new BN("15078171304226679255109208385550089670084403346086625455685162834280347331551");
        var pointy = new BN("8881873565392307003851088394022901056176902688116352930154823093818501191314");
        var scalar = new BN("2");
            
        // Test1: Scalar multiplication
        var result = bn128.ecMul(pointx, pointy, scalar);
        
        var resultx = new BN("5510013098449802545674842232184790964617845220219322129886133249418243968356");
        var resulty = new BN("8084761392516637377444397836920165033039042434750766698192040165461999456159")

        expect(result[0].cmp(resultx)).equals(0);
        expect(result[1].cmp(resulty)).equals(0);

        // Test 2: Multiplication with 1 is identity
        var result = bn128.ecMul(pointx, pointy, new BN("1"));
        
        expect(result[0].cmp(pointx)).equals(0);
        expect(result[1].cmp(pointy)).equals(0);

        // Test 3: Multiplication with zero is zero
        var zero = new BN("0");
        var result = bn128.ecMul(pointx, pointy, zero);
        expect(result[0].cmp(zero)).equals(0);
        expect(result[1].cmp(zero)).equals(0);

    })
});

describe('EC Add', function() {
    it(" should perform EC addition on BN 128 curve", function() {
        // Test 1: Addition with self == multiplication with 2
        var pointx = new BN("15078171304226679255109208385550089670084403346086625455685162834280347331551");
        var pointy = new BN("8881873565392307003851088394022901056176902688116352930154823093818501191314");
        var scalar = new BN("2");
            
        var mul_result = bn128.ecMul(pointx, pointy, scalar);
        var add_result = bn128.ecAdd(pointx, pointy, pointx, pointy);

        expect(mul_result[0].cmp(add_result[0])).equals(0);
        expect(mul_result[1].cmp(add_result[1])).equals(0);
    })
});