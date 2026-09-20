package com.vega.compiler.lsp.analyzer;

import com.vega.compiler.lsp.protocol.LspPosition;
import com.vega.compiler.lsp.protocol.LspRange;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/**
 * Symbol database for indexing functions, classes, variables etc.
 */
public class SymbolDatabase {
    
    /**
     * Symbol information
     */
    public static class Symbol {
        public String name;
        public String type;  // "function", "class", "variable", "struct", "enum"
        public LspRange range;
        public String signature;  // For functions
        public String documentation;
        public String uri;
        
        public Symbol(String name, String type, LspRange range, String uri) {
            this.name = name;
            this.type = type;
            this.range = range;
            this.uri = uri;
        }
    }
    
    private final Map<String, List<Symbol>> fileSymbols = new ConcurrentHashMap<>();
    private final Map<String, Symbol> globalSymbols = new ConcurrentHashMap<>();
    private final Map<String, List<Symbol>> stdlibSymbols = new ConcurrentHashMap<>();
    
    public SymbolDatabase() {
        loadStdlibSymbols();
    }
    
    /**
     * Load stdlib symbols for autocomplete
     */
    private void loadStdlibSymbols() {
        // io module
        List<Symbol> ioSymbols = new ArrayList<>();
        ioSymbols.add(new Symbol("println", "function", new LspRange(0, 0, 0, 7), "stdlib.io"));
        ioSymbols.add(new Symbol("print", "function", new LspRange(0, 0, 0, 5), "stdlib.io"));
        ioSymbols.add(new Symbol("readLine", "function", new LspRange(0, 0, 0, 8), "stdlib.io"));
        ioSymbols.add(new Symbol("file", "function", new LspRange(0, 0, 0, 4), "stdlib.io"));
        ioSymbols.add(new Symbol("read", "function", new LspRange(0, 0, 0, 4), "stdlib.io"));
        ioSymbols.add(new Symbol("write", "function", new LspRange(0, 0, 0, 5), "stdlib.io"));
        ioSymbols.add(new Symbol("exists", "function", new LspRange(0, 0, 0, 6), "stdlib.io"));
        ioSymbols.add(new Symbol("delete", "function", new LspRange(0, 0, 0, 6), "stdlib.io"));
        stdlibSymbols.put("io", ioSymbols);
        
        // string module
        List<Symbol> stringSymbols = new ArrayList<>();
        stringSymbols.add(new Symbol("len", "function", new LspRange(0, 0, 0, 3), "stdlib.string"));
        stringSymbols.add(new Symbol("toUpper", "function", new LspRange(0, 0, 0, 7), "stdlib.string"));
        stringSymbols.add(new Symbol("toLower", "function", new LspRange(0, 0, 0, 7), "stdlib.string"));
        stringSymbols.add(new Symbol("trim", "function", new LspRange(0, 0, 0, 4), "stdlib.string"));
        stringSymbols.add(new Symbol("contains", "function", new LspRange(0, 0, 0, 8), "stdlib.string"));
        stringSymbols.add(new Symbol("split", "function", new LspRange(0, 0, 0, 5), "stdlib.string"));
        stringSymbols.add(new Symbol("join", "function", new LspRange(0, 0, 0, 4), "stdlib.string"));
        stringSymbols.add(new Symbol("replace", "function", new LspRange(0, 0, 0, 7), "stdlib.string"));
        stringSymbols.add(new Symbol("substring", "function", new LspRange(0, 0, 0, 9), "stdlib.string"));
        stdlibSymbols.put("string", stringSymbols);
        
        // math module
        List<Symbol> mathSymbols = new ArrayList<>();
        mathSymbols.add(new Symbol("abs", "function", new LspRange(0, 0, 0, 3), "stdlib.math"));
        mathSymbols.add(new Symbol("sqrt", "function", new LspRange(0, 0, 0, 4), "stdlib.math"));
        mathSymbols.add(new Symbol("pow", "function", new LspRange(0, 0, 0, 3), "stdlib.math"));
        mathSymbols.add(new Symbol("sin", "function", new LspRange(0, 0, 0, 3), "stdlib.math"));
        mathSymbols.add(new Symbol("cos", "function", new LspRange(0, 0, 0, 3), "stdlib.math"));
        mathSymbols.add(new Symbol("tan", "function", new LspRange(0, 0, 0, 3), "stdlib.math"));
        mathSymbols.add(new Symbol("max", "function", new LspRange(0, 0, 0, 3), "stdlib.math"));
        mathSymbols.add(new Symbol("min", "function", new LspRange(0, 0, 0, 3), "stdlib.math"));
        mathSymbols.add(new Symbol("PI", "variable", new LspRange(0, 0, 0, 2), "stdlib.math"));
        mathSymbols.add(new Symbol("E", "variable", new LspRange(0, 0, 0, 1), "stdlib.math"));
        stdlibSymbols.put("math", mathSymbols);
        
        // collections module
        List<Symbol> collSymbols = new ArrayList<>();
        collSymbols.add(new Symbol("len", "function", new LspRange(0, 0, 0, 3), "stdlib.collections"));
        collSymbols.add(new Symbol("push", "function", new LspRange(0, 0, 0, 4), "stdlib.collections"));
        collSymbols.add(new Symbol("pop", "function", new LspRange(0, 0, 0, 3), "stdlib.collections"));
        collSymbols.add(new Symbol("map", "function", new LspRange(0, 0, 0, 3), "stdlib.collections"));
        collSymbols.add(new Symbol("filter", "function", new LspRange(0, 0, 0, 6), "stdlib.collections"));
        collSymbols.add(new Symbol("fold", "function", new LspRange(0, 0, 0, 4), "stdlib.collections"));
        stdlibSymbols.put("collections", collSymbols);
    }
    
    /**
     * Index file symbols
     */
    public void indexFile(String uri, String content) {
        List<Symbol> symbols = new ArrayList<>();
        
        // Parse functions
        Pattern fnPattern = Pattern.compile("fn\\s+(\\w+)\\s*\\(");
        Matcher fnMatcher = fnPattern.matcher(content);
        int line = 0;
        for (String contentLine : content.split("\n")) {
            Matcher matcher = fnPattern.matcher(contentLine);
            if (matcher.find()) {
                String name = matcher.group(1);
                Symbol sym = new Symbol(name, "function", new LspRange(line, 0, line, contentLine.length()), uri);
                sym.signature = "fn " + name + "()";
                symbols.add(sym);
                globalSymbols.put(name, sym);
            }
            line++;
        }
        
        // Parse classes
        Pattern classPattern = Pattern.compile("class\\s+(\\w+)");
        line = 0;
        for (String contentLine : content.split("\n")) {
            Matcher matcher = classPattern.matcher(contentLine);
            if (matcher.find()) {
                String name = matcher.group(1);
                Symbol sym = new Symbol(name, "class", new LspRange(line, 0, line, contentLine.length()), uri);
                symbols.add(sym);
                globalSymbols.put(name, sym);
            }
            line++;
        }
        
        // Parse structs
        Pattern structPattern = Pattern.compile("struct\\s+(\\w+)");
        line = 0;
        for (String contentLine : content.split("\n")) {
            Matcher matcher = structPattern.matcher(contentLine);
            if (matcher.find()) {
                String name = matcher.group(1);
                Symbol sym = new Symbol(name, "struct", new LspRange(line, 0, line, contentLine.length()), uri);
                symbols.add(sym);
                globalSymbols.put(name, sym);
            }
            line++;
        }
        
        fileSymbols.put(uri, symbols);
        System.err.println("[LSP] Indexed " + symbols.size() + " symbols in " + uri);
    }
    
    /**
     * Remove file symbols
     */
    public void removeFile(String uri) {
        fileSymbols.remove(uri);
    }
    
    /**
     * Get symbol at position
     */
    public Symbol getSymbolAtPosition(String uri, String content, LspPosition position) {
        String word = getWordAtPosition(content, position);
        if (word == null) return null;
        
        // Check local symbols first
        List<Symbol> symbols = fileSymbols.get(uri);
        if (symbols != null) {
            for (Symbol sym : symbols) {
                if (sym.name.equals(word)) {
                    return sym;
                }
            }
        }
        
        // Check global symbols
        if (globalSymbols.containsKey(word)) {
            return globalSymbols.get(word);
        }
        
        // Check stdlib symbols
        for (List<Symbol> stdlibSyns : stdlibSymbols.values()) {
            for (Symbol sym : stdlibSyns) {
                if (sym.name.equals(word)) {
                    return sym;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get word at position in content
     */
    public static String getWordAtPosition(String content, LspPosition position) {
        String[] lines = content.split("\n");
        if (position.line >= lines.length) {
            return null;
        }
        
        String line = lines[position.line];
        if (position.character > line.length()) {
            return null;
        }
        
        // Find word boundaries
        int start = position.character;
        int end = position.character;
        
        while (start > 0 && Character.isJavaIdentifierPart(line.charAt(start - 1))) {
            start--;
        }
        
        while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
            end++;
        }
        
        if (start < end) {
            return line.substring(start, end);
        }
        
        return null;
    }
    
    /**
     * Get all symbols
     */
    public List<Symbol> getAllSymbols() {
        List<Symbol> all = new ArrayList<>(globalSymbols.values());
        fileSymbols.values().forEach(all::addAll);
        stdlibSymbols.values().forEach(all::addAll);
        return all;
    }
    
    /**
     * Get stdlib symbols for given module
     */
    public List<Symbol> getStdlibSymbols(String module) {
        return stdlibSymbols.getOrDefault(module, new ArrayList<>());
    }
}
