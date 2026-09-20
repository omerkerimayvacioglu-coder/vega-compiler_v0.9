package com.vega.compiler.ast;

public class AwaitExpr extends Expr {
    public final Expr expr;
    
    public AwaitExpr(Expr expr, int line, int col) {
        this.expr = expr;
        this.line = line;
        this.col = col;
    }
}
