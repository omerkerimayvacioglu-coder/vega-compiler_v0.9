package com.vega.compiler.ast;

import java.util.List;

public class LambdaExpr extends Expr {
    public final List<String> params;
    public final List<Expr> body;
    public LambdaExpr(List<String> params, List<Expr> body, int line, int col) {
        this.params = params; this.body = body; this.line = line; this.col = col;
    }
}
