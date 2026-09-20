package com.vega.compiler.ast;

public class CharLiteral extends Expr {
    public final char value;
    public CharLiteral(char value, int line, int col) { this.value = value; this.line = line; this.col = col; }
}
