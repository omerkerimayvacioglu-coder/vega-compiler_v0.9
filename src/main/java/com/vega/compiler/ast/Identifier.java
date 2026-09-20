package com.vega.compiler.ast;

public class Identifier extends Expr {
    public final String name;
    public Identifier(String name, int line, int col) { this.name = name; this.line = line; this.col = col; }
}
