package com.vega.compiler.lsp.protocol;

/**
 * LSP Diagnostic - error, warning, info in a document
 */
public class LspDiagnostic {
    public static final int ERROR = 1;
    public static final int WARNING = 2;
    public static final int INFORMATION = 3;
    public static final int HINT = 4;
    
    public LspRange range;
    public int severity;  // 1=Error, 2=Warning, 3=Info, 4=Hint
    public String code;
    public String source = "vega";
    public String message;
    
    public LspDiagnostic() {}
    
    public LspDiagnostic(LspRange range, int severity, String message) {
        this.range = range;
        this.severity = severity;
        this.message = message;
    }
    
    public LspDiagnostic(LspRange range, int severity, String code, String message) {
        this.range = range;
        this.severity = severity;
        this.code = code;
        this.message = message;
    }
    
    @Override
    public String toString() {
        String sevStr = switch(severity) {
            case ERROR -> "ERROR";
            case WARNING -> "WARNING";
            case INFORMATION -> "INFO";
            case HINT -> "HINT";
            default -> "UNKNOWN";
        };
        return sevStr + " @ " + range + ": " + message;
    }
}
