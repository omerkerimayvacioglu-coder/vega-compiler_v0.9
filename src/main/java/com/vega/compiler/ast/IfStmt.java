package com.vega.compiler.ast;

import java.util.List;

public class IfStmt extends Stmt {
    public final Expr condition;
    public final List<Stmt> thenBody;
    public final List<Stmt> elseBody;
    public IfStmt(Expr condition, List<Stmt> thenBody, List<Stmt> elseBody, int line, int col) {
        this.condition = condition; this.thenBody = thenBody; this.elseBody = elseBody;
        this.line = line; this.col = col;
    }
}
