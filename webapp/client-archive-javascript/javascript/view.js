jq          = require("jquery");
controller  = require("./controller");
module.exports = {
    init : function() {
        var me = this;
        jq(window).on("load", function() {
            me.createUI();
        })
    },

    createUI :  function() {
        jq("#deposit_btn").click(function() {
            controller.deposit(1);
        });

        jq("#range_proof").click(function() {
            controller.getRangeProof("1");
        })
    }
}