package com.vega.compiler.ast;

public class BoolLiteral extends Expr {
    public final boolean value;
    public BoolLiteral(boolean value, int line, int col) { this.value = value; this.line = line; this.col = col; }
}
