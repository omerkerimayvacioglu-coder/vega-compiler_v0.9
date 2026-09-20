package com.vega.compiler.ast;

import java.util.List;

public class BinaryExpr extends Expr {
    public final String op;
    public final Expr left;
    public final Expr right;
    public BinaryExpr(String op, Expr left, Expr right, int line, int col) {
        this.op = op; this.left = left; this.right = right; this.line = line; this.col = col;
    }
}
