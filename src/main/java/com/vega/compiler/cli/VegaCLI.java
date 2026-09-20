package com.vega.compiler.cli;

import com.vega.compiler.Compiler;
import com.vega.compiler.lexer.Lexer;
import com.vega.compiler.lexer.Token;
import com.vega.compiler.parser.Parser;
import com.vega.compiler.ast.Program;
import com.vega.compiler.codegen.CodeGen;
import com.vega.compiler.stdlib.StdlibLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class VegaCLI {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Vega Compiler v0.1");
            System.err.println("Usage: vega <command> [options]");
            System.err.println("Commands:");
            System.err.println("  build <file.vg> [--out <dir>]   Compile Vega source to JVM .class files");
            System.err.println("  run <file.vg>                   Compile and run a Vega program");
            System.err.println("  tokens <file.vg>                Print tokens (debug)");
            System.err.println("  ast <file.vg>                   Print AST (debug)");
            System.err.println("  stdlib                          Show stdlib info");
            System.exit(1);
        }

        String command = args[0];
        try {
            switch (command) {
                case "build" -> cmdBuild(args);
                case "run" -> cmdRun(args);
                case "tokens" -> cmdTokens(args);
                case "ast" -> cmdAst(args);
                case "stdlib" -> cmdStdlib(args);
                default -> {
                    System.err.println("Unknown command: " + command);
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String getSourceFile(String[] args) {
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) return args[i];
        }
        System.err.println("No source file specified");
        System.exit(1);
        return null;
    }

    private static String getOption(String[] args, String name, String defaultValue) {
        for (int i = 1; i < args.length - 1; i++) {
            if (args[i].equals(name)) return args[i + 1];
        }
        return defaultValue;
    }

    private static String readSource(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    private static Program compileToAst(String source) throws IOException {
        // Prepend stdlib
        String fullSource = StdlibLoader.prependStdlib(source);
        
        Lexer lexer = new Lexer(fullSource);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    private static void cmdBuild(String[] args) throws Exception {
        String file = getSourceFile(args);
        String outDir = getOption(args, "--out", "out");
        String source = readSource(file);
        Program program = compileToAst(source);
        CodeGen codegen = new CodeGen();
        codegen.generate(program, outDir);
        System.out.println("Build complete. Output: " + outDir + "/");
    }

    private static void cmdRun(String[] args) throws Exception {
        String file = getSourceFile(args);
        String outDir = System.getProperty("java.io.tmpdir") + "/vega_build_" + System.currentTimeMillis();
        String source = readSource(file);
        Program program = compileToAst(source);
        CodeGen codegen = new CodeGen();
        codegen.generate(program, outDir);

        // Run the compiled program
        String pkgPath = program.packageName.replace('.', '/');
        String mainClass = program.packageName + ".Main";
        String classpath = outDir + "/vega:" + outDir + ":libs/asm-9.7.jar:libs/asm-tree-9.7.jar:libs/asm-util-9.7.jar";

        ProcessBuilder pb = new ProcessBuilder("java", "-cp", classpath, mainClass);
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();
        System.exit(exitCode);
    }

    private static void cmdTokens(String[] args) throws Exception {
        String file = getSourceFile(args);
        String source = readSource(file);
        // Load stdlib and prepend
        String fullSource = StdlibLoader.prependStdlib(source);
        
        Lexer lexer = new Lexer(fullSource);
        List<Token> tokens = lexer.tokenize();
        for (Token t : tokens) {
            System.out.println(t);
        }
    }

    private static void cmdAst(String[] args) throws Exception {
        String file = getSourceFile(args);
        String source = readSource(file);
        Program program = compileToAst(source);
        System.out.println("Package: " + program.packageName);
        System.out.println("Statements: " + program.statements.size());
        for (var s : program.statements) {
            System.out.println("  " + s.getClass().getSimpleName());
        }
    }
    
    private static void cmdStdlib(String[] args) throws Exception {
        System.out.println("Vega Standard Library Info");
        System.out.println("===========================");
        System.out.println("Path: " + StdlibLoader.getStdlibPath());
        System.out.println("\nModules:");
        String[] modules = {"io", "collections", "string", "math", "errors", "time"};
        for (String module : modules) {
            boolean exists = StdlibLoader.moduleExists(module);
            System.out.println("  - " + module + (exists ? " ✓" : " ✗"));
        }
    }
}
