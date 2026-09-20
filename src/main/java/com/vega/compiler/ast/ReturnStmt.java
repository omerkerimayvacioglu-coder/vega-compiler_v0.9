package com.vega.compiler.ast;

import java.util.List;

public class ReturnStmt extends Stmt {
    public final Expr value;
    public ReturnStmt(Expr value, int line, int col) { this.value = value; this.line = line; this.col = col; }
}
