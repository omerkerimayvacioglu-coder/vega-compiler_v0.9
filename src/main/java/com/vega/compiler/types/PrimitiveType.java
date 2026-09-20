package com.vega.compiler.types;

public class PrimitiveType extends VegaType {
    public static final PrimitiveType INT = new PrimitiveType("int");
    public static final PrimitiveType FLOAT = new PrimitiveType("float");
    public static final PrimitiveType BOOL = new PrimitiveType("bool");
    public static final PrimitiveType CHAR = new PrimitiveType("char");
    public static final PrimitiveType VOID = new PrimitiveType("void");
    public static final PrimitiveType LONG = new PrimitiveType("long");

    private final String name;
    private PrimitiveType(String name) { this.name = name; }

    @Override public String getName() { return name; }
    @Override public boolean isPrimitive() { return true; }

    @Override public String toString() { return name; }
}
