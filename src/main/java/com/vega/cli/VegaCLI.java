package com.vega.cli;

import com.vega.compiler.Compiler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class VegaCLI {
    private static final String VERSION = "1.0.0";
    private static final String USAGE = """
        Vega 1.0 Compiler CLI
        
        Usage:
          vegac compile <file.vg>           Compile Vega file to Java bytecode
          vegac run <file.vg>               Compile and execute Vega file
          vega <file.vg>                    Execute Vega file directly
          vegac --help                      Show this help message
          vegac --version                   Show version
        
        Examples:
          vegac compile hello.vg
          vegac run hello.vg
          vega hello.vg
        """;

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                showHelp();
                System.exit(0);
            }

            String command = args[0];

            switch (command) {
                case "compile" -> handleCompile(args);
                case "run" -> handleRun(args);
                case "execute" -> handleExecute(args);
                case "--help", "-h", "help" -> showHelp();
                case "--version", "-v", "version" -> showVersion();
                default -> {
                    // Try to execute as file if it's a .vg file
                    if (command.endsWith(".vg")) {
                        handleExecute(args);
                    } else {
                        System.err.println("Unknown command: " + command);
                        showHelp();
                        System.exit(1);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void handleCompile(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Error: compile requires a file argument");
            System.err.println("Usage: vegac compile <file.vg>");
            System.exit(1);
        }

        String filePath = args[1];
        File file = new File(filePath);

        if (!file.exists()) {
            System.err.println("Error: File not found: " + filePath);
            System.exit(1);
        }

        if (!filePath.endsWith(".vg")) {
            System.err.println("Error: File must have .vg extension");
            System.exit(1);
        }

        System.out.println("Compiling: " + filePath);

        try {
            String outputDir = "out";
            new File(outputDir).mkdirs();

            Compiler compiler = new Compiler();
            compiler.compileFile(filePath, outputDir);

            System.out.println("✓ Compilation successful");
            System.out.println("Output: " + outputDir);
        } catch (Exception e) {
            System.err.println("✗ Compilation failed: " + e.getMessage());
            throw e;
        }
    }

    private static void handleRun(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Error: run requires a file argument");
            System.err.println("Usage: vegac run <file.vg>");
            System.exit(1);
        }

        String filePath = args[1];
        File file = new File(filePath);

        if (!file.exists()) {
            System.err.println("Error: File not found: " + filePath);
            System.exit(1);
        }

        if (!filePath.endsWith(".vg")) {
            System.err.println("Error: File must have .vg extension");
            System.exit(1);
        }

        System.out.println("Compiling and running: " + filePath);

        try {
            String outputDir = "out";
            new File(outputDir).mkdirs();

            // Compile
            Compiler compiler = new Compiler();
            compiler.compileFile(filePath, outputDir);
            System.out.println("✓ Compilation successful");

            // Execute
            executeCompiledCode(outputDir);
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            throw e;
        }
    }

    private static void handleExecute(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Error: execute requires a file argument");
            System.err.println("Usage: vega <file.vg>");
            System.exit(1);
        }

        String filePath = args[0];
        File file = new File(filePath);

        if (!file.exists()) {
            System.err.println("Error: File not found: " + filePath);
            System.exit(1);
        }

        if (!filePath.endsWith(".vg")) {
            System.err.println("Error: File must have .vg extension");
            System.exit(1);
        }

        try {
            String outputDir = "out";
            new File(outputDir).mkdirs();

            // Compile
            Compiler compiler = new Compiler();
            compiler.compileFile(filePath, outputDir);

            // Execute
            executeCompiledCode(outputDir);
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            throw e;
        }
    }

    private static void executeCompiledCode(String outputDir) throws Exception {
        System.out.println("Running...");
        System.out.println();

        try {
            // Load and execute Main class from compiled output
            File outDir = new File(outputDir);
            if (!outDir.exists()) {
                throw new Exception("Output directory not found: " + outputDir);
            }

            // Find and execute main method via reflection
            ClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[]{outDir.toURI().toURL()},
                ClassLoader.getSystemClassLoader()
            );

            Class<?> mainClass = loader.loadClass("Main");
            java.lang.reflect.Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) new String[]{});
        } catch (ClassNotFoundException e) {
            throw new Exception("Compiled Main class not found. Make sure compilation succeeded.");
        } catch (NoSuchMethodException e) {
            throw new Exception("Main method not found in compiled class");
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap the actual exception
            throw new Exception(e.getCause());
        }
    }

    private static void showHelp() {
        System.out.println(USAGE);
    }

    private static void showVersion() {
        System.out.println("Vega " + VERSION);
    }
}
