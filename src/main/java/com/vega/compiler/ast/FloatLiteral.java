package com.vega.compiler.ast;

public class FloatLiteral extends Expr {
    public final double value;
    public FloatLiteral(double value, int line, int col) { this.value = value; this.line = line; this.col = col; }
}
