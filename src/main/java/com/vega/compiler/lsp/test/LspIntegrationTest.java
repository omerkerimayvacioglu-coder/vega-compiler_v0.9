package com.vega.compiler.lsp.test;

import com.vega.compiler.lsp.protocol.*;
import com.vega.compiler.lsp.analyzer.SymbolDatabase;
import com.vega.compiler.lsp.analyzer.DiagnosticsEngine;
import com.vega.compiler.lsp.provider.*;
import java.util.*;

/**
 * Integration tests for LSP components
 */
public class LspIntegrationTest {
    
    private static final String TEST_CODE = """
        package examples
        
        import stdlib.io.*
        import stdlib.string.*
        import stdlib.math.*
        
        fn add(a: Int, b: Int): Int {
            return a + b
        }
        
        fn greet(name: String): String {
            return "Hello, " + name
        }
        
        fn main() {
            let result = add(5, 3)
            println("Sum: \\(result)")
            
            let greeting = greet("Vega")
            println(greeting)
            
            let text = "Hello"
            println("Length: \\(len(text))")
            
            let x = sqrt(16.0)
            println("sqrt(16) = \\(x)")
        }
        """;
    
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Vega LSP Integration Test Suite");
        System.out.println("=".repeat(60));
        
        SymbolDatabase symbolDb = new SymbolDatabase();
        DiagnosticsEngine diagnosticsEngine = new DiagnosticsEngine(symbolDb);
        HoverProvider hoverProvider = new HoverProvider(symbolDb);
        CompletionProvider completionProvider = new CompletionProvider(symbolDb);
        DefinitionProvider definitionProvider = new DefinitionProvider(symbolDb);
        
        int passed = 0;
        int failed = 0;
        
        // Test 1: Symbol indexing
        System.out.println("\n[Test 1] Symbol Indexing");
        try {
            symbolDb.indexFile("test.vg", TEST_CODE);
            List<SymbolDatabase.Symbol> symbols = symbolDb.getAllSymbols();
            if (symbols.size() > 0) {
                System.out.println("✓ Successfully indexed symbols");
                System.out.println("  Found " + symbols.size() + " symbols");
                passed++;
            } else {
                System.out.println("✗ No symbols found");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            failed++;
        }
        
        // Test 2: Get symbol at position
        System.out.println("\n[Test 2] Get Symbol at Position");
        try {
            LspPosition pos = new LspPosition(8, 20);  // "add" function
            SymbolDatabase.Symbol sym = symbolDb.getSymbolAtPosition("test.vg", TEST_CODE, pos);
            if (sym != null) {
                System.out.println("✓ Found symbol: " + sym.name + " (" + sym.type + ")");
                passed++;
            } else {
                System.out.println("✗ Symbol not found at position");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            failed++;
        }
        
        // Test 3: Diagnostics
        System.out.println("\n[Test 3] Diagnostics Generation");
        try {
            List<LspDiagnostic> diagnostics = diagnosticsEngine.getDiagnostics("test.vg", TEST_CODE);
            System.out.println("✓ Generated " + diagnostics.size() + " diagnostics");
            for (LspDiagnostic diag : diagnostics) {
                System.out.println("  - " + diag);
            }
            passed++;
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            failed++;
        }
        
        // Test 4: Hover information
        System.out.println("\n[Test 4] Hover Information");
        try {
            LspPosition pos = new LspPosition(8, 20);  // "add" function
            LspHover hover = hoverProvider.getHover("test.vg", TEST_CODE, pos);
            if (hover != null && hover.contents != null) {
                System.out.println("✓ Hover info generated:");
                System.out.println("  " + hover.contents.substring(0, Math.min(50, hover.contents.length())) + "...");
                passed++;
            } else {
                System.out.println("✗ No hover info available");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            failed++;
        }
        
        // Test 5: Code completion
        System.out.println("\n[Test 5] Code Completion");
        try {
            LspPosition pos = new LspPosition(14, 20);  // After "prin"
            List<LspCompletionItem> completions = completionProvider.getCompletions("test.vg", TEST_CODE, pos);
            System.out.println("✓ Generated " + completions.size() + " completion items");
            completions.stream().limit(5).forEach(item -> 
                System.out.println("  - " + item.label + " (" + item.detail + ")")
            );
            if (completions.size() > 0) {
                passed++;
            } else {
                failed++;
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            failed++;
        }
        
        // Test 6: Go to definition
        System.out.println("\n[Test 6] Go to Definition");
        try {
            LspPosition pos = new LspPosition(8, 20);  // "add" function
            LspLocation location = definitionProvider.getDefinition("test.vg", TEST_CODE, pos);
            if (location != null) {
                System.out.println("✓ Found definition at: " + location);
                passed++;
            } else {
                System.out.println("✗ Definition not found");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            failed++;
        }
        
        // Test 7: Syntax errors
        System.out.println("\n[Test 7] Syntax Error Detection");
        try {
            String errorCode = """
                fn test() {
                    let x = 5
                    // Missing closing brace
                """;
            List<LspDiagnostic> diagnostics = diagnosticsEngine.getDiagnostics("error.vg", errorCode);
            if (diagnostics.size() > 0) {
                System.out.println("✓ Detected " + diagnostics.size() + " syntax errors");
                passed++;
            } else {
                System.out.println("✗ No errors detected");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            failed++;
        }
        
        // Test 8: Stdlib symbol lookup
        System.out.println("\n[Test 8] Stdlib Symbol Lookup");
        try {
            List<SymbolDatabase.Symbol> ioSymbols = symbolDb.getStdlibSymbols("io");
            if (ioSymbols.size() > 0) {
                System.out.println("✓ Found " + ioSymbols.size() + " io module symbols");
                passed++;
            } else {
                System.out.println("✗ No stdlib symbols found");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            failed++;
        }
        
        // Summary
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Test Results: " + passed + " passed, " + failed + " failed");
        System.out.println("=".repeat(60));
        
        if (failed == 0) {
            System.out.println("✓ All tests passed!");
            System.exit(0);
        } else {
            System.out.println("✗ Some tests failed");
            System.exit(1);
        }
    }
}
