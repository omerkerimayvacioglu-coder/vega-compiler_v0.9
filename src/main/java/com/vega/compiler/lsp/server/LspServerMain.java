package com.vega.compiler.lsp.server;

/**
 * Entry point for Vega Language Server
 * Starts LSP server and communicates with client via JSON-RPC over stdin/stdout
 */
public class LspServerMain {
    
    public static void main(String[] args) {
        System.err.println("=".repeat(50));
        System.err.println("Vega Language Server - Starting");
        System.err.println("=".repeat(50));
        
        try {
            VegaLanguageServer server = new VegaLanguageServer();
            
            // Start server in background thread
            Thread serverThread = new Thread(server, "Vega-LSP-Server");
            serverThread.start();
            
            // Wait for shutdown
            serverThread.join();
            
        } catch (Exception e) {
            System.err.println("Error starting Vega Language Server:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
