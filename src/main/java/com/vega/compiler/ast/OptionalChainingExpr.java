package com.vega.compiler.ast;

public class OptionalChainingExpr extends Expr {
    public final Expr target;
    public final String member;
    public OptionalChainingExpr(Expr target, String member, int line, int col) {
        this.target = target; this.member = member; this.line = line; this.col = col;
    }
}
