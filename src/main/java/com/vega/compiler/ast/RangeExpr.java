package com.vega.compiler.ast;

public class RangeExpr extends Expr {
    public final Expr start;
    public final Expr end;
    public final boolean exclusive;
    public RangeExpr(Expr start, Expr end, boolean exclusive, int line, int col) {
        this.start = start; this.end = end; this.exclusive = exclusive;
        this.line = line; this.col = col;
    }
}
