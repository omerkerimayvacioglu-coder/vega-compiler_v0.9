package com.vega.compiler.lsp.provider;

import com.vega.compiler.lsp.protocol.*;
import com.vega.compiler.lsp.analyzer.SymbolDatabase;

/**
 * Provides go-to-definition functionality
 */
public class DefinitionProvider {
    private final SymbolDatabase symbolDatabase;
    
    public DefinitionProvider(SymbolDatabase symbolDatabase) {
        this.symbolDatabase = symbolDatabase;
    }
    
    /**
     * Get definition location for symbol at position
     */
    public LspLocation getDefinition(String uri, String content, LspPosition position) {
        SymbolDatabase.Symbol symbol = symbolDatabase.getSymbolAtPosition(uri, content, position);
        
        if (symbol == null) {
            return null;
        }
        
        // Return symbol's location
        if (symbol.range != null) {
            return new LspLocation(symbol.uri, symbol.range);
        }
        
        return null;
    }
    
    /**
     * Get all occurrences of symbol in file
     */
    public java.util.List<LspLocation> getReferences(String uri, String content, LspPosition position) {
        java.util.List<LspLocation> locations = new java.util.ArrayList<>();
        
        SymbolDatabase.Symbol symbol = symbolDatabase.getSymbolAtPosition(uri, content, position);
        if (symbol == null) {
            return locations;
        }
        
        // Find all occurrences in content
        String[] lines = content.split("\n");
        int lineNum = 0;
        
        for (String line : lines) {
            int index = 0;
            while ((index = line.indexOf(symbol.name, index)) != -1) {
                // Check if it's a word boundary
                boolean isWord = (index == 0 || !Character.isJavaIdentifierPart(line.charAt(index - 1))) &&
                                (index + symbol.name.length() >= line.length() || 
                                 !Character.isJavaIdentifierPart(line.charAt(index + symbol.name.length())));
                
                if (isWord) {
                    locations.add(new LspLocation(
                        uri,
                        new LspRange(lineNum, index, lineNum, index + symbol.name.length())
                    ));
                }
                
                index += symbol.name.length();
            }
            
            lineNum++;
        }
        
        return locations;
    }
}
