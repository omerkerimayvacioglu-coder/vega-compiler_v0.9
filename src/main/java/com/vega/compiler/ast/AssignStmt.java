package com.vega.compiler.ast;

import java.util.List;

public class AssignStmt extends Stmt {
    public final Expr target;
    public final Expr value;
    public AssignStmt(Expr target, Expr value, int line, int col) {
        this.target = target; this.value = value; this.line = line; this.col = col;
    }
}
