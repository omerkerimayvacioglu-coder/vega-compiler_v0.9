package com.vega.compiler.ast;

import java.util.List;

public class VegaTypeRef extends Node {
    public final String name;
    public final List<VegaTypeRef> genericArgs;
    public final boolean isOptional;
    public VegaTypeRef(String name, List<VegaTypeRef> genericArgs, boolean isOptional, int line, int col) {
        this.name = name; this.genericArgs = genericArgs; this.isOptional = isOptional;
        this.line = line; this.col = col;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        if (genericArgs != null && !genericArgs.isEmpty()) {
            sb.append("<");
            for (int i = 0; i < genericArgs.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(genericArgs.get(i));
            }
            sb.append(">");
        }
        if (isOptional) sb.append("?");
        return sb.toString();
    }
}
