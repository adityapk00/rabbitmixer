import * as jq              from "jquery";
import * as dt              from "node-datetime";

import {BigNumber}          from "bignumber.js"

import {controller}         from "./controller";
import {model    }          from "./model";
import {privatekeys}        from "./privatekey";
import {accountmodal}       from "./accountmodal";
import {txstatusmodal}      from "./txstatusmodal";
import { bn128 } from "./bn_128";

class View {

    public init() : void {
        var me = this;
        jq(window).on("load", function() {
            me.createUI();
            controller.init();
        })
    }

    public createUI() : void {
        accountmodal.init();
        txstatusmodal.init();

        jq("#range_proof").click(function() {
            //controller.getRangeProof(new BigNumber(1), new BigNumber(crypto.randomBytes(256/8).toString("hex"), 16));
            controller.tokenTransfer(
                privatekeys.getPrimaryAddress(), 
                privatekeys.getStealthKey(0)["address"], 
                new BigNumber(1));  
        })
        
        var realJq = window["$"];
        var me = this;

        // Wire up the Deposit Modal
        realJq("#deposit-modal").on("shown.bs.modal", function(data) {
            var address = data.relatedTarget.id.substr(-42);
            realJq("#deposit-modal #ether-address").text(address);

            var gasCost = model.getGasPrice().multipliedBy(186090);  // Gas cost of deposit method
            var max = model.getAccountDetails(address).ether_balance.minus(gasCost);

            realJq("#deposit-modal input").val(max.dividedBy(new BigNumber(10).pow(18).toString(10)));
        });
        realJq("#deposit-modal").on("hidden.bs.modal", function(data) {
            realJq("#deposit-modal #ether-address").text("");
        });
        jq("#deposit-modal .btn-primary").click(function() {                        
            var amount = new BigNumber(realJq("#deposit-modal input").val());
            var address = realJq("#deposit-modal #ether-address").text();

            // Amount is in Ether, but we need Wei, so multiply. 
            amount = amount.multipliedBy(new BigNumber(10).pow(18));            
            controller.deposit(address, amount);

            realJq("#deposit-modal").modal('hide');
        });

        // Wire up the Send
        realJq("#send-modal").on("shown.bs.modal", function(data) {
            var address = data.relatedTarget.id.substr(-42);
            realJq("#send-modal #ether-address").text(address);
            
            // Populate the fee and max values
            realJq("#send-modal #currentfee").text(me.toEther(model.getCurrentFee()));
            var max = model.getAccountDetails(address).token_balance.minus(model.getCurrentFee());
            if (max.isLessThan(0)) max = new BigNumber(0);
            realJq("#send-modal #maxether").text(me.toEther(max));
            realJq("#send-modal input.amount").val(me.toEther(max));
        });
        realJq("#send-modal #maxether").click(function() {
            realJq("#send-modal input.amount").val(realJq("#send-modal #maxether").text());
        });
        realJq("#send-modal").on("hidden.bs.modal", function(data) {
            realJq("#send-modal #ether-address").text("");
            realJq("#send-modal input.amount").val("");
            realJq("#send-modal input.address").val("");
        });
        jq("#send-modal .btn-primary").click(function() {            
            var amount = new BigNumber(realJq("#send-modal input.amount").val());
            var fromAddress = realJq("#send-modal #ether-address").text();
            var toAddress = realJq("#send-modal input.address").val();

            // Amount is in Ether, but we need Wei, so multiply. 
            amount = amount.multipliedBy(new BigNumber(10).pow(18));  
            controller.tokenTransfer(fromAddress, toAddress, amount);

            realJq("#send-modal").modal('hide');
        });


         // Wire up the Withdraw
         realJq("#withdraw-modal").on("shown.bs.modal", function(data) {
            var address = data.relatedTarget.id.substr(-42);
            realJq("#withdraw-modal #ether-address").text(address);

             // Populate the fee and max values
             realJq("#withdraw-modal #currentfee").text(me.toEther(model.getCurrentFee()));
             var max = model.getAccountDetails(address).token_balance.minus(model.getCurrentFee());
             if (max.isLessThan(0)) max = new BigNumber(0);
             realJq("#withdraw-modal #maxether").text(me.toEther(max));
             realJq("#withdraw-modal input.amount").val(me.toEther(max));
        });
        realJq("#withdraw-modal #maxether").click(function() {
            realJq("#withdraw-modal input.amount").val(realJq("#withdraw-modal #maxether").text());
        });
        realJq("#withdraw-modal").on("hidden.bs.modal", function(data) {
            realJq("#withdraw-modal #ether-address").text("");
            realJq("#withdraw-modal input").val('');
            realJq("#withdraw-modal input.address").val("");
        });
        jq("#withdraw-modal .btn-primary").click(function() {            
            var amount = new BigNumber(realJq("#withdraw-modal input.amount").val());
            var fromAddress = realJq("#withdraw-modal #ether-address").text();
            var toAddress = realJq("#withdraw-modal input.address").val();

            amount = amount.multipliedBy(new BigNumber(10).pow(18));  
            controller.tokenTransferTransparent(fromAddress, toAddress, amount);

            realJq("#withdraw-modal").modal('hide');
        });


    }

    public setContractAddress(address: string): void {
        jq("#menu-contract-address").text(address);
    }

    private toEther(amountInWei: BigNumber): string {
        var amount = amountInWei.dividedBy(new BigNumber(10).pow(18)).decimalPlaces(4, BigNumber.ROUND_DOWN);
        return amount.toString(10);
    }

    public updateTable(): void {
        var maintable = jq("#maintable");
        var me = this;
        
        maintable.empty();

        // Identify all addresses that have a status. 
        var allAddresses: string[] = [privatekeys.getPrimaryAddress()];
        for(var i=0; i < privatekeys.getNumStealthAccounts(); i++) {
            allAddresses.push(privatekeys.getStealthKey(i).address);
        }
        
        var txstatuses: string = "";
        allAddresses.forEach(a => {
            if (model.getAccountDetails(a).mix_status["length"] > 0 && 
                model.getAccountDetails(a).mix_status[0].type == "SENDER") {
                txstatuses = txstatuses + `
                    <a href="#" id="txstatus_${a}" data-toggle="modal" data-target="#transferstatus-modal">
                        ${a} has a pending stealth transfer
                    </a>`;
            }
        });

            

        // First, create the primary account
        var primaryAccount = model.getAccountDetails(privatekeys.getPrimaryAddress());        

        if (txstatuses != "") {
            maintable.append(`
            <div class="col-sm-12">
                <div class="col-sm-10 offset-sm-1">
                    <div id="txstatusbubble" class="alert alert-primary" role="alert">
                        ${txstatuses}
                    </div>
                </div>
            </div>
            `);
        }

        maintable.append(`
        <div id="addresscards" class="col-sm-6">
            <div class="row">
                <div class="col-sm-12">
                    <div class="card text-black mb-3">
                        <div class="card-header bg-primary text-white">
                            <h6 class="float-left">${primaryAccount.address} </h6>
                            <h6 class="float-right">Primary Address</h6>
                        </div>
                        <div class="card-body">                    
                            <p class="card-text">Ether Balance: ${me.toEther(primaryAccount.ether_balance)} Ether</p>
                            <button id="deposit_btn_${primaryAccount.address}" class="deposit-btn btn btn-info" 
                                    data-toggle="modal" data-target="#deposit-modal">
                                    Deposit
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div id="helpcontent" class="col-sm-6">
            <div class="card">
                <div class="card-header"><h4>Using Rabbit Mixer</h4></div>
                <div class="card-body">
                    <div>    
                        <h5 class="card-title">Step 1: Fund with Ether</h5>
                        <p class="card-text">To get started, send the amount of Ether you want to mix to your Primary Address: ${primaryAccount.address}</p>
                    </div>
                    <br/><hr/>
                    <div>    
                        <h5 class="card-title">Step 2: Deposit Ether</h5>
                        <p class="card-text">Deposit Ether into the mixer by clicking the "Deposit" button. This will convert your Ether into Stealth Ether and send it to your first Stealth Address.</p>
                    </div>
                    <br/><hr/>
                    <div>    
                        <h5 class="card-title">Step 3: Withdraw or Transfer Stealth Ether</h5>
                        <p class="card-text">After your Stealth Ether is in your Stealth Address, you can withdraw it. Please use a brand new Ether address if you can. <br/> You can also send Stealth Ether to a friend's Stealth Address by using the "Transfer" button.</p>
                    </div>
            </div>
            </div>
        </div>
        `);
        
        var getHistoryHTML = function(address: string): string {
            var ans:string;

            if(model.getAccountDetails(address).token_history.length == 0) {
                ans = `
                    <hr/>
                    <div class="row">
                        <div class="col-sm-12">No History Yet</div>
                    </div>`;
            } else {
                ans = `
                <hr/>
                <div class="row">
                    <div class="col-sm-12">History</div>
                </div>
                <br/>
                <div class="row">
                    <div class="col-sm-3">Type</div>
                    <div class="col-sm-6">Amount</div>
                    <div class="col-sm-3">Date & Time</div>
                </div>
                `;

                model.getAccountDetails(address).token_history.forEach(item => {
                    var amount = item.amount == null ? '' : item.amount.toString(10);
                    ans = ans + `
                        <div class="row">
                            <div class="col-sm-3">${item.type}</div>
                            <div class="col-sm-6">${amount} Wei</div>
                            <div class="col-sm-3">${dt.create(item.timestamp.toNumber() * 1000).format("Y-m-d H:M")}</div>
                        </div>
                    `;
                });
            }   
            return ans;
        }

        var addresscards = jq("#addresscards");
        for(var i=0; i < privatekeys.getNumStealthAccounts(); i++) {
            var stealthAccount = model.getAccountDetails(privatekeys.getStealthKey(i).address);
            var hasTokenBalance = stealthAccount.token_balance.isGreaterThan(0);

            addresscards.append(`
            <div class="row">
                <div class="col-sm-12">
                    <div class="card bg-light mb-3">
                        <div class="card-header bg-info text-white">
                            <h6 class="float-left">${stealthAccount.address} </h6>
                            <h6 class="float-right">Stealth Address</h6>
                        </div>
                        <div class="card-body">                                                   
                            <p class="card-text">Stealth Balance: ${me.toEther(stealthAccount.token_balance)} Ether</p>
                            <div class="float-left">
                                <button id="withdraw_btn_${stealthAccount.address}" 
                                            class="withdraw-btn btn btn-info" 
                                            data-toggle="modal" data-target="#withdraw-modal" ${hasTokenBalance ? "" : "disabled"}>
                                    Withdraw
                                </button>
                            </div>
                            <div class="float-right">
                                <button id="send_btn_${stealthAccount.address}" 
                                            class="send-btn btn btn-info" 
                                            data-toggle="modal" data-target="#send-modal" ${hasTokenBalance ? "" : "disabled"}>
                                    Transfer
                                </button>
                                <div class="btn btn-info" data-toggle="collapse" data-target="#debug_${stealthAccount.address}">
                                    Details
                                </div>
                            </div>
                        </div>
                        <div class="card-footer text-dark bg-light collapse small" id="debug_${stealthAccount.address}">
                        <div class="row">
                            <div class="col-sm-3">
                                Verified
                            </div>
                            <div class="col-sm-9">
                                ${stealthAccount.token_balance_verified}
                            </div>                                
                            </div>
                            <div class="row">
                                <div class="col-sm-3">
                                    Stealth Balance
                                </div>
                                <div class="col-sm-9">
                                    ${stealthAccount.token_balance} Wei
                                </div>                                
                            </div>
                            <div class="row">
                                <div class="col-sm-3">
                                    Blinding Factor:
                                </div>
                                <div class="col-sm-9">
                                    ${stealthAccount.token_blinding_factor.toString(10)}
                                </div>                                
                            </div>
                            <div class="row">
                                <div class="col-sm-3">
                                    Balance Pederson Commitment:
                                </div>
                                <div class="col-sm-9">
                                    ${me.prettyPoint(stealthAccount.token_total_commitment)}
                                </div>                                
                            </div>
                            <div class="row">
                                <div class="col-sm-3">
                                    Private Key:
                                </div>
                                <div class="col-sm-9">
                                    ${privatekeys.getStealthKey(i).privatekey.toString("hex")}
                                </div>
                            </div>                            
                            <hr/>                            
                            <div class="row">
                                <div class="col-sm-3">
                                    Ether Balance
                                </div>
                                <div class="col-sm-9">
                                    ${stealthAccount.ether_balance} Wei
                                </div>                                
                            </div>
                            ${getHistoryHTML(stealthAccount.address)}
                        </div>
                    </div>                
                </div>
            </div>
            `);
        }        
    }

    private prettyPoint(point: BigNumber[]): string {
        return `
            <span>
                ${point[0].toString(10)}, <br/>
                ${point[1].toString(10)}
            </span>
        `;
    }

    public showPendingStealthTransferBubble(address: string) {
        jq("#txstatusbubble").append(`
            <a href="#" data-toggle="modal" data-target="#transferstatus-modal">${address} has a pending stealth transfer.</a>
        `);
    }
}

export var view = new View();
