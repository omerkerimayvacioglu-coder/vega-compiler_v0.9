package com.vega.compiler.lsp.server;

import com.vega.compiler.lsp.protocol.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Base JSON-RPC server that communicates via stdin/stdout
 * Used by LSP (Language Server Protocol)
 */
public abstract class JsonRpcServer implements Runnable {
    protected final BufferedReader reader;
    protected final PrintWriter writer;
    protected final Gson gson;
    protected volatile boolean running = true;
    protected ExecutorService executorService;
    
    protected static final String HEADER_CONTENT_LENGTH = "Content-Length: ";
    protected static final String HEADER_CONTENT_TYPE = "Content-Type: ";
    
    public JsonRpcServer() {
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.writer = new PrintWriter(new BufferedOutputStream(System.out), false);
        this.gson = new Gson();
        this.executorService = Executors.newFixedThreadPool(4);
    }
    
    @Override
    public void run() {
        System.err.println("[LSP] Server starting...");
        try {
            while (running) {
                try {
                    // Read message headers
                    Map<String, String> headers = readMessageHeaders();
                    if (headers.isEmpty()) {
                        break;
                    }
                    
                    String contentLength = headers.get("Content-Length");
                    if (contentLength == null) {
                        System.err.println("[LSP] Missing Content-Length header");
                        continue;
                    }
                    
                    int length = Integer.parseInt(contentLength);
                    char[] buffer = new char[length];
                    int read = reader.read(buffer, 0, length);
                    
                    if (read != length) {
                        System.err.println("[LSP] Could not read full message");
                        continue;
                    }
                    
                    String messageStr = new String(buffer);
                    handleMessage(messageStr);
                    
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[LSP] I/O error: " + e.getMessage());
                    }
                    break;
                }
            }
        } finally {
            shutdown();
        }
    }
    
    /**
     * Read message headers until empty line
     */
    private Map<String, String> readMessageHeaders() throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex);
                String value = line.substring(colonIndex + 2);
                headers.put(key, value);
            }
        }
        return headers;
    }
    
    /**
     * Handle incoming JSON-RPC message
     */
    protected void handleMessage(String messageStr) {
        try {
            JsonObject json = JsonParser.parseString(messageStr).getAsJsonObject();
            
            // Check if it's a notification or request
            if (json.has("method")) {
                String method = json.get("method").getAsString();
                
                // Execute as async task
                executorService.submit(() -> {
                    try {
                        if (json.has("id")) {
                            // Request - needs response
                            Object id = json.get("id").getAsNumber();
                            Object params = json.has("params") ? json.get("params") : null;
                            
                            JsonRpcResponse response = handleRequest(method, id, params);
                            sendResponse(response);
                        } else {
                            // Notification - no response
                            Object params = json.has("params") ? json.get("params") : null;
                            handleNotification(method, params);
                        }
                    } catch (Exception e) {
                        System.err.println("[LSP] Error handling message: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[LSP] Error parsing message: " + e.getMessage());
        }
    }
    
    /**
     * Handle JSON-RPC request - must return response
     */
    protected abstract JsonRpcResponse handleRequest(String method, Object id, Object params);
    
    /**
     * Handle JSON-RPC notification - no response needed
     */
    protected abstract void handleNotification(String method, Object params);
    
    /**
     * Send JSON-RPC response
     */
    protected void sendResponse(JsonRpcResponse response) {
        try {
            String responseStr = gson.toJson(response);
            byte[] responseBytes = responseStr.getBytes("UTF-8");
            
            synchronized (writer) {
                writer.print(HEADER_CONTENT_LENGTH + responseBytes.length + "\r\n");
                writer.print(HEADER_CONTENT_TYPE + "application/vscode-jsonrpc; charset=utf-8\r\n");
                writer.print("\r\n");
                writer.print(responseStr);
                writer.flush();
            }
        } catch (Exception e) {
            System.err.println("[LSP] Error sending response: " + e.getMessage());
        }
    }
    
    /**
     * Send notification (no response from client)
     */
    protected void sendNotification(String method, Object params) {
        try {
            JsonRpcMessage message = new JsonRpcMessage(method);
            if (params != null) {
                message.params.put("params", params);
            }
            
            String messageStr = gson.toJson(message);
            byte[] messageBytes = messageStr.getBytes("UTF-8");
            
            synchronized (writer) {
                writer.print(HEADER_CONTENT_LENGTH + messageBytes.length + "\r\n");
                writer.print(HEADER_CONTENT_TYPE + "application/vscode-jsonrpc; charset=utf-8\r\n");
                writer.print("\r\n");
                writer.print(messageStr);
                writer.flush();
            }
        } catch (Exception e) {
            System.err.println("[LSP] Error sending notification: " + e.getMessage());
        }
    }
    
    /**
     * Send error response
     */
    protected void sendError(Object id, int code, String message) {
        JsonRpcResponse response = new JsonRpcResponse(
            id,
            new JsonRpcError(code, message)
        );
        sendResponse(response);
    }
    
    /**
     * Shutdown server
     */
    public void shutdown() {
        running = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        System.err.println("[LSP] Server stopped");
    }
}
