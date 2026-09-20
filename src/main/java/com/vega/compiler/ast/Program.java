package com.vega.compiler.ast;

import java.util.List;

public class Program extends Node {
    public final List<Stmt> statements;
    public String packageName = "vega";
    public Program(List<Stmt> statements) { this.statements = statements; }
}
