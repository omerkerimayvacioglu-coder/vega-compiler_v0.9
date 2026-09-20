package com.vega.compiler.ast;

import java.util.List;

public class TupleExpr extends Expr {
    public final List<String> names;
    public final List<Expr> elements;
    public TupleExpr(List<String> names, List<Expr> elements, int line, int col) {
        this.names = names; this.elements = elements; this.line = line; this.col = col;
    }
}
