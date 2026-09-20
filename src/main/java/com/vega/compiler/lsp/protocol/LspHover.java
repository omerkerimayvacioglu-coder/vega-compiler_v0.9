package com.vega.compiler.lsp.protocol;

/**
 * LSP Hover information
 */
public class LspHover {
    public String contents;
    public LspRange range;
    
    public LspHover() {}
    
    public LspHover(String contents) {
        this.contents = contents;
    }
    
    public LspHover(String contents, LspRange range) {
        this.contents = contents;
        this.range = range;
    }
    
    @Override
    public String toString() {
        return "Hover{" + contents + "}";
    }
}
