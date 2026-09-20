package com.vega.compiler.lsp.provider;

import com.vega.compiler.lsp.protocol.*;
import com.vega.compiler.lsp.analyzer.SymbolDatabase;
import java.util.*;

/**
 * Provides code completion suggestions
 */
public class CompletionProvider {
    private final SymbolDatabase symbolDatabase;
    
    public CompletionProvider(SymbolDatabase symbolDatabase) {
        this.symbolDatabase = symbolDatabase;
    }
    
    /**
     * Get completion items at position
     */
    public List<LspCompletionItem> getCompletions(String uri, String content, LspPosition position) {
        List<LspCompletionItem> items = new ArrayList<>();
        
        // Get partial word before position
        String prefix = getWordPrefix(content, position);
        
        // Add user-defined symbols
        for (SymbolDatabase.Symbol symbol : symbolDatabase.getAllSymbols()) {
            if (symbol.name.startsWith(prefix)) {
                int kind = switch (symbol.type) {
                    case "function" -> LspCompletionItem.FUNCTION;
                    case "class" -> LspCompletionItem.CLASS;
                    case "struct" -> LspCompletionItem.CLASS;
                    case "variable" -> LspCompletionItem.VARIABLE;
                    default -> LspCompletionItem.TEXT;
                };
                
                LspCompletionItem item = new LspCompletionItem(symbol.name, kind);
                item.sortText = symbol.name;
                items.add(item);
            }
        }
        
        // Add stdlib functions
        if (prefix.isEmpty() || prefix.length() <= 3) {
            items.addAll(getStdlibCompletions(prefix));
        }
        
        // Add keywords
        items.addAll(getKeywordCompletions(prefix));
        
        // Sort by match quality
        items.sort((a, b) -> {
            int aPrefix = a.label.startsWith(prefix) ? 0 : 1;
            int bPrefix = b.label.startsWith(prefix) ? 0 : 1;
            if (aPrefix != bPrefix) return aPrefix - bPrefix;
            return a.label.compareTo(b.label);
        });
        
        return items;
    }
    
    /**
     * Get word prefix before position
     */
    private String getWordPrefix(String content, LspPosition position) {
        String[] lines = content.split("\n");
        if (position.line >= lines.length) {
            return "";
        }
        
        String line = lines[position.line];
        int start = Math.min(position.character, line.length());
        
        while (start > 0 && Character.isJavaIdentifierPart(line.charAt(start - 1))) {
            start--;
        }
        
        return line.substring(start, Math.min(position.character, line.length()));
    }
    
    /**
     * Get stdlib completion items
     */
    private List<LspCompletionItem> getStdlibCompletions(String prefix) {
        List<LspCompletionItem> items = new ArrayList<>();
        
        // io module
        addCompletion(items, "println", "Function", prefix);
        addCompletion(items, "print", "Function", prefix);
        addCompletion(items, "readLine", "Function", prefix);
        addCompletion(items, "file", "Function", prefix);
        addCompletion(items, "read", "Function", prefix);
        addCompletion(items, "write", "Function", prefix);
        addCompletion(items, "exists", "Function", prefix);
        addCompletion(items, "delete", "Function", prefix);
        
        // string module
        addCompletion(items, "len", "Function", prefix);
        addCompletion(items, "toUpper", "Function", prefix);
        addCompletion(items, "toLower", "Function", prefix);
        addCompletion(items, "trim", "Function", prefix);
        addCompletion(items, "split", "Function", prefix);
        addCompletion(items, "contains", "Function", prefix);
        addCompletion(items, "replace", "Function", prefix);
        addCompletion(items, "substring", "Function", prefix);
        addCompletion(items, "join", "Function", prefix);
        
        // math module
        addCompletion(items, "abs", "Function", prefix);
        addCompletion(items, "sqrt", "Function", prefix);
        addCompletion(items, "pow", "Function", prefix);
        addCompletion(items, "sin", "Function", prefix);
        addCompletion(items, "cos", "Function", prefix);
        addCompletion(items, "tan", "Function", prefix);
        addCompletion(items, "max", "Function", prefix);
        addCompletion(items, "min", "Function", prefix);
        addCompletion(items, "PI", "Constant", prefix);
        addCompletion(items, "E", "Constant", prefix);
        
        // collections module
        addCompletion(items, "push", "Function", prefix);
        addCompletion(items, "pop", "Function", prefix);
        addCompletion(items, "map", "Function", prefix);
        addCompletion(items, "filter", "Function", prefix);
        addCompletion(items, "fold", "Function", prefix);
        addCompletion(items, "any", "Function", prefix);
        addCompletion(items, "all", "Function", prefix);
        addCompletion(items, "arrayOf", "Function", prefix);
        
        // errors module
        addCompletion(items, "ok", "Function", prefix);
        addCompletion(items, "err", "Function", prefix);
        addCompletion(items, "error", "Function", prefix);
        addCompletion(items, "some", "Function", prefix);
        addCompletion(items, "none", "Function", prefix);
        
        // time module
        addCompletion(items, "now", "Function", prefix);
        addCompletion(items, "today", "Function", prefix);
        addCompletion(items, "sleep", "Function", prefix);
        
        return items;
    }
    
    /**
     * Get keyword completions
     */
    private List<LspCompletionItem> getKeywordCompletions(String prefix) {
        List<LspCompletionItem> items = new ArrayList<>();
        
        String[] keywords = {
            "fn", "let", "var", "if", "else", "for", "while", "return",
            "class", "struct", "enum", "case", "match", "import", "package",
            "true", "false", "nil", "async", "await", "try", "catch",
            "public", "private", "protected", "break", "continue"
        };
        
        for (String keyword : keywords) {
            if (keyword.startsWith(prefix)) {
                LspCompletionItem item = new LspCompletionItem(keyword, LspCompletionItem.KEYWORD);
                item.sortText = " " + keyword;  // Keywords sort first
                items.add(item);
            }
        }
        
        return items;
    }
    
    /**
     * Add completion if matches prefix
     */
    private void addCompletion(List<LspCompletionItem> items, String name, String type, String prefix) {
        if (name.startsWith(prefix)) {
            int kind = switch (type) {
                case "Function" -> LspCompletionItem.FUNCTION;
                case "Class" -> LspCompletionItem.CLASS;
                case "Variable" -> LspCompletionItem.VARIABLE;
                case "Module" -> LspCompletionItem.MODULE;
                case "Constant" -> LspCompletionItem.VARIABLE;
                default -> LspCompletionItem.TEXT;
            };
            
            LspCompletionItem item = new LspCompletionItem(name, kind, type);
            items.add(item);
        }
    }
}
