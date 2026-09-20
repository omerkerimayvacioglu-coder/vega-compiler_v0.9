package com.vega.compiler.types;

public class ArrayType extends VegaType {
    public final VegaType element;
    public ArrayType(VegaType element) { this.element = element; }

    @Override public String getName() { return "Array<" + element + ">"; }
    @Override public boolean isPrimitive() { return false; }

    @Override public String toString() { return "Array<" + element + ">"; }
}
