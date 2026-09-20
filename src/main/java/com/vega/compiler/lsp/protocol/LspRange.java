package com.vega.compiler.lsp.protocol;

/**
 * LSP Range - start and end position
 */
public class LspRange {
    public LspPosition start;
    public LspPosition end;
    
    public LspRange() {}
    
    public LspRange(LspPosition start, LspPosition end) {
        this.start = start;
        this.end = end;
    }
    
    public LspRange(int startLine, int startChar, int endLine, int endChar) {
        this.start = new LspPosition(startLine, startChar);
        this.end = new LspPosition(endLine, endChar);
    }
    
    @Override
    public String toString() {
        return "[" + start + " - " + end + "]";
    }
}
