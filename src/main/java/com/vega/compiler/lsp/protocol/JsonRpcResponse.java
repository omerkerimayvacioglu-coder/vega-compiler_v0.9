package com.vega.compiler.lsp.protocol;

/**
 * JSON-RPC response
 */
public class JsonRpcResponse {
    public static final String JSONRPC = "2.0";
    
    public String jsonrpc = JSONRPC;
    public Object id;
    public Object result;
    public JsonRpcError error;
    
    public JsonRpcResponse() {}
    
    public JsonRpcResponse(Object id, Object result) {
        this.id = id;
        this.result = result;
    }
    
    public JsonRpcResponse(Object id, JsonRpcError error) {
        this.id = id;
        this.error = error;
    }
    
    @Override
    public String toString() {
        return "JsonRpcResponse{" +
                "id=" + id +
                ", result=" + result +
                ", error=" + error +
                '}';
    }
}
