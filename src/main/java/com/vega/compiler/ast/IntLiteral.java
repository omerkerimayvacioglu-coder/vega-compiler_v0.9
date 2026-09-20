package com.vega.compiler.ast;

public class IntLiteral extends Expr {
    public final int value;
    public IntLiteral(int value, int line, int col) { this.value = value; this.line = line; this.col = col; }
}
