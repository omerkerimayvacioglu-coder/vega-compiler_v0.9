package com.vega.compiler.ast;

import java.util.List;

public class ErrorDecl extends Stmt {
    public final String name;
    public final List<VarDecl> fields;
    public ErrorDecl(String name, List<VarDecl> fields, int line, int col) {
        this.name = name; this.fields = fields; this.line = line; this.col = col;
    }
}
