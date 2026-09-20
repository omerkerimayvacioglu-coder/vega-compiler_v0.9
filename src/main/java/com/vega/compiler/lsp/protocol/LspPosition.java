package com.vega.compiler.lsp.protocol;

/**
 * LSP Position - line and character in a document
 * Line and character are 0-indexed
 */
public class LspPosition {
    public int line;
    public int character;
    
    public LspPosition() {}
    
    public LspPosition(int line, int character) {
        this.line = line;
        this.character = character;
    }
    
    @Override
    public String toString() {
        return line + ":" + character;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LspPosition that = (LspPosition) o;
        return line == that.line && character == that.character;
    }
    
    @Override
    public int hashCode() {
        return 31 * line + character;
    }
}
