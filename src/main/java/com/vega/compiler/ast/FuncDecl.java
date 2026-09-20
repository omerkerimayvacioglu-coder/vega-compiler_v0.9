package com.vega.compiler.ast;

import java.util.List;

public class FuncDecl extends Stmt {
    public final String name;
    public final List<Param> params;
    public final VegaTypeRef returnType;
    public final List<Stmt> body;
    public final boolean isAsync;
    public final boolean throwsError;
    public final String visibility;
    public final boolean isOverride;
    public final boolean isOperator;
    public final List<String> genericParams;

    public FuncDecl(String name, List<Param> params, VegaTypeRef returnType,
                    List<Stmt> body, boolean isAsync, boolean throwsError,
                    String visibility, boolean isOverride, boolean isOperator,
                    List<String> genericParams, int line, int col) {
        this.name = name; this.params = params; this.returnType = returnType;
        this.body = body; this.isAsync = isAsync; this.throwsError = throwsError;
        this.visibility = visibility; this.isOverride = isOverride; this.isOperator = isOperator;
        this.genericParams = genericParams; this.line = line; this.col = col;
    }

    public static class Param {
        public final String name;
        public final VegaTypeRef type;
        public Param(String name, VegaTypeRef type) { this.name = name; this.type = type; }
    }
}
