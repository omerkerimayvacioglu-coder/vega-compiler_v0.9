package com.vega.compiler.ast;

import java.util.List;

public class ExtensionDecl extends Stmt {
    public final String targetType;
    public final List<FuncDecl> methods;
    public ExtensionDecl(String targetType, List<FuncDecl> methods, int line, int col) {
        this.targetType = targetType; this.methods = methods; this.line = line; this.col = col;
    }
}
