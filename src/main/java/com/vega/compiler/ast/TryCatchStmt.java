package com.vega.compiler.ast;

import java.util.List;

public class TryCatchStmt extends Stmt {
    public final List<Stmt> tryBody;
    public final String errorVar;
    public final List<Stmt> catchBody;
    public TryCatchStmt(List<Stmt> tryBody, String errorVar, List<Stmt> catchBody, int line, int col) {
        this.tryBody = tryBody; this.errorVar = errorVar; this.catchBody = catchBody;
        this.line = line; this.col = col;
    }
}
