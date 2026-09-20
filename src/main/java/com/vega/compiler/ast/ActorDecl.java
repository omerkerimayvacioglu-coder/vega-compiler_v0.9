package com.vega.compiler.ast;

import java.util.List;

public class ActorDecl extends Stmt {
    public final String name;
    public final List<VarDecl> fields;
    public final List<FuncDecl> methods;
    public final String visibility;

    public ActorDecl(
            String name,
            List<VarDecl> fields,
            List<FuncDecl> methods,
            String visibility,
            int line,
            int col
    ) {
        this.name = name;
        this.fields = fields;
        this.methods = methods;
        this.visibility = visibility;
        this.line = line;
        this.col = col;
    }
}
