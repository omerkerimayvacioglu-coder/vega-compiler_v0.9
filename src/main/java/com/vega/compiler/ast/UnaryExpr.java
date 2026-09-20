package com.vega.compiler.ast;

public class UnaryExpr extends Expr {
    public final String op;
    public final Expr expr;
    public UnaryExpr(String op, Expr expr, int line, int col) {
        this.op = op; this.expr = expr; this.line = line; this.col = col;
    }
}
