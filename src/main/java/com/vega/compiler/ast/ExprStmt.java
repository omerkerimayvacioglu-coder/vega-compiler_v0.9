package com.vega.compiler.ast;

import java.util.List;

public class ExprStmt extends Stmt {
    public final Expr expr;
    public ExprStmt(Expr expr, int line, int col) { this.expr = expr; this.line = line; this.col = col; }
}
