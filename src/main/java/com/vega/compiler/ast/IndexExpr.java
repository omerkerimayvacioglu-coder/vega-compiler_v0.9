package com.vega.compiler.ast;

public class IndexExpr extends Expr {
    public final Expr target;
    public final Expr index;
    public IndexExpr(Expr target, Expr index, int line, int col) {
        this.target = target; this.index = index; this.line = line; this.col = col;
    }
}
