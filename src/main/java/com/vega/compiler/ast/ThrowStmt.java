package com.vega.compiler.ast;

public class ThrowStmt extends Stmt {
    public final Expr error;
    public ThrowStmt(Expr error, int line, int col) { this.error = error; this.line = line; this.col = col; }
}
