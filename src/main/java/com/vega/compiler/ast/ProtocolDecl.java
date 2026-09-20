package com.vega.compiler.ast;

import java.util.List;

public class ProtocolDecl extends Stmt {
    public final String name;
    public final List<FuncDecl> methodSigs;
    public final List<FuncDecl> defaultImpls;
    public final String visibility;
    public ProtocolDecl(String name, List<FuncDecl> methodSigs, List<FuncDecl> defaultImpls,
                       String visibility, int line, int col) {
        this.name = name; this.methodSigs = methodSigs; this.defaultImpls = defaultImpls;
        this.visibility = visibility; this.line = line; this.col = col;
    }
}
