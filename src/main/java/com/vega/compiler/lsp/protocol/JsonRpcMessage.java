package com.vega.compiler.lsp.protocol;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base JSON-RPC message
 */
public class JsonRpcMessage {
    public static final String JSONRPC = "2.0";
    
    public String jsonrpc = JSONRPC;
    public Object id;
    public String method;
    public Map<String, Object> params = new ConcurrentHashMap<>();
    
    public JsonRpcMessage() {}
    
    public JsonRpcMessage(String method) {
        this.method = method;
    }
    
    public JsonRpcMessage(String method, Object id) {
        this.method = method;
        this.id = id;
    }
    
    @Override
    public String toString() {
        return "JsonRpcMessage{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", id=" + id +
                ", method='" + method + '\'' +
                ", params=" + params +
                '}';
    }
}
