package com.vega.compiler.ast;

import java.util.List;

/**
 * Optional binding statement: if let varName { ... }
 * Binds an optional value to a variable if it's not null
 */
public class IfLetStmt extends Stmt {
    public final String varName;
    public final Expr optionalExpr;
    public final List<Stmt> thenBody;
    public final List<Stmt> elseBody;
    
    public IfLetStmt(String varName, Expr optionalExpr, List<Stmt> thenBody, 
                     List<Stmt> elseBody, int line, int col) {
        this.varName = varName;
        this.optionalExpr = optionalExpr;
        this.thenBody = thenBody;
        this.elseBody = elseBody;
        this.line = line;
        this.col = col;
    }
}
