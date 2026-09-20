package com.vega.compiler.ast;

import com.vega.compiler.lexer.Token;

public class EnumAccessExpr extends Expr {
    public final String enumName;
    public final String variant;
    public EnumAccessExpr(String enumName, String variant, int line, int col) {
        this.enumName = enumName; this.variant = variant; this.line = line; this.col = col;
    }
}
