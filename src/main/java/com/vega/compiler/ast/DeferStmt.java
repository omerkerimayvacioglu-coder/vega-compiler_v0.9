package com.vega.compiler.ast;

import java.util.List;

public class DeferStmt extends Stmt {
    public final List<Stmt> body;
    public DeferStmt(List<Stmt> body, int line, int col) { this.body = body; this.line = line; this.col = col; }
}
