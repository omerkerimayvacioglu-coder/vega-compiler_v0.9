package com.vega.compiler.ast;

import java.util.List;

public abstract class Expr extends Node {
    public com.vega.compiler.types.VegaType inferredType;
}
