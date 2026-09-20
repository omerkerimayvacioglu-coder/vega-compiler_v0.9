package com.vega.compiler.lsp.server;

import com.vega.compiler.lsp.protocol.*;
import com.vega.compiler.lsp.analyzer.SymbolDatabase;
import com.vega.compiler.lsp.analyzer.DiagnosticsEngine;
import com.vega.compiler.lsp.provider.HoverProvider;
import com.vega.compiler.lsp.provider.CompletionProvider;
import com.vega.compiler.lsp.provider.DefinitionProvider;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * Vega Language Server - implements LSP for Vega language
 */
public class VegaLanguageServer extends JsonRpcServer {
    private final SymbolDatabase symbolDatabase;
    private final DiagnosticsEngine diagnosticsEngine;
    private final HoverProvider hoverProvider;
    private final CompletionProvider completionProvider;
    private final DefinitionProvider definitionProvider;
    
    private final Map<String, String> openDocuments = new HashMap<>();
    private boolean initialized = false;
    
    public VegaLanguageServer() {
        this.symbolDatabase = new SymbolDatabase();
        this.diagnosticsEngine = new DiagnosticsEngine(symbolDatabase);
        this.hoverProvider = new HoverProvider(symbolDatabase);
        this.completionProvider = new CompletionProvider(symbolDatabase);
        this.definitionProvider = new DefinitionProvider(symbolDatabase);
    }
    
    @Override
    protected JsonRpcResponse handleRequest(String method, Object id, Object params) {
        return switch (method) {
            case "initialize" -> handleInitialize(id, params);
            case "textDocument/hover" -> handleHover(id, params);
            case "textDocument/completion" -> handleCompletion(id, params);
            case "textDocument/definition" -> handleDefinition(id, params);
            case "textDocument/didOpen" -> handleDidOpen(id, params);
            case "textDocument/didChange" -> handleDidChange(id, params);
            case "textDocument/didClose" -> handleDidClose(id, params);
            case "shutdown" -> handleShutdown(id, params);
            default -> new JsonRpcResponse(id, new JsonRpcError(-32601, "Method not found"));
        };
    }
    
    @Override
    protected void handleNotification(String method, Object params) {
        switch (method) {
            case "exit" -> System.exit(0);
            case "textDocument/didOpen" -> handleDidOpenNotification(params);
            case "textDocument/didChange" -> handleDidChangeNotification(params);
            case "textDocument/didClose" -> handleDidCloseNotification(params);
        }
    }
    
    /**
     * Initialize - responds to client capabilities
     */
    private JsonRpcResponse handleInitialize(Object id, Object params) {
        initialized = true;
        
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("hoverProvider", true);
        capabilities.addProperty("completionProvider", true);
        capabilities.addProperty("definitionProvider", true);
        capabilities.addProperty("textDocumentSync", 1);  // Full text sync
        
        JsonObject result = new JsonObject();
        result.add("capabilities", capabilities);
        
        System.err.println("[LSP] Initialized");
        return new JsonRpcResponse(id, result);
    }
    
    /**
     * Hover - return type info for symbol at position
     */
    private JsonRpcResponse handleHover(Object id, Object params) {
        if (!initialized) {
            return new JsonRpcResponse(id, null);
        }
        
        try {
            // Parse textDocument/position from params
            String uri = extractUri(params);
            LspPosition position = extractPosition(params);
            
            if (uri != null && position != null) {
                String content = openDocuments.get(uri);
                if (content != null) {
                    LspHover hover = hoverProvider.getHover(uri, content, position);
                    return new JsonRpcResponse(id, hover);
                }
            }
        } catch (Exception e) {
            System.err.println("[LSP] Error in hover: " + e.getMessage());
        }
        
        return new JsonRpcResponse(id, null);
    }
    
    /**
     * Completion - return completion items at position
     */
    private JsonRpcResponse handleCompletion(Object id, Object params) {
        if (!initialized) {
            return new JsonRpcResponse(id, new ArrayList<>());
        }
        
        try {
            String uri = extractUri(params);
            LspPosition position = extractPosition(params);
            
            if (uri != null && position != null) {
                String content = openDocuments.get(uri);
                if (content != null) {
                    List<LspCompletionItem> items = completionProvider.getCompletions(uri, content, position);
                    return new JsonRpcResponse(id, items);
                }
            }
        } catch (Exception e) {
            System.err.println("[LSP] Error in completion: " + e.getMessage());
        }
        
        return new JsonRpcResponse(id, new ArrayList<>());
    }
    
    /**
     * Go to definition - return location of symbol definition
     */
    private JsonRpcResponse handleDefinition(Object id, Object params) {
        if (!initialized) {
            return new JsonRpcResponse(id, null);
        }
        
        try {
            String uri = extractUri(params);
            LspPosition position = extractPosition(params);
            
            if (uri != null && position != null) {
                String content = openDocuments.get(uri);
                if (content != null) {
                    LspLocation location = definitionProvider.getDefinition(uri, content, position);
                    return new JsonRpcResponse(id, location);
                }
            }
        } catch (Exception e) {
            System.err.println("[LSP] Error in definition: " + e.getMessage());
        }
        
        return new JsonRpcResponse(id, null);
    }
    
    /**
     * Document opened
     */
    private JsonRpcResponse handleDidOpen(Object id, Object params) {
        handleDidOpenNotification(params);
        return new JsonRpcResponse(id, null);
    }
    
    /**
     * Document changed
     */
    private JsonRpcResponse handleDidChange(Object id, Object params) {
        handleDidChangeNotification(params);
        return new JsonRpcResponse(id, null);
    }
    
    /**
     * Document closed
     */
    private JsonRpcResponse handleDidClose(Object id, Object params) {
        handleDidCloseNotification(params);
        return new JsonRpcResponse(id, null);
    }
    
    /**
     * Shutdown server
     */
    private JsonRpcResponse handleShutdown(Object id, Object params) {
        shutdown();
        return new JsonRpcResponse(id, null);
    }
    
    // Notification handlers
    
    private void handleDidOpenNotification(Object params) {
        try {
            String uri = extractUri(params);
            String content = extractContent(params);
            
            if (uri != null && content != null) {
                openDocuments.put(uri, content);
                symbolDatabase.indexFile(uri, content);
                
                // Send diagnostics
                List<LspDiagnostic> diagnostics = diagnosticsEngine.getDiagnostics(uri, content);
                sendDiagnosticsNotification(uri, diagnostics);
                
                System.err.println("[LSP] Opened: " + uri);
            }
        } catch (Exception e) {
            System.err.println("[LSP] Error in didOpen: " + e.getMessage());
        }
    }
    
    private void handleDidChangeNotification(Object params) {
        try {
            String uri = extractUri(params);
            String content = extractContent(params);
            
            if (uri != null && content != null) {
                openDocuments.put(uri, content);
                symbolDatabase.indexFile(uri, content);
                
                // Send diagnostics
                List<LspDiagnostic> diagnostics = diagnosticsEngine.getDiagnostics(uri, content);
                sendDiagnosticsNotification(uri, diagnostics);
                
                System.err.println("[LSP] Changed: " + uri);
            }
        } catch (Exception e) {
            System.err.println("[LSP] Error in didChange: " + e.getMessage());
        }
    }
    
    private void handleDidCloseNotification(Object params) {
        try {
            String uri = extractUri(params);
            
            if (uri != null) {
                openDocuments.remove(uri);
                symbolDatabase.removeFile(uri);
                
                // Clear diagnostics
                sendDiagnosticsNotification(uri, new ArrayList<>());
                
                System.err.println("[LSP] Closed: " + uri);
            }
        } catch (Exception e) {
            System.err.println("[LSP] Error in didClose: " + e.getMessage());
        }
    }
    
    // Helper methods
    
    private String extractUri(Object params) {
        if (params instanceof JsonObject json) {
            if (json.has("textDocument")) {
                return json.getAsJsonObject("textDocument").get("uri").getAsString();
            }
        }
        return null;
    }
    
    private LspPosition extractPosition(Object params) {
        if (params instanceof JsonObject json && json.has("position")) {
            JsonObject pos = json.getAsJsonObject("position");
            return new LspPosition(
                pos.get("line").getAsInt(),
                pos.get("character").getAsInt()
            );
        }
        return null;
    }
    
    private String extractContent(Object params) {
        if (params instanceof JsonObject json && json.has("textDocument")) {
            JsonObject td = json.getAsJsonObject("textDocument");
            return td.has("text") ? td.get("text").getAsString() : null;
        }
        return null;
    }
    
    private void sendDiagnosticsNotification(String uri, List<LspDiagnostic> diagnostics) {
        JsonObject params = new JsonObject();
        params.addProperty("uri", uri);
        // params.add("diagnostics", gson.toJsonTree(diagnostics));
        sendNotification("textDocument/publishDiagnostics", params);
    }
}
