package com.vega.compiler.types;

import com.vega.compiler.ast.*;
import java.util.*;

/**
 * Protocol compliance checker.
 * Validates that types implementing a protocol define all required methods.
 */
public class ProtocolChecker {
    private final Map<String, ProtocolDecl> protocols = new HashMap<>();
    private final Map<String, StructDecl> structs = new HashMap<>();
    private final Map<String, ClassDecl> classes = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    
    public void registerProtocol(ProtocolDecl protocol) {
        protocols.put(protocol.name, protocol);
    }
    
    public void registerStruct(StructDecl struct) {
        structs.put(struct.name, struct);
    }
    
    public void registerClass(ClassDecl klass) {
        classes.put(klass.name, klass);
    }
    
    /**
     * Validate that a struct implements all required protocol methods
     */
    public void validateStructProtocols(StructDecl struct) {
        for (String protocolName : struct.implementsList) {
            ProtocolDecl protocol = protocols.get(protocolName);
            if (protocol == null) {
                errors.add("Protocol '" + protocolName + "' not found");
                continue;
            }
            
            // Check that struct implements all required methods
            for (FuncDecl sig : protocol.methodSigs) {
                boolean found = false;
                for (FuncDecl method : struct.methods) {
                    if (method.name.equals(sig.name)) {
                        // Signature check: parameters must match
                        if (method.params.size() == sig.params.size()) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    errors.add("Struct '" + struct.name + "' does not implement required method '" 
                        + sig.name + "' from protocol '" + protocolName + "'");
                }
            }
        }
    }
    
    /**
     * Validate that a class implements all required protocol methods
     */
    public void validateClassProtocols(ClassDecl klass) {
        for (String protocolName : klass.implementsList) {
            ProtocolDecl protocol = protocols.get(protocolName);
            if (protocol == null) {
                errors.add("Protocol '" + protocolName + "' not found");
                continue;
            }
            
            // Check that class implements all required methods
            for (FuncDecl sig : protocol.methodSigs) {
                boolean found = false;
                for (FuncDecl method : klass.methods) {
                    if (method.name.equals(sig.name)) {
                        if (method.params.size() == sig.params.size()) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    errors.add("Class '" + klass.name + "' does not implement required method '" 
                        + sig.name + "' from protocol '" + protocolName + "'");
                }
            }
        }
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public boolean isValid() {
        return errors.isEmpty();
    }
}
