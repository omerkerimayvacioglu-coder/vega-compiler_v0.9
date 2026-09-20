package com.vega.compiler.ast;

import com.vega.compiler.types.VegaType;

public class VarDecl extends Stmt {
    public final boolean mutable;
    public final String name;
    public final VegaType declaredType;
    public final Expr initializer;
    public final String visibility;
    public VarDecl(boolean mutable, String name, VegaType declaredType, Expr initializer, int line, int col) {
        this(mutable, name, declaredType, initializer, "internal", line, col);
    }
    public VarDecl(boolean mutable, String name, VegaType declaredType, Expr initializer, String visibility, int line, int col) {
        this.mutable = mutable; this.name = name; this.declaredType = declaredType;
        this.initializer = initializer; this.visibility = visibility; this.line = line; this.col = col;
    }
}
