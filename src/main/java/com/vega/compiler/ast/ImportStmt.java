package com.vega.compiler.ast;

import java.util.List;

public class ImportStmt extends Stmt {
    public final String module;
    public final String symbol;
    public final String alias;
    public ImportStmt(String module, String symbol, String alias, int line, int col) {
        this.module = module; this.symbol = symbol; this.alias = alias;
        this.line = line; this.col = col;
    }
}
