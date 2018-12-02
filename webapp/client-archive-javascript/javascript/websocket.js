keys        = require("./privatekeys");
config      = require("./config");
controller  = require("./controller");

var ws;
exports.connect = function() {
    ws = new WebSocket("ws://127.0.0.1:18080/");

    ws.onopen = function() {
        console.log("Opened!");
        controller.connected();
    };
    
    ws.onmessage = function (evt) {
        var data = JSON.parse(evt.data);

        // When a response arrives, we have to route it to the appropriate callback which was registered in the callback_mapper
        callback = callback_mapper[data.id];
        if (data.status != "OK") {
            console.log("Server Error for request id:" + data.id);
        } else if (data.id == "") {
            // Proactive server notification
            console.log("Server notified us:" + JSON.stringify(data));
            controller.notification(data.response);
        } else if (callback != null) {
            // else, call the callback
            callback_mapper[data.id] = null;
            console.log("Processing: " + JSON.stringify(data));
            callback(data);
        } else {
            console.log("Unexpected message from server: " + evt.data);
        }
    };
    
    ws.onclose = function() {
        console.log("Closed!");
    };
    
    ws.onerror = function(err) {
        console.log("Websocket client Error: " + err);
    };    
}

var caller_id = 0;
var callback_mapper = Object();

exports.send = function(cmd, callback) {
    cmd["id"] = caller_id++;
    callback_mapper[cmd["id"]] = callback;

    console.log("Sending :" + JSON.stringify(cmd));
    ws.send(JSON.stringify(cmd));
}