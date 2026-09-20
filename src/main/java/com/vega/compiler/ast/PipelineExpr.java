package com.vega.compiler.ast;

import java.util.List;

public class PipelineExpr extends Expr {
    public final Expr left;
    public final Expr right;
    public PipelineExpr(Expr left, Expr right, int line, int col) {
        this.left = left; this.right = right; this.line = line; this.col = col;
    }
}
