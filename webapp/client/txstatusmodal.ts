import {model    }          from "./model";

class TxStatusModal {
    realJq;
    id: string = "#transferstatus-modal";

    public init() {
        this.realJq = window["$"];
        var me = this;

        me.realJq(me.id).on("hidden.bs.modal", function(data) {
            me.realJq(me.id + " .address").text("");
        });

        me.realJq(me.id).on("shown.bs.modal", function(data) {
            var address = data.relatedTarget.id.substr(-42);
            me.realJq(me.id + " .address").text(address);

            // Get the status from the model.
            var details = model.getAccountDetails(address);
            me.realJq(me.id + " .statuses").empty();

            details.mix_status[0].statusItems.forEach(s => {
                if (s["txHash"] == null) s["txHash"] = "";
                var row = `
                    <div class="row">
                        <div class="col-sm-3">${s["stepName"]}</div>
                        <div class="col-sm-1">${s["status"]}</div>
                        <div class="col-sm-8">${s["txHash"]}</div>
                    </div> 
                `;
                me.realJq(me.id + " .statuses").append(row);
            });

            
        });
    }



}

export var txstatusmodal: TxStatusModal = new TxStatusModal();