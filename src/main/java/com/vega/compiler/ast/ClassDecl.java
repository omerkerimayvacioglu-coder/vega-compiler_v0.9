package com.vega.compiler.ast;

import java.util.List;

public class ClassDecl extends Stmt {
    public final String name;
    public final String superClass;
    public final List<VarDecl> fields;
    public final List<FuncDecl> methods;
    public final FuncDecl init;
    public final List<FuncDecl> constructors;
    public final List<String> implementsList;
    public final List<String> genericParams;
    public final String visibility;
    public ClassDecl(String name, String superClass, List<VarDecl> fields,
                    List<FuncDecl> methods, FuncDecl init,
                    List<String> implementsList, List<String> genericParams,
                    String visibility, int line, int col) {
        this.name = name; this.superClass = superClass; this.fields = fields;
        this.methods = methods; this.init = init; this.implementsList = implementsList;
        this.constructors = init == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(java.util.List.of(init));
        this.genericParams = genericParams; this.visibility = visibility;
        this.line = line; this.col = col;
    }

    public ClassDecl(String name, String superClass, List<VarDecl> fields,
                     List<FuncDecl> methods, List<FuncDecl> constructors,
                     List<String> implementsList, List<String> genericParams,
                     String visibility, int line, int col) {
        this.name = name; this.superClass = superClass; this.fields = fields;
        this.methods = methods; this.constructors = constructors;
        this.init = constructors.isEmpty() ? null : constructors.get(0);
        this.implementsList = implementsList; this.genericParams = genericParams;
        this.visibility = visibility; this.line = line; this.col = col;
    }
}
