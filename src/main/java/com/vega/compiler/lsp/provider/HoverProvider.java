package com.vega.compiler.lsp.provider;

import com.vega.compiler.lsp.protocol.*;
import com.vega.compiler.lsp.analyzer.SymbolDatabase;

/**
 * Provides hover information for symbols
 */
public class HoverProvider {
    private final SymbolDatabase symbolDatabase;
    
    public HoverProvider(SymbolDatabase symbolDatabase) {
        this.symbolDatabase = symbolDatabase;
    }
    
    /**
     * Get hover information at position
     */
    public LspHover getHover(String uri, String content, LspPosition position) {
        SymbolDatabase.Symbol symbol = symbolDatabase.getSymbolAtPosition(uri, content, position);
        
        if (symbol == null) {
            return null;
        }
        
        String documentation = buildDocumentation(symbol);
        return new LspHover(documentation, symbol.range);
    }
    
    /**
     * Build documentation string for symbol
     */
    private String buildDocumentation(SymbolDatabase.Symbol symbol) {
        StringBuilder sb = new StringBuilder();
        
        // Add signature/type
        switch (symbol.type) {
            case "function" -> {
                sb.append("```vega\n");
                sb.append(symbol.signature != null ? symbol.signature : "fn " + symbol.name + "()\n");
                sb.append("```\n");
            }
            case "class" -> {
                sb.append("```vega\nclass ").append(symbol.name).append("\n```\n");
            }
            case "struct" -> {
                sb.append("```vega\nstruct ").append(symbol.name).append("\n```\n");
            }
            case "variable" -> {
                sb.append("```vega\nlet ").append(symbol.name).append("\n```\n");
            }
        }
        
        // Add documentation if available
        if (symbol.documentation != null && !symbol.documentation.isEmpty()) {
            sb.append("\n");
            sb.append(symbol.documentation);
        }
        
        // Add source location
        sb.append("\n\n");
        sb.append("**Location**: ").append(symbol.uri);
        if (symbol.range != null) {
            sb.append(" (").append(symbol.range.start.line + 1).append(":").append(symbol.range.start.character).append(")");
        }
        
        return sb.toString();
    }
    
    /**
     * Get stdlib function documentation
     */
    public static String getStdlibFunctionDoc(String functionName, String module) {
        return switch (module) {
            case "io" -> getIoFunctionDoc(functionName);
            case "string" -> getStringFunctionDoc(functionName);
            case "math" -> getMathFunctionDoc(functionName);
            case "collections" -> getCollectionsFunctionDoc(functionName);
            case "errors" -> getErrorsFunctionDoc(functionName);
            case "time" -> getTimeFunctionDoc(functionName);
            default -> "No documentation available";
        };
    }
    
    private static String getIoFunctionDoc(String name) {
        return switch (name) {
            case "println" -> "Print string with newline\n\n**Signature**: `fn println(msg: String)`";
            case "print" -> "Print string without newline\n\n**Signature**: `fn print(msg: String)`";
            case "readLine" -> "Read line from standard input\n\n**Signature**: `fn readLine(): String`";
            case "file" -> "Create or reference a file\n\n**Signature**: `fn file(path: String): File`";
            case "read" -> "Read entire file as string\n\n**Signature**: `fn read(f: File): String`";
            case "write" -> "Write content to file\n\n**Signature**: `fn write(f: File, content: String)`";
            case "exists" -> "Check if file exists\n\n**Signature**: `fn exists(f: File): Bool`";
            case "delete" -> "Delete file\n\n**Signature**: `fn delete(f: File)`";
            default -> "No documentation available";
        };
    }
    
    private static String getStringFunctionDoc(String name) {
        return switch (name) {
            case "len" -> "Get string length\n\n**Signature**: `fn len(s: String): Int`";
            case "toUpper" -> "Convert to uppercase\n\n**Signature**: `fn toUpper(s: String): String`";
            case "toLower" -> "Convert to lowercase\n\n**Signature**: `fn toLower(s: String): String`";
            case "trim" -> "Remove whitespace\n\n**Signature**: `fn trim(s: String): String`";
            case "split" -> "Split string by delimiter\n\n**Signature**: `fn split(s: String, delimiter: String): Array<String>`";
            case "contains" -> "Check if string contains substring\n\n**Signature**: `fn contains(s: String, substring: String): Bool`";
            case "replace" -> "Replace substring\n\n**Signature**: `fn replace(s: String, target: String, replacement: String): String`";
            case "substring" -> "Get substring\n\n**Signature**: `fn substring(s: String, start: Int, end: Int): String`";
            default -> "No documentation available";
        };
    }
    
    private static String getMathFunctionDoc(String name) {
        return switch (name) {
            case "abs" -> "Absolute value\n\n**Signature**: `fn abs(x: Int): Int`";
            case "sqrt" -> "Square root\n\n**Signature**: `fn sqrt(x: Float): Float`";
            case "pow" -> "Power\n\n**Signature**: `fn pow(x: Float, y: Float): Float`";
            case "sin" -> "Sine (radians)\n\n**Signature**: `fn sin(x: Float): Float`";
            case "cos" -> "Cosine (radians)\n\n**Signature**: `fn cos(x: Float): Float`";
            case "max" -> "Maximum of two numbers\n\n**Signature**: `fn max(a: Int, b: Int): Int`";
            case "min" -> "Minimum of two numbers\n\n**Signature**: `fn min(a: Int, b: Int): Int`";
            case "PI" -> "Pi constant (≈3.14159)\n\n**Type**: `Float`";
            case "E" -> "Euler's number (≈2.71828)\n\n**Type**: `Float`";
            default -> "No documentation available";
        };
    }
    
    private static String getCollectionsFunctionDoc(String name) {
        return switch (name) {
            case "len" -> "Get array length\n\n**Signature**: `fn len<T>(arr: Array<T>): Int`";
            case "push" -> "Add element to array\n\n**Signature**: `fn push<T>(list: Array<T>, element: T)`";
            case "filter" -> "Filter array\n\n**Signature**: `fn filter<T>(list: Array<T>, predicate: (T) -> Bool): Array<T>`";
            case "map" -> "Transform array\n\n**Signature**: `fn map<T, U>(list: Array<T>, transform: (T) -> U): Array<U>`";
            case "fold" -> "Reduce array\n\n**Signature**: `fn fold<T, U>(list: Array<T>, initial: U, f: (U, T) -> U): U`";
            default -> "No documentation available";
        };
    }
    
    private static String getErrorsFunctionDoc(String name) {
        return switch (name) {
            case "ok" -> "Create success result\n\n**Signature**: `fn ok<T>(value: T): Result<T>`";
            case "err" -> "Create error result\n\n**Signature**: `fn err<T>(error: Error): Result<T>`";
            case "error" -> "Create error\n\n**Signature**: `fn error(message: String): Error`";
            case "some" -> "Create some option\n\n**Signature**: `fn some<T>(value: T): Option<T>`";
            case "none" -> "Create empty option\n\n**Signature**: `fn none<T>(): Option<T>`";
            default -> "No documentation available";
        };
    }
    
    private static String getTimeFunctionDoc(String name) {
        return switch (name) {
            case "now" -> "Get current time\n\n**Signature**: `fn now(): LocalDateTime`";
            case "today" -> "Get today's date\n\n**Signature**: `fn today(): LocalDate`";
            case "sleep" -> "Sleep for duration\n\n**Signature**: `fn sleep(d: Duration)`";
            case "sleepMillis" -> "Sleep for milliseconds\n\n**Signature**: `fn sleepMillis(millis: Long)`";
            default -> "No documentation available";
        };
    }
}
