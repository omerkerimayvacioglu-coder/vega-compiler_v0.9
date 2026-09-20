package com.vega.compiler.ast;

import java.util.List;

public class WhileStmt extends Stmt {
    public final Expr condition;
    public final List<Stmt> body;
    public WhileStmt(Expr condition, List<Stmt> body, int line, int col) {
        this.condition = condition; this.body = body; this.line = line; this.col = col;
    }
}
