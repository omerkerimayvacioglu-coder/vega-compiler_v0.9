package com.vega.compiler.types;

import com.vega.compiler.ast.VegaTypeRef;
import java.util.List;

public abstract class VegaType {
    public abstract String getName();
    public abstract boolean isPrimitive();
    public boolean isOptional() { return false; }

    public static VegaType fromRef(VegaTypeRef ref) {
        if (ref.isOptional) {
            return new OptionalType(fromRef(new VegaTypeRef(ref.name, ref.genericArgs, false, ref.line, ref.col)));
        }
        return switch (ref.name) {
            case "Int" -> PrimitiveType.INT;
            case "Long" -> PrimitiveType.LONG;
            case "Float" -> PrimitiveType.FLOAT;
            case "Bool" -> PrimitiveType.BOOL;
            case "String" -> ClassType.STRING;
            case "Char" -> PrimitiveType.CHAR;
            case "Void" -> PrimitiveType.VOID;
            case "Any" -> ClassType.OBJECT;
            default -> {
                if (ref.genericArgs != null && ref.genericArgs.size() == 1 && ref.name.equals("Array")) {
                    yield new ArrayType(fromRef(ref.genericArgs.get(0)));
                }
                yield new ClassType(ref.name);
            }
        };
    }

    public static VegaType array(VegaType element) { return new ArrayType(element); }
    public static VegaType optional(VegaType inner) { return new OptionalType(inner); }
}
