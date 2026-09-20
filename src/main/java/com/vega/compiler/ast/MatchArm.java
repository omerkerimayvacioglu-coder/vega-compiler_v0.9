package com.vega.compiler.ast;

import java.util.List;

public class MatchArm {
    public final Expr pattern;
    public final List<Expr> body;
    public MatchArm(Expr pattern, List<Expr> body) {
        this.pattern = pattern;
        this.body = body;
    }
}
