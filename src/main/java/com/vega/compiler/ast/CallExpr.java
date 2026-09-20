package com.vega.compiler.ast;

import java.util.List;

public class CallExpr extends Expr {
    public final Expr callee;
    public final List<Expr> args;
    public final List<String> argNames;
    public CallExpr(Expr callee, List<Expr> args, List<String> argNames, int line, int col) {
        this.callee = callee; this.args = args; this.argNames = argNames;
        this.line = line; this.col = col;
    }
}
