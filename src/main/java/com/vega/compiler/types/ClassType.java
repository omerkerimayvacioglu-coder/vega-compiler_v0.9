package com.vega.compiler.types;

public class ClassType extends VegaType {
    public static final ClassType STRING = new ClassType("java/lang/String");
    public static final ClassType OBJECT = new ClassType("java/lang/Object");

    public final String jvmName;
    public ClassType(String jvmName) { this.jvmName = jvmName; }

    @Override public String getName() { return jvmName; }
    @Override public boolean isPrimitive() { return false; }

    @Override public String toString() { return jvmName; }
}
