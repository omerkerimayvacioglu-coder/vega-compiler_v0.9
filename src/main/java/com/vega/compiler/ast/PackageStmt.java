package com.vega.compiler.ast;

import java.util.List;

public class PackageStmt extends Stmt {
    public final String name;
    public PackageStmt(String name, int line, int col) { this.name = name; this.line = line; this.col = col; }
}
