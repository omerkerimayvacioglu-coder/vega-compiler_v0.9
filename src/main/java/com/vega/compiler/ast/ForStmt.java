package com.vega.compiler.ast;

import java.util.List;

public class ForStmt extends Stmt {
    public final String varName;
    public final Expr iterable;
    public final List<Stmt> body;
    public ForStmt(String varName, Expr iterable, List<Stmt> body, int line, int col) {
        this.varName = varName; this.iterable = iterable; this.body = body;
        this.line = line; this.col = col;
    }
}
