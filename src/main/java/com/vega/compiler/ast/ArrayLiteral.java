package com.vega.compiler.ast;

import java.util.List;

public class ArrayLiteral extends Expr {
    public final List<Expr> elements;
    public ArrayLiteral(List<Expr> elements, int line, int col) {
        this.elements = elements; this.line = line; this.col = col;
    }
}
