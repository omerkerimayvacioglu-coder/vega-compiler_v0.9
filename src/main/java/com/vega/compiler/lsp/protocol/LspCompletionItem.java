package com.vega.compiler.lsp.protocol;

/**
 * LSP Completion item for code completion
 */
public class LspCompletionItem {
    public static final int TEXT = 1;
    public static final int METHOD = 2;
    public static final int FUNCTION = 3;
    public static final int VARIABLE = 6;
    public static final int CLASS = 5;
    public static final int INTERFACE = 11;
    public static final int MODULE = 9;
    public static final int KEYWORD = 14;
    public static final int SNIPPET = 15;
    
    public String label;
    public int kind;  // Completion item kind
    public String detail;
    public String documentation;
    public String sortText;
    public String insertText;
    
    public LspCompletionItem() {}
    
    public LspCompletionItem(String label, int kind) {
        this.label = label;
        this.kind = kind;
        this.insertText = label;
        this.sortText = label;
    }
    
    public LspCompletionItem(String label, int kind, String detail) {
        this.label = label;
        this.kind = kind;
        this.detail = detail;
        this.insertText = label;
        this.sortText = label;
    }
    
    @Override
    public String toString() {
        return label + " (" + kindToString(kind) + ")";
    }
    
    private static String kindToString(int kind) {
        return switch(kind) {
            case TEXT -> "Text";
            case METHOD -> "Method";
            case FUNCTION -> "Function";
            case VARIABLE -> "Variable";
            case CLASS -> "Class";
            case INTERFACE -> "Interface";
            case MODULE -> "Module";
            case KEYWORD -> "Keyword";
            case SNIPPET -> "Snippet";
            default -> "Unknown";
        };
    }
}
