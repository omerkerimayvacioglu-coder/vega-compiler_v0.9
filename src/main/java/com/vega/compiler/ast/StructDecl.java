package com.vega.compiler.ast;

import java.util.List;

public class StructDecl extends Stmt {
    public final String name;
    public final List<VarDecl> fields;
    public final List<FuncDecl> methods;
    public final List<String> implementsList;
    public final List<String> genericParams;
    public final String visibility;
    public StructDecl(String name, List<VarDecl> fields, List<FuncDecl> methods,
                     List<String> implementsList, List<String> genericParams,
                     String visibility, int line, int col) {
        this.name = name; this.fields = fields; this.methods = methods;
        this.implementsList = implementsList; this.genericParams = genericParams;
        this.visibility = visibility; this.line = line; this.col = col;
    }
}
