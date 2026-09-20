package com.vega.compiler.ast;

public class Annotation {
    public final String name;
    public final String value;  // Optional annotation value
    
    public Annotation(String name, String value) {
        this.name = name;
        this.value = value;
    }
    
    public Annotation(String name) {
        this(name, null);
    }
}
