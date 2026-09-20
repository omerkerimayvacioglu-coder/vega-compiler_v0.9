package com.vega.compiler.lsp.analyzer;

import com.vega.compiler.lsp.protocol.*;
import java.util.*;
import java.util.regex.*;

/**
 * Diagnostics engine for finding errors in code
 */
public class DiagnosticsEngine {
    private final SymbolDatabase symbolDatabase;
    
    public DiagnosticsEngine(SymbolDatabase symbolDatabase) {
        this.symbolDatabase = symbolDatabase;
    }
    
    /**
     * Get diagnostics for a file
     */
    public List<LspDiagnostic> getDiagnostics(String uri, String content) {
        List<LspDiagnostic> diagnostics = new ArrayList<>();
        
        // Check for syntax errors
        diagnostics.addAll(checkSyntaxErrors(content));
        
        // Check for undefined symbols
        diagnostics.addAll(checkUndefinedSymbols(content));
        
        // Check for type mismatches (basic)
        diagnostics.addAll(checkTypeIssues(content));
        
        return diagnostics;
    }
    
    /**
     * Check for syntax errors
     */
    private List<LspDiagnostic> checkSyntaxErrors(String content) {
        List<LspDiagnostic> errors = new ArrayList<>();
        String[] lines = content.split("\n");
        
        int lineNum = 0;
        Stack<Character> bracketStack = new Stack<>();
        
        for (String line : lines) {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                switch (c) {
                    case '{', '[', '(' -> bracketStack.push(c);
                    case '}' -> {
                        if (bracketStack.empty() || bracketStack.peek() != '{') {
                            errors.add(new LspDiagnostic(
                                new LspRange(lineNum, i, lineNum, i + 1),
                                LspDiagnostic.ERROR,
                                "Mismatched bracket: }"
                            ));
                        } else {
                            bracketStack.pop();
                        }
                    }
                    case ']' -> {
                        if (bracketStack.empty() || bracketStack.peek() != '[') {
                            errors.add(new LspDiagnostic(
                                new LspRange(lineNum, i, lineNum, i + 1),
                                LspDiagnostic.ERROR,
                                "Mismatched bracket: ]"
                            ));
                        } else {
                            bracketStack.pop();
                        }
                    }
                    case ')' -> {
                        if (bracketStack.empty() || bracketStack.peek() != '(') {
                            errors.add(new LspDiagnostic(
                                new LspRange(lineNum, i, lineNum, i + 1),
                                LspDiagnostic.ERROR,
                                "Mismatched bracket: )"
                            ));
                        } else {
                            bracketStack.pop();
                        }
                    }
                }
            }
            lineNum++;
        }
        
        // Check unclosed brackets
        if (!bracketStack.empty()) {
            errors.add(new LspDiagnostic(
                new LspRange(lineNum - 1, 0, lineNum, 0),
                LspDiagnostic.ERROR,
                "Unclosed bracket"
            ));
        }
        
        return errors;
    }
    
    /**
     * Check for undefined symbols
     */
    private List<LspDiagnostic> checkUndefinedSymbols(String content) {
        List<LspDiagnostic> errors = new ArrayList<>();
        String[] lines = content.split("\n");
        
        // Known stdlib functions
        Set<String> knownSymbols = new HashSet<>();
        knownSymbols.addAll(getStdlibFunctions());
        
        // Add user-defined functions
        for (SymbolDatabase.Symbol sym : symbolDatabase.getAllSymbols()) {
            knownSymbols.add(sym.name);
        }
        
        // Built-in keywords
        knownSymbols.addAll(List.of(
            "fn", "let", "var", "class", "struct", "enum", "if", "else",
            "for", "while", "return", "break", "continue", "match",
            "case", "import", "package", "public", "private", "protected",
            "true", "false", "nil", "async", "await", "try", "catch"
        ));
        
        // Add basic types
        knownSymbols.addAll(List.of(
            "Int", "Float", "Double", "String", "Bool", "Char", "Array"
        ));
        
        // Scan for undefined identifiers
        int lineNum = 0;
        for (String line : lines) {
            // Skip comments
            if (line.trim().startsWith("//")) {
                lineNum++;
                continue;
            }
            
            // Find identifiers
            Pattern pattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
            Matcher matcher = pattern.matcher(line);
            
            while (matcher.find()) {
                String identifier = matcher.group(1);
                
                // Skip if it's a keyword or known symbol
                if (!knownSymbols.contains(identifier) && !Character.isUpperCase(identifier.charAt(0))) {
                    // Check if it looks like a function call or assignment
                    int endPos = matcher.end();
                    if (endPos < line.length() && line.charAt(endPos) == '(') {
                        errors.add(new LspDiagnostic(
                            new LspRange(lineNum, matcher.start(), lineNum, matcher.end()),
                            LspDiagnostic.WARNING,
                            "'" + identifier + "' may be undefined"
                        ));
                    }
                }
            }
            
            lineNum++;
        }
        
        return errors;
    }
    
    /**
     * Check for type issues
     */
    private List<LspDiagnostic> checkTypeIssues(String content) {
        List<LspDiagnostic> errors = new ArrayList<>();
        
        // Basic type checking - look for obvious issues
        int lineNum = 0;
        for (String line : content.split("\n")) {
            // Check for unmatched quotes
            int singleQuotes = 0;
            int doubleQuotes = 0;
            boolean inString = false;
            
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                    doubleQuotes++;
                    inString = !inString;
                }
            }
            
            if (doubleQuotes % 2 != 0) {
                errors.add(new LspDiagnostic(
                    new LspRange(lineNum, 0, lineNum, line.length()),
                    LspDiagnostic.ERROR,
                    "Unclosed string literal"
                ));
            }
            
            lineNum++;
        }
        
        return errors;
    }
    
    /**
     * Get stdlib functions
     */
    private Set<String> getStdlibFunctions() {
        return new HashSet<>(List.of(
            // io
            "println", "print", "printf", "readLine", "readInt",
            "file", "read", "write", "append", "exists", "delete", "size",
            "mkdir", "mkdirs", "listFiles", "absolutePath", "fileName", "parentDir",
            // string
            "len", "isEmpty", "toUpper", "toLower", "trim", "trimStart", "trimEnd",
            "substring", "charAt", "first", "last", "startsWith", "endsWith", "contains",
            "indexOf", "lastIndexOf", "count", "replace", "replaceAll", "replaceRegex",
            "split", "splitRegex", "lines", "join", "toCamelCase", "toSnakeCase",
            "toKebabCase", "capitalize", "capitalizeWords", "padEnd", "padStart", "repeat",
            "toInt", "toFloat", "toDouble", "toBool", "toString", "compare",
            "compareIgnoreCase", "equals", "equalsIgnoreCase", "isDigit", "isLetter",
            "isWhitespace", "isUpper", "isLower", "toUpperChar", "toLowerChar",
            // math
            "abs", "max", "min", "sign", "pow", "sqrt", "cbrt", "exp", "ln", "log10",
            "log", "sin", "cos", "tan", "asin", "acos", "atan", "atan2", "sinh",
            "cosh", "tanh", "toRadians", "toDegrees", "round", "floor", "ceil", "trunc",
            "random", "randomInt", "randomFloat", "sum", "average",
            // collections
            "push", "pop", "insert", "remove", "get", "put", "containsKey", "keys",
            "values", "clear", "size", "add", "union", "intersection", "difference",
            "reverse", "sort", "slice", "filter", "map", "fold", "any", "all", "arrayOf",
            // errors
            "error", "errorWithCause", "message", "cause", "ok", "err", "isOk", "isErr",
            "getOrNil", "getOrDefault", "getError", "andThen", "orElse", "some", "none",
            "isSome", "isNone", "getOrThrow", "try", "tryRun", "notFoundError",
            "invalidOperationError", "nullReferenceError", "argumentError", "parseError",
            "ioError", "timeoutError", "wrapError", "getFullErrorMessage", "errorContains",
            "requireNonNull", "ifNotNull", "chain",
            // time
            "now", "today", "date", "time", "dateTime", "currentTime", "parseDate",
            "parseTime", "parseDateTime", "formatDate", "formatTime", "formatDateTime",
            "addDays", "addMonths", "addYears", "addHours", "addMinutes", "addSeconds",
            "dayOfWeek", "dayOfYear", "isLeapYear", "daysInMonth", "stopwatch", "start",
            "stop", "elapsed", "reset", "sleep", "sleepMillis", "sleepSeconds",
            "getCurrentTimeMillis", "getCurrentTimeNanos", "utc", "systemZone"
        ));
    }
}
