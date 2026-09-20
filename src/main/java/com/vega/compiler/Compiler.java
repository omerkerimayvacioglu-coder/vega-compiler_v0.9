package com.vega.compiler;

import com.vega.compiler.ast.Program;
import com.vega.compiler.codegen.CodeGen;
import com.vega.compiler.lexer.Lexer;
import com.vega.compiler.parser.Parser;
import com.vega.compiler.stdlib.StdlibLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Compiler {
    
    private boolean includeStdlib = true;
    
    public Compiler() {
        this.includeStdlib = true;
    }
    
    public Compiler(boolean includeStdlib) {
        this.includeStdlib = includeStdlib;
    }
    
    public void compileFile(String filePath, String outputDir) throws Exception {
        // Read source file
        String source;
        try {
            source = new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            throw new Exception("Cannot read file: " + filePath, e);
        }

        // Compile source code
        compile(source, outputDir);
    }

    public void compile(String source, String outputDir) throws Exception {
        try {
            // Prepend stdlib if enabled
            String fullSource = source;
            if (includeStdlib) {
                try {
                    fullSource = StdlibLoader.prependStdlib(source);
                } catch (IOException e) {
                    System.err.println("Warning: Could not load stdlib: " + e.getMessage());
                    // Continue with user source only
                }
            }
            
            // Lexical analysis
            Lexer lexer = new Lexer(fullSource);
            var tokens = lexer.tokenize();

            // Parsing
            Parser parser = new Parser(tokens);
            Program program = parser.parse();

            // Code generation
            CodeGen codegen = new CodeGen();
            codegen.generate(program, outputDir);
        } catch (Exception e) {
            throw new Exception("Compilation error: " + e.getMessage(), e);
        }
    }
}
