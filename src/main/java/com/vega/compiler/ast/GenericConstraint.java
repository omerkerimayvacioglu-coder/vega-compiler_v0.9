package com.vega.compiler.ast;

import java.util.List;

/**
 * Represents a generic type constraint: T: Protocol1 & Protocol2
 */
public class GenericConstraint extends Node {
    public final String typeParam;
    public final List<String> bounds;  // Protocol names that T must implement
    
    public GenericConstraint(String typeParam, List<String> bounds, int line, int col) {
        this.typeParam = typeParam;
        this.bounds = bounds;
        this.line = line;
        this.col = col;
    }
}
