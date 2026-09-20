package com.vega.compiler.ast;

import java.util.List;

public class EnumDecl extends Stmt {
    public final String name;
    public final List<String> variants;
    public final String visibility;
    public EnumDecl(String name, List<String> variants, String visibility, int line, int col) {
        this.name = name; this.variants = variants; this.visibility = visibility;
        this.line = line; this.col = col;
    }
}
