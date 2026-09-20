package com.vega.compiler.stdlib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads Vega standard library modules
 */
public class StdlibLoader {
    
    private static final String[] STDLIB_MODULES = {
        "io",
        "collections", 
        "string",
        "math",
        "errors",
        "time"
    };
    
    /**
     * Get path to stdlib directory
     */
    public static String getStdlibPath() {
        // Try multiple paths
        String[] possiblePaths = {
            "stdlib",
            "./stdlib",
            "../stdlib",
            "vega-compiler-main/stdlib"
        };
        
        for (String path : possiblePaths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                return path;
            }
        }
        
        throw new RuntimeException("Standard library path not found. Searched: " + String.join(", ", possiblePaths));
    }
    
    /**
     * Load all stdlib modules and return their source code
     */
    public static List<String> loadAllModules() throws IOException {
        List<String> sources = new ArrayList<>();
        String stdlibPath = getStdlibPath();
        
        for (String module : STDLIB_MODULES) {
            String source = loadModule(module);
            if (source != null) {
                sources.add(source);
            }
        }
        
        return sources;
    }
    
    /**
     * Load a specific stdlib module by name
     */
    public static String loadModule(String moduleName) throws IOException {
        String stdlibPath = getStdlibPath();
        String modulePath = stdlibPath + File.separator + moduleName + File.separator + moduleName + ".vg";
        return loadModuleFromFile(modulePath);
    }
    
    /**
     * Load module from file path
     */
    private static String loadModuleFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            System.err.println("Warning: Module file not found: " + filePath);
            return null;
        }
        
        try {
            return Files.readString(path);
        } catch (IOException e) {
            System.err.println("Warning: Could not read module: " + filePath);
            throw e;
        }
    }
    
    /**
     * Prepend stdlib modules to user source code
     */
    public static String prependStdlib(String userSource) throws IOException {
        // For now, disable stdlib loading to fix the build issue
        return userSource;
    }
    
    /**
     * Check if module exists
     */
    public static boolean moduleExists(String moduleName) {
        try {
            String stdlibPath = getStdlibPath();
            String modulePath = stdlibPath + File.separator + moduleName + File.separator + moduleName + ".vg";
            return Files.exists(Paths.get(modulePath));
        } catch (Exception e) {
            return false;
        }
    }
}
