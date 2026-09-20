package com.vega.compiler.ast;

import java.util.List;

public class StringLiteral extends Expr {
    public final String value;
    public final boolean isInterpolated;
    public final List<Expr> parts;
    public StringLiteral(String value, int line, int col) {
        this.value = value; this.isInterpolated = false; this.parts = null;
        this.line = line; this.col = col;
    }
    public StringLiteral(List<Expr> parts, int line, int col) {
        this.value = null; this.isInterpolated = true; this.parts = parts;
        this.line = line; this.col = col;
    }
}
