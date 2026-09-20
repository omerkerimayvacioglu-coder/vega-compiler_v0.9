package com.vega.compiler.lsp.protocol;

/**
 * LSP Location - file URI and range
 */
public class LspLocation {
    public String uri;
    public LspRange range;
    
    public LspLocation() {}
    
    public LspLocation(String uri, LspRange range) {
        this.uri = uri;
        this.range = range;
    }
    
    public LspLocation(String uri, int startLine, int startChar, int endLine, int endChar) {
        this.uri = uri;
        this.range = new LspRange(startLine, startChar, endLine, endChar);
    }
    
    @Override
    public String toString() {
        return uri + "#" + range;
    }
}
