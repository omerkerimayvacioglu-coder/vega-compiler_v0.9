package com.vega.compiler.ast;

import java.util.List;

public class MemberExpr extends Expr {
    public final Expr target;
    public final String member;
    public MemberExpr(Expr target, String member, int line, int col) {
        this.target = target; this.member = member; this.line = line; this.col = col;
    }
}
