package com.vega.compiler.ast;

import java.util.List;

public class MatchExpr extends Expr {
    public final Expr subject;
    public final List<MatchArm> arms;
    public MatchExpr(Expr subject, List<MatchArm> arms, int line, int col) {
        this.subject = subject; this.arms = arms; this.line = line; this.col = col;
    }
}
