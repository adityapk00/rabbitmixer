import {privatekeys}    from "./privatekey";
import {config}         from "./config";
import {controller}     from "./controller";


class Websocket {
    ws              : WebSocket;

    caller_id       : number = 0;
    callback_mapper : Object = Object();

    public connect(): void {        
        this.ws = new WebSocket("ws://127.0.0.1:18080/");

        this.ws.onopen = function() {
            console.log("Opened!");
            controller.connected();
        };
        
        var me = this;
        this.ws.onmessage = function (evt) {
            var data = JSON.parse(evt.data);

            // When a response arrives, we have to route it to the appropriate callback which was registered in the callback_mapper
            var callback = me.callback_mapper[data.id];
            if (data.status != "OK") {
                console.log("Server Error for request id:" + data.id);
            } else if (data.id == "") {
                // Proactive server notification
                console.log("Server notified us:" + JSON.stringify(data));
                controller.notification(data.response);
            } else if (callback != null) {
                // else, call the callback
                me.callback_mapper[data.id] = null;
                console.log("Processing: " + JSON.stringify(data));
                callback(data);
            } else {
                console.log("Unexpected message from server: " + evt.data);
            }
        };
    
        this.ws.onclose = function() {
            console.log("Closed!");
        };
        
        this.ws.onerror = function(err) {
            console.log("Websocket client Error: " + err);
        };    
    }
    
    /**
     * Make a single call to the websocket, and return the id that was used. 
     * The response callback will have this id. 
     */
    public send(cmd: Object, callback: (data: Object) => void): Number {
        cmd["id"] = this.caller_id++;
        this.callback_mapper[cmd["id"]] = callback;
    
        console.log("Sending :" + JSON.stringify(cmd));
        this.ws.send(JSON.stringify(cmd));

        return cmd["id"];
    }

}

export var websocket: Websocket = new Websocket();