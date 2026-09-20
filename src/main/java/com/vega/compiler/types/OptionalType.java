package com.vega.compiler.types;

public class OptionalType extends VegaType {
    public final VegaType inner;
    public OptionalType(VegaType inner) { this.inner = inner; }

    @Override public String getName() { return inner.getName() + "?"; }
    @Override public boolean isPrimitive() { return false; }
    @Override public boolean isOptional() { return true; }

    @Override public String toString() { return inner + "?"; }
}
