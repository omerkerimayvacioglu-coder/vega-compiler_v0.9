package com.vega.compiler.ast;

public class NullCoalescingExpr extends Expr {
    public final Expr left;
    public final Expr right;
    public NullCoalescingExpr(Expr left, Expr right, int line, int col) {
        this.left = left; this.right = right; this.line = line; this.col = col;
    }
}
