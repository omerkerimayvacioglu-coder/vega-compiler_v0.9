package com.vega.compiler.ast;

import java.util.List;

public class IfExpr extends Expr {
    public final Expr condition;
    public final List<Expr> thenBody;
    public final List<Expr> elseBody;
    public IfExpr(Expr condition, List<Expr> thenBody, List<Expr> elseBody, int line, int col) {
        this.condition = condition; this.thenBody = thenBody; this.elseBody = elseBody;
        this.line = line; this.col = col;
    }
}
