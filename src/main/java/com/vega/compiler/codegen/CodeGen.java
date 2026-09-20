package com.vega.compiler.codegen;

import com.vega.compiler.ast.*;
import com.vega.compiler.types.*;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class CodeGen {
    private String className;
    private String packageName;
    private String jvmClassName;
    private String currentOwner;
    private final Map<String, VegaType> globalVars = new HashMap<>();
    private final Map<String, FuncDecl> functions = new HashMap<>();
    private final Map<String, StructDecl> structs = new HashMap<>();
    private final Map<String, ClassDecl> classes = new HashMap<>();
    private final Map<String, EnumDecl> enums = new HashMap<>();
    private final Map<String, List<FuncDecl>> extensions = new HashMap<>();  // targetType -> methods
    private final Map<String, Map<String, FuncDecl>> operatorMethods = new HashMap<>();  // typeName -> op -> method

    // Per-method state
    private MethodVisitor mv;
    private int localCounter;
    private final Map<String, Integer> locals = new HashMap<>();
    private final Map<String, VegaType> localTypes = new HashMap<>();
    private final Set<String> genericTypes = new HashSet<>();
    private final List<DeferStmt> defers = new ArrayList<>();

    public void generate(Program program, String outputDir) throws Exception {
        packageName = program.packageName;
        String pkgPath = packageName.replace('.', '/');

        // First pass: collect declarations
        for (Stmt s : program.statements) {
            if (s instanceof FuncDecl fd) functions.put(fd.name, fd);
            else if (s instanceof StructDecl sd) structs.put(sd.name, sd);
            else if (s instanceof ClassDecl cd) classes.put(cd.name, cd);
            else if (s instanceof EnumDecl ed) enums.put(ed.name, ed);
            else if (s instanceof ExtensionDecl ext) {
                extensions.computeIfAbsent(ext.targetType, k -> new ArrayList<>()).addAll(ext.methods);
            }
            else if (s instanceof VarDecl vd) globalVars.put(vd.name,
                    vd.declaredType != null ? vd.declaredType : inferType(vd.initializer));
        }

        // Generate error classes
        for (Stmt s : program.statements) {
            if (s instanceof ErrorDecl ed) {
                generateError(ed, pkgPath, outputDir);
            }
        }

        // Generate struct classes
        for (StructDecl sd : structs.values()) {
            generateStruct(sd, pkgPath, outputDir);
        }
        // Generate class classes
        for (ClassDecl cd : classes.values()) {
            generateClass(cd, pkgPath, outputDir);
        }
        // Generate enum classes
        for (EnumDecl ed : enums.values()) {
            generateEnum(ed, pkgPath, outputDir);
        }

        // Generate main class with functions
        className = "Main";
        jvmClassName = pkgPath + "/Main";
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, jvmClassName, null, "java/lang/Object", null);
        for (Map.Entry<String, VegaType> global : globalVars.entrySet()) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, global.getKey(),
                    typeDesc(global.getValue()), null, null).visitEnd();
        }

        // Generate all top-level functions as static methods
        for (FuncDecl fd : functions.values()) {
            generateFunction(cw, fd, true);
        }
        
        // Generate extension methods as static helper methods
        // e.g., extension String { fn isBlank() } becomes String_isBlank(String self)
        for (Map.Entry<String, List<FuncDecl>> ext : extensions.entrySet()) {
            for (FuncDecl method : ext.getValue()) {
                generateExtensionMethod(cw, method, ext.getKey());
            }
        }

        // Generate main method
        generateMainMethod(cw, program);

        cw.visitEnd();
        writeClass(outputDir, jvmClassName, cw.toByteArray());
    }

    private void generateStruct(StructDecl sd, String pkgPath, String outputDir) throws Exception {
        String structJvmName = pkgPath + "/" + sd.name;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, structJvmName, null, "java/lang/Object", null);

        // Fields
        for (VarDecl f : sd.fields) {
            String desc = typeDesc(f.declaredType != null ? f.declaredType : inferType(f.initializer));
            int acc = getAccessFlags(f.visibility);
            cw.visitField(acc, f.name, desc, null, null).visitEnd();
        }

        // Constructor
        String desc = "(";
        for (VarDecl f : sd.fields) {
            desc += typeDesc(f.declaredType != null ? f.declaredType : PrimitiveType.INT);
        }
        desc += ")V";
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        int idx = 1;
        for (VarDecl f : sd.fields) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            VegaType ft = f.declaredType != null ? f.declaredType : PrimitiveType.INT;
            loadLocal(mv, ft, idx);
            mv.visitFieldInsn(Opcodes.PUTFIELD, structJvmName, f.name, typeDesc(ft));
            idx += typeSize(ft);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // Collect operator methods for this struct
        Map<String, FuncDecl> ops = new HashMap<>();
        for (FuncDecl md : sd.methods) {
            if (md.isOperator) {
                ops.put(md.name, md);
            }
        }
        if (!ops.isEmpty()) {
            operatorMethods.put(sd.name, ops);
        }

        // Methods
        for (FuncDecl md : sd.methods) {
            generateMethodInClass(cw, md, structJvmName, false);
        }

        cw.visitEnd();
        writeClass(outputDir, structJvmName, cw.toByteArray());
    }

    private void generateError(ErrorDecl ed, String pkgPath, String outputDir) throws Exception {
        String errorJvmName = pkgPath + "/" + ed.name;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        // Errors extend java.lang.Exception
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, errorJvmName, null, "java/lang/Exception", null);

        // Fields (same as structs)
        for (VarDecl f : ed.fields) {
            String desc = typeDesc(f.declaredType != null ? f.declaredType : PrimitiveType.INT);
            cw.visitField(Opcodes.ACC_PUBLIC, f.name, desc, null, null).visitEnd();
        }

        // Constructor: (String message, fields...) -> void
        StringBuilder constructorDesc = new StringBuilder("(");
        for (VarDecl f : ed.fields) {
            constructorDesc.append(typeDesc(f.declaredType != null ? f.declaredType : PrimitiveType.INT));
        }
        constructorDesc.append(")V");
        
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDesc.toString(), null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Exception", "<init>", "()V", false);
        
        int idx = 1;
        for (VarDecl f : ed.fields) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            VegaType ft = f.declaredType != null ? f.declaredType : PrimitiveType.INT;
            loadLocal(mv, ft, idx);
            mv.visitFieldInsn(Opcodes.PUTFIELD, errorJvmName, f.name, typeDesc(ft));
            idx += typeSize(ft);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        writeClass(outputDir, errorJvmName, cw.toByteArray());
    }

    private void generateEnum(EnumDecl ed, String pkgPath, String outputDir) throws Exception {
        String enumJvmName = pkgPath + "/" + ed.name;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        // Enums extend java.lang.Object (simplified - could extend java.lang.Enum)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, enumJvmName, null, "java/lang/Object", null);

        // Generate a static field for each variant
        for (String variant : ed.variants) {
            cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, variant, "L" + enumJvmName + ";", null, null).visitEnd();
        }

        // Generate static initializer to create enum instances
        MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        
        for (String variant : ed.variants) {
            clinit.visitTypeInsn(Opcodes.NEW, enumJvmName);
            clinit.visitInsn(Opcodes.DUP);
            clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, enumJvmName, "<init>", "()V", false);
            clinit.visitFieldInsn(Opcodes.PUTSTATIC, enumJvmName, variant, "L" + enumJvmName + ";");
        }
        
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        // Generate default constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        writeClass(outputDir, enumJvmName, cw.toByteArray());
    }

    private void generateClass(ClassDecl cd, String pkgPath, String outputDir) throws Exception {
        String classJvmName = pkgPath + "/" + cd.name;
        String superName = cd.superClass != null ? pkgPath + "/" + cd.superClass : "java/lang/Object";
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, classJvmName, null, superName, null);

        // Fields
        for (VarDecl f : cd.fields) {
            String desc = typeDesc(f.declaredType != null ? f.declaredType : PrimitiveType.INT);
            int acc = getAccessFlags(f.visibility);
            cw.visitField(acc, f.name, desc, null, null).visitEnd();
        }

        // Constructors (JVM overloads are selected by their parameter descriptors).
        if (!cd.constructors.isEmpty()) {
            for (FuncDecl constructor : cd.constructors) {
            StringBuilder d = new StringBuilder("(");
            for (FuncDecl.Param p : constructor.params) {
                d.append(typeRefDesc(p.type));
            }
            d.append(")V");
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", d.toString(), null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
            // init body
            this.mv = mv;
            localCounter = constructor.params.size() + 1;
            locals.clear();
            localTypes.clear();
            locals.put("self", 0);
            localTypes.put("self", new ClassType(classJvmName));
            int idx = 1;
            for (FuncDecl.Param p : constructor.params) {
                locals.put(p.name, idx);
                localTypes.put(p.name, VegaType.fromRef(p.type));
                idx++;
            }
            for (Stmt s : constructor.body) {
                genStmt(s);
            }
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            }
        } else {
            // Default constructor
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // Collect operator methods for this class
        Map<String, FuncDecl> ops = new HashMap<>();
        for (FuncDecl md : cd.methods) {
            if (md.isOperator) {
                ops.put(md.name, md);
            }
        }
        if (!ops.isEmpty()) {
            operatorMethods.put(cd.name, ops);
        }

        // Methods
        for (FuncDecl md : cd.methods) {
            generateMethodInClass(cw, md, classJvmName, false);
        }

        cw.visitEnd();
        writeClass(outputDir, classJvmName, cw.toByteArray());
    }

    private void generateMethodInClass(ClassWriter cw, FuncDecl md, String ownerClass, boolean isStatic) {
        StringBuilder desc = new StringBuilder("(");
        for (FuncDecl.Param p : md.params) {
            desc.append(typeRefDesc(p.type));
        }
        desc.append(")");
        desc.append(returnTypeDesc(md.returnType));

        int acc = getAccessFlags(md.visibility);
        if (isStatic) acc |= Opcodes.ACC_STATIC;

        MethodVisitor mv = cw.visitMethod(acc, md.name, desc.toString(), null, null);
        mv.visitCode();
        this.mv = mv;
        currentOwner = ownerClass;
        localCounter = isStatic ? 0 : 1;
        locals.clear();
        localTypes.clear();
        defers.clear(); // Reset defers for this method
        if (!isStatic) {
            locals.put("self", 0);
            localTypes.put("self", new ClassType(ownerClass));
        }
        int idx = isStatic ? 0 : 1;
        for (FuncDecl.Param p : md.params) {
            locals.put(p.name, idx);
            localTypes.put(p.name, VegaType.fromRef(p.type));
            idx += typeSize(VegaType.fromRef(p.type));
        }
        for (int i = 0; i < md.body.size() - 1; i++) {
            genStmt(md.body.get(i));
        }
        
        // Execute deferred statements before return
        for (DeferStmt ds : defers) {
            for (Stmt s : ds.body) {
                genStmt(s);
            }
        }
        defers.clear();
        
        if (!md.body.isEmpty() && md.returnType != null && !"Void".equals(md.returnType.name)) {
            Stmt last = md.body.get(md.body.size() - 1);
            if (last instanceof ReturnStmt rs) {
                genReturn(rs);
            } else if (last instanceof ExprStmt es) {
                genExpr(es.expr);
                VegaType type = inferType(es.expr);
                if (type == PrimitiveType.INT || type == PrimitiveType.BOOL || type == PrimitiveType.CHAR) {
                    mv.visitInsn(Opcodes.IRETURN);
                } else if (type == PrimitiveType.FLOAT) {
                    mv.visitInsn(Opcodes.FRETURN);
                } else {
                    mv.visitInsn(Opcodes.ARETURN);
                }
            } else {
                genStmt(last);
                addDefaultReturn(md);
            }
        } else {
            if (!md.body.isEmpty()) genStmt(md.body.get(md.body.size() - 1));
            addDefaultReturn(md);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateFunction(ClassWriter cw, FuncDecl fd, boolean isStatic) {
        genericTypes.clear();
        if (fd.genericParams != null) {
            genericTypes.addAll(fd.genericParams);
        }

        StringBuilder desc = new StringBuilder("(");
        for (FuncDecl.Param p : fd.params) {
            desc.append(typeRefDesc(p.type));
        }
        desc.append(")");
        desc.append(returnTypeDesc(fd.returnType));

        int acc = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

        MethodVisitor mv = cw.visitMethod(acc, fd.name, desc.toString(), null, null);
        mv.visitCode();
        this.mv = mv;
        currentOwner = jvmClassName;
        localCounter = 0;
        locals.clear();
        localTypes.clear();
        defers.clear(); // Reset defers for this function
        int idx = 0;
        for (FuncDecl.Param p : fd.params) {
            locals.put(p.name, idx);
            localTypes.put(p.name, VegaType.fromRef(p.type));
            idx += typeSize(VegaType.fromRef(p.type));
        }
        // Generate all statements except the last one
        for (int i = 0; i < fd.body.size() - 1; i++) {
            genStmt(fd.body.get(i));
        }
        // Execute deferred statements before return
        for (DeferStmt ds : defers) {
            for (Stmt s : ds.body) {
                genStmt(s);
            }
        }
        defers.clear();
        
        // Handle implicit return for the last statement
        if (!fd.body.isEmpty() && fd.returnType != null && !"Void".equals(fd.returnType.name)) {
            Stmt last = fd.body.get(fd.body.size() - 1);
            if (last instanceof ReturnStmt rs) {
                genReturn(rs);
            } else if (last instanceof ExprStmt es) {
                genExpr(es.expr);
                VegaType type = inferType(es.expr);
                if (type == PrimitiveType.INT || type == PrimitiveType.BOOL || type == PrimitiveType.CHAR) {
                    mv.visitInsn(Opcodes.IRETURN);
                } else if (type == PrimitiveType.FLOAT) {
                    mv.visitInsn(Opcodes.FRETURN);
                } else {
                    mv.visitInsn(Opcodes.ARETURN);
                }
            } else {
                genStmt(last);
                addDefaultReturn(fd);
            }
        } else {
            if (!fd.body.isEmpty()) genStmt(fd.body.get(fd.body.size() - 1));
            addDefaultReturn(fd);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateExtensionMethod(ClassWriter cw, FuncDecl method, String targetType) {
        // Extension methods become static methods with the target type as first parameter
        // extension String { fn isBlank() } becomes static isBlank(String self)
        StringBuilder desc = new StringBuilder("(");
        
        // Add self parameter (the target type)
        if ("String".equals(targetType)) {
            desc.append("Ljava/lang/String;");
        } else {
            // For other types, we'd need a mapping
            desc.append("Ljava/lang/Object;");
        }
        
        // Add actual method parameters
        for (FuncDecl.Param p : method.params) {
            desc.append(typeRefDesc(p.type));
        }
        desc.append(")");
        desc.append(returnTypeDesc(method.returnType));

        int acc = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

        MethodVisitor mv = cw.visitMethod(acc, targetType + "_" + method.name, desc.toString(), null, null);
        mv.visitCode();
        this.mv = mv;
        currentOwner = jvmClassName;
        localCounter = 0;
        locals.clear();
        localTypes.clear();
        
        // First parameter is self (the target object)
        locals.put("self", 0);
        localTypes.put("self", ClassType.STRING);  // Simplified
        int idx = 1;
        
        // Add actual parameters
        for (FuncDecl.Param p : method.params) {
            locals.put(p.name, idx);
            localTypes.put(p.name, VegaType.fromRef(p.type));
            idx += typeSize(VegaType.fromRef(p.type));
        }
        
        // Generate method body
        for (int i = 0; i < method.body.size() - 1; i++) {
            genStmt(method.body.get(i));
        }
        
        if (!method.body.isEmpty() && method.returnType != null && !"Void".equals(method.returnType.name)) {
            Stmt last = method.body.get(method.body.size() - 1);
            if (last instanceof ReturnStmt rs) {
                genReturn(rs);
            } else if (last instanceof ExprStmt es) {
                genExpr(es.expr);
                VegaType type = inferType(es.expr);
                if (type == PrimitiveType.INT || type == PrimitiveType.BOOL || type == PrimitiveType.CHAR) {
                    mv.visitInsn(Opcodes.IRETURN);
                } else if (type == PrimitiveType.FLOAT) {
                    mv.visitInsn(Opcodes.FRETURN);
                } else {
                    mv.visitInsn(Opcodes.ARETURN);
                }
            } else {
                genStmt(last);
                addDefaultReturn(method);
            }
        } else {
            if (!method.body.isEmpty()) genStmt(method.body.get(method.body.size() - 1));
            addDefaultReturn(method);
        }
        
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void addDefaultReturn(FuncDecl fd) {
        if (fd.returnType == null || "Void".equals(fd.returnType.name)) {
            mv.visitInsn(Opcodes.RETURN);
        } else {
            VegaType rt = VegaType.fromRef(fd.returnType);
            if (rt == PrimitiveType.INT || rt == PrimitiveType.BOOL || rt == PrimitiveType.CHAR) {
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
            } else if (rt == PrimitiveType.FLOAT) {
                mv.visitInsn(Opcodes.FCONST_0);
                mv.visitInsn(Opcodes.FRETURN);
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
    }

    private void generateMainMethod(ClassWriter cw, Program program) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mv.visitCode();
        this.mv = mv;
        localCounter = 1;
        locals.clear();
        localTypes.clear();
        defers.clear(); // Reset defers for main method
        locals.put("args", 0);
        localTypes.put("args", new ArrayType(ClassType.STRING));

        // Find main function or execute top-level statements
        FuncDecl mainFn = functions.get("main");
        if (mainFn != null) {
            for (Stmt s : program.statements) {
                if (s instanceof VarDecl vd && vd.initializer != null) {
                    genExpr(vd.initializer);
                    mv.visitFieldInsn(Opcodes.PUTSTATIC, jvmClassName, vd.name,
                            typeDesc(globalVars.get(vd.name)));
                }
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, jvmClassName, "main", "()V", false);
        } else {
            // Execute top-level statements directly
            for (Stmt s : program.statements) {
                if (s instanceof FuncDecl || s instanceof StructDecl || s instanceof ClassDecl
                        || s instanceof EnumDecl || s instanceof ProtocolDecl || s instanceof ExtensionDecl
                        || s instanceof ImportStmt || s instanceof PackageStmt || s instanceof ErrorDecl) {
                    continue;
                }
                genStmt(s);
            }
        }
        
        // Execute deferred statements before return
        for (DeferStmt ds : defers) {
            for (Stmt s : ds.body) {
                genStmt(s);
            }
        }
        defers.clear();
        
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    // ============= Statement Generation =============

    private void genStmt(Stmt s) {
        if (s instanceof VarDecl vd) genVarDecl(vd);
        else if (s instanceof ExprStmt es) genExpr(es.expr);
        else if (s instanceof AssignStmt as) genAssign(as);
        else if (s instanceof ReturnStmt rs) genReturn(rs);
        else if (s instanceof IfStmt is) genIfStmt(is);
        else if (s instanceof IfLetStmt ils) genIfLetStmt(ils);
        else if (s instanceof ForStmt fs) genForStmt(fs);
        else if (s instanceof WhileStmt ws) genWhileStmt(ws);
        else if (s instanceof DeferStmt ds) defers.add(ds);
        else if (s instanceof ThrowStmt ts) genThrow(ts);
        else if (s instanceof TryCatchStmt tc) genTryCatch(tc);
    }

    private void genVarDecl(VarDecl vd) {
        if (vd.initializer != null) {
            genExpr(vd.initializer);
            VegaType type = vd.declaredType != null ? vd.declaredType : inferType(vd.initializer);
            int slot = localCounter;
            localCounter += typeSize(type);
            locals.put(vd.name, slot);
            localTypes.put(vd.name, type);
            storeLocal(mv, type, slot);
        } else {
            VegaType type = vd.declaredType != null ? vd.declaredType : PrimitiveType.INT;
            int slot = localCounter;
            localCounter += typeSize(type);
            locals.put(vd.name, slot);
            localTypes.put(vd.name, type);
        }
    }

    private void genAssign(AssignStmt as) {
        if (as.target instanceof Identifier id) {
            genExpr(as.value);
            VegaType type = localTypes.getOrDefault(id.name, PrimitiveType.INT);
            storeLocal(mv, type, locals.get(id.name));
        } else if (as.target instanceof MemberExpr me) {
            genExpr(me.target);
            genExpr(as.value);
            VegaType targetType = inferType(me.target);
            String owner = jvmNameFromType(targetType);
            VegaType fieldType = localTypes.getOrDefault(me.member, PrimitiveType.INT);
            mv.visitFieldInsn(Opcodes.PUTFIELD, owner, me.member, typeDesc(fieldType));
        } else if (as.target instanceof IndexExpr ie) {
            genExpr(ie.target);
            genExpr(ie.index);
            genExpr(as.value);
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private void genReturn(ReturnStmt rs) {
        // Execute deferred statements before return
        for (DeferStmt ds : defers) {
            for (Stmt s : ds.body) {
                genStmt(s);
            }
        }
        
        if (rs.value == null) {
            mv.visitInsn(Opcodes.RETURN);
        } else {
            genExpr(rs.value);
            VegaType type = inferType(rs.value);
            if (type == PrimitiveType.INT || type == PrimitiveType.BOOL || type == PrimitiveType.CHAR) {
                mv.visitInsn(Opcodes.IRETURN);
            } else if (type == PrimitiveType.FLOAT) {
                mv.visitInsn(Opcodes.FRETURN);
            } else {
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
    }

    private void genIfStmt(IfStmt is) {
        genExpr(is.condition);
        Label elseLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
        for (Stmt s : is.thenBody) genStmt(s);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(elseLabel);
        if (is.elseBody != null) {
            for (Stmt s : is.elseBody) genStmt(s);
        }
        mv.visitLabel(endLabel);
    }

    private void genIfLetStmt(IfLetStmt ils) {
        // Optional binding: if let varName = optionalExpr { ... } else { ... }
        // Evaluate optional expression and check if not null
        genExpr(ils.optionalExpr);
        
        // Store the optional value
        int optSlot = localCounter++;
        VegaType optType = inferType(ils.optionalExpr);
        storeLocal(mv, optType, optSlot);
        
        // Check if not null
        loadLocal(mv, optType, optSlot);
        Label elseLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFNULL, elseLabel);
        
        // Bind variable to unwrapped value
        loadLocal(mv, optType, optSlot);
        int varSlot = localCounter++;
        locals.put(ils.varName, varSlot);
        localTypes.put(ils.varName, optType);
        storeLocal(mv, optType, varSlot);
        
        // Execute then body
        for (Stmt s : ils.thenBody) genStmt(s);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        
        // Else branch
        mv.visitLabel(elseLabel);
        if (ils.elseBody != null) {
            for (Stmt s : ils.elseBody) genStmt(s);
        }
        
        mv.visitLabel(endLabel);
    }

    private void genForStmt(ForStmt fs) {
        // Support both array and range iteration
        VegaType iterType = inferType(fs.iterable);
        
        if (fs.iterable instanceof RangeExpr re) {
            // Range iteration: for i in 0..10 or 0..<10
            genExpr(re.start);
            int startSlot = localCounter++;
            mv.visitVarInsn(Opcodes.ISTORE, startSlot);
            
            genExpr(re.end);
            int endSlot = localCounter++;
            mv.visitVarInsn(Opcodes.ISTORE, endSlot);
            
            // If exclusive, decrement end
            if (re.exclusive) {
                mv.visitVarInsn(Opcodes.ILOAD, endSlot);
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.ISUB);
                mv.visitVarInsn(Opcodes.ISTORE, endSlot);
            }
            
            int idxSlot = localCounter++;
            mv.visitVarInsn(Opcodes.ILOAD, startSlot);
            mv.visitVarInsn(Opcodes.ISTORE, idxSlot);
            
            Label loopStart = new Label();
            Label loopEnd = new Label();
            
            mv.visitLabel(loopStart);
            mv.visitVarInsn(Opcodes.ILOAD, idxSlot);
            mv.visitVarInsn(Opcodes.ILOAD, endSlot);
            mv.visitJumpInsn(Opcodes.IF_ICMPGT, loopEnd);
            
            locals.put(fs.varName, idxSlot);
            localTypes.put(fs.varName, PrimitiveType.INT);
            
            for (Stmt s : fs.body) genStmt(s);
            
            mv.visitIincInsn(idxSlot, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopStart);
            mv.visitLabel(loopEnd);
            
            localCounter = idxSlot;
        } else {
            // Array iteration
            genExpr(fs.iterable);
            int arrSlot = localCounter;
            localCounter++;
            mv.visitVarInsn(Opcodes.ASTORE, arrSlot);
            int idxSlot = localCounter;
            localCounter++;
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, idxSlot);
            Label loopStart = new Label();
            Label loopEnd = new Label();
            mv.visitLabel(loopStart);
            mv.visitVarInsn(Opcodes.ILOAD, idxSlot);
            mv.visitVarInsn(Opcodes.ALOAD, arrSlot);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ALOAD, arrSlot);
            mv.visitVarInsn(Opcodes.ILOAD, idxSlot);
            mv.visitInsn(Opcodes.AALOAD);
            // Store the element as the loop variable
            VegaType elemType = iterType instanceof ArrayType at ? at.element : ClassType.OBJECT;
            unboxIfPrimitive(elemType);
            int elemSlot = localCounter;
            localCounter += typeSize(elemType);
            locals.put(fs.varName, elemSlot);
            localTypes.put(fs.varName, elemType);
            storeLocal(mv, elemType, elemSlot);
            for (Stmt s : fs.body) genStmt(s);
            // Restore local counter
            localCounter = elemSlot;
            mv.visitIincInsn(idxSlot, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopStart);
            mv.visitLabel(loopEnd);
            localCounter = idxSlot;
        }
    }

    private void genWhileStmt(WhileStmt ws) {
        Label loopStart = new Label();
        Label loopEnd = new Label();
        mv.visitLabel(loopStart);
        genExpr(ws.condition);
        mv.visitJumpInsn(Opcodes.IFEQ, loopEnd);
        for (Stmt s : ws.body) genStmt(s);
        mv.visitJumpInsn(Opcodes.GOTO, loopStart);
        mv.visitLabel(loopEnd);
    }

    private void genThrow(ThrowStmt ts) {
        genExpr(ts.error);
        mv.visitInsn(Opcodes.ATHROW);
    }

    private void genTryCatch(TryCatchStmt tc) {
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchStart = new Label();
        Label catchEnd = new Label();
        mv.visitLabel(tryStart);
        for (Stmt s : tc.tryBody) genStmt(s);
        mv.visitLabel(tryEnd);
        mv.visitJumpInsn(Opcodes.GOTO, catchEnd);
        mv.visitLabel(catchStart);
        // Store exception
        int errSlot = localCounter;
        localCounter++;
        locals.put(tc.errorVar, errSlot);
        localTypes.put(tc.errorVar, ClassType.OBJECT);
        mv.visitVarInsn(Opcodes.ASTORE, errSlot);
        for (Stmt s : tc.catchBody) genStmt(s);
        mv.visitLabel(catchEnd);
        mv.visitTryCatchBlock(tryStart, tryEnd, catchStart, "java/lang/Exception");
    }

    // ============= Expression Generation =============

    private void genExpr(Expr e) {
        if (e instanceof IntLiteral il) {
            int v = il.value;
            if (il.inferredType == PrimitiveType.LONG) {
                // Generate as long
                if (v >= -1 && v <= 5) mv.visitInsn(Opcodes.LCONST_0 + v);
                else mv.visitLdcInsn((long) v);
            } else {
                // Generate as int
                if (v >= -1 && v <= 5) mv.visitInsn(Opcodes.ICONST_0 + v);
                else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) mv.visitIntInsn(Opcodes.BIPUSH, v);
                else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) mv.visitIntInsn(Opcodes.SIPUSH, v);
                else mv.visitLdcInsn(v);
            }
        }
        else if (e instanceof FloatLiteral fl) {
            mv.visitLdcInsn((float) fl.value);
        }
        else if (e instanceof StringLiteral sl) {
            if (sl.isInterpolated) {
                // Handle interpolation
                generateInterpolatedString(sl.parts);
            } else {
                mv.visitLdcInsn(sl.value);
            }
        }
        else if (e instanceof CharLiteral cl) {
            mv.visitIntInsn(Opcodes.BIPUSH, cl.value);
        }
        else if (e instanceof BoolLiteral bl) {
            mv.visitInsn(bl.value ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        }
        else if (e instanceof NullLiteral) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        }
        else if (e instanceof Identifier id) genIdentifier(id);
        else if (e instanceof EnumAccessExpr ea) genEnumAccess(ea);
        else if (e instanceof BinaryExpr be) genBinary(be);
        else if (e instanceof UnaryExpr ue) genUnary(ue);
        else if (e instanceof CallExpr ce) genCall(ce);
        else if (e instanceof MemberExpr me) genMember(me);
        else if (e instanceof IndexExpr ie) genIndex(ie);
        else if (e instanceof ArrayLiteral al) genArrayLiteral(al);
        else if (e instanceof IfExpr ie) genIfExpr(ie);
        else if (e instanceof MatchExpr me) genMatch(me);
        else if (e instanceof RangeExpr re) genRange(re);
        else if (e instanceof NullCoalescingExpr nc) genNullCoalescing(nc);
        else if (e instanceof OptionalChainingExpr oc) genOptionalChaining(oc);
        else if (e instanceof PipelineExpr pe) genPipeline(pe);
        else if (e instanceof TupleExpr te) genTuple(te);
        else if (e instanceof LambdaExpr le) genLambda(le);
        else if (e instanceof AwaitExpr ae) genAwait(ae);
    }

    private void genIdentifier(Identifier id) {
        if (locals.containsKey(id.name)) {
            VegaType type = localTypes.get(id.name);
            loadLocal(mv, type, locals.get(id.name));
        } else if (globalVars.containsKey(id.name)) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, jvmClassName, id.name, typeDesc(globalVars.get(id.name)));
        } else {
            String simpleOwner = currentOwner == null ? "" : currentOwner.substring(currentOwner.lastIndexOf('/') + 1);
            StructDecl struct = structs.get(simpleOwner);
            if (currentOwner != null && struct != null) {
                for (VarDecl field : struct.fields) {
                    if (field.name.equals(id.name)) {
                        VegaType type = field.declaredType != null ? field.declaredType : inferType(field.initializer);
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitFieldInsn(Opcodes.GETFIELD, currentOwner, field.name, typeDesc(type));
                        return;
                    }
                }
            }
            if (enums.containsKey(id.name)) {
                mv.visitLdcInsn(id.name);
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
            }
        }
    }

    private void genEnumAccess(EnumAccessExpr ea) {
        // Generate enum access: Status.Active -> GetStatic Status.Active
        String enumJvmName = packageName.replace('.', '/') + "/" + ea.enumName;
        mv.visitFieldInsn(Opcodes.GETSTATIC, enumJvmName, ea.variant, "L" + enumJvmName + ";");
    }

    private void genBinary(BinaryExpr be) {
        // Try operator overloading first
        VegaType leftType = inferType(be.left);
        if (leftType instanceof ClassType ct) {
           String typeName = ct.jvmName.substring(ct.jvmName.lastIndexOf("/") + 1);
Map<String, FuncDecl> ops = operatorMethods.get(typeName);
            if (ops != null && ops.containsKey(be.op)) {
                FuncDecl opMethod = ops.get(be.op);
                genExpr(be.left);
                genExpr(be.right);
                String opDesc = "(";
                for (FuncDecl.Param p : opMethod.params) {
                    opDesc += typeRefDesc(p.type);
                }
                opDesc += ")" + returnTypeDesc(opMethod.returnType);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 
                    ct.jvmName, 
                    be.op, opDesc, false);
                return;
            }
        }
        
        // String concatenation
        if (be.op.equals("+")) {
            VegaType lt = inferType(be.left);
            VegaType rt = inferType(be.right);
            if (lt == ClassType.STRING || rt == ClassType.STRING) {
                genStringBuilder(be);
                return;
            }
        }

        // Short-circuit logical operators
        if (be.op.equals("&&")) {
            Label falseLabel = new Label();
            Label endLabel = new Label();
            genExpr(be.left);
            mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
            genExpr(be.right);
            mv.visitJumpInsn(Opcodes.IFEQ, falseLabel);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(falseLabel);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitLabel(endLabel);
            return;
        }
        if (be.op.equals("||")) {
            Label trueLabel = new Label();
            Label endLabel = new Label();
            genExpr(be.left);
            mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
            genExpr(be.right);
            mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(trueLabel);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitLabel(endLabel);
            return;
        }

        // Arithmetic operators
        if (be.op.equals("+") || be.op.equals("-") || be.op.equals("*")
                || be.op.equals("/") || be.op.equals("%")) {
            genExpr(be.left);
            genExpr(be.right);
            boolean isFloat = inferType(be.left) == PrimitiveType.FLOAT;
            switch (be.op) {
                case "+" -> mv.visitInsn(isFloat ? Opcodes.FADD : Opcodes.IADD);
                case "-" -> mv.visitInsn(isFloat ? Opcodes.FSUB : Opcodes.ISUB);
                case "*" -> mv.visitInsn(isFloat ? Opcodes.FMUL : Opcodes.IMUL);
                case "/" -> mv.visitInsn(isFloat ? Opcodes.FDIV : Opcodes.IDIV);
                case "%" -> mv.visitInsn(isFloat ? Opcodes.FREM : Opcodes.IREM);
            }
            return;
        }

        // Comparison operators — produce boolean (0/1)
        genExpr(be.left);
        genExpr(be.right);
        boolean isFloat = inferType(be.left) == PrimitiveType.FLOAT;
        Label trueLabel = new Label();
        Label endLabel = new Label();
        if (isFloat) {
            mv.visitInsn(Opcodes.FCMPG);
            switch (be.op) {
                case "==" -> mv.visitJumpInsn(Opcodes.IFEQ, trueLabel);
                case "!=" -> mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
                case "<" -> mv.visitJumpInsn(Opcodes.IFLT, trueLabel);
                case ">" -> mv.visitJumpInsn(Opcodes.IFGT, trueLabel);
                case "<=" -> mv.visitJumpInsn(Opcodes.IFLE, trueLabel);
                case ">=" -> mv.visitJumpInsn(Opcodes.IFGE, trueLabel);
            }
        } else {
            switch (be.op) {
                case "==" -> mv.visitJumpInsn(Opcodes.IF_ICMPEQ, trueLabel);
                case "!=" -> mv.visitJumpInsn(Opcodes.IF_ICMPNE, trueLabel);
                case "<" -> mv.visitJumpInsn(Opcodes.IF_ICMPLT, trueLabel);
                case ">" -> mv.visitJumpInsn(Opcodes.IF_ICMPGT, trueLabel);
                case "<=" -> mv.visitJumpInsn(Opcodes.IF_ICMPLE, trueLabel);
                case ">=" -> mv.visitJumpInsn(Opcodes.IF_ICMPGE, trueLabel);
            }
        }
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(endLabel);
    }

    private void genStringBuilder(BinaryExpr be) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        appendToStringBuilder(be.left);
        appendToStringBuilder(be.right);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }

    private void appendToStringBuilder(Expr e) {
        genExpr(e);
        VegaType type = inferType(e);
        if (type == ClassType.STRING) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        } else if (type == PrimitiveType.INT) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
        } else if (type == PrimitiveType.FLOAT) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(F)Ljava/lang/StringBuilder;", false);
        } else if (type == PrimitiveType.BOOL) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;", false);
        } else if (type == PrimitiveType.CHAR) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
        } else {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
        }
    }

    private void genUnary(UnaryExpr ue) {
        genExpr(ue.expr);
        if (ue.op.equals("-")) {
            if (inferType(ue.expr) == PrimitiveType.FLOAT) mv.visitInsn(Opcodes.FNEG);
            else mv.visitInsn(Opcodes.INEG);
        } else if (ue.op.equals("!")) {
            // logical not on boolean
            Label trueLabel = new Label();
            Label endLabel = new Label();
            mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(trueLabel);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitLabel(endLabel);
        }
    }

    private void genCall(CallExpr ce) {
        if (ce.callee instanceof Identifier id) {
            if (id.name.equals("print")) {
                // print(arg) -> System.out.println(arg)
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                if (ce.args.size() > 0) {
                    genExpr(ce.args.get(0));
                    VegaType type = inferType(ce.args.get(0));
                    String desc = switch (type == null ? "" : type.getName()) {
                        case "int" -> "(I)V";
                        case "float" -> "(F)V";
                        case "bool" -> "(Z)V";
                        case "char" -> "(C)V";
                        case "java/lang/String" -> "(Ljava/lang/String;)V";
                        default -> "(Ljava/lang/Object;)V";
                    };
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", desc, false);
                } else {
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
                }
                return;
            }
            // Check if it's a struct/class constructor
            if (structs.containsKey(id.name) || classes.containsKey(id.name)) {
                String owner = packageName.replace('.', '/') + "/" + id.name;
                mv.visitTypeInsn(Opcodes.NEW, owner);
                mv.visitInsn(Opcodes.DUP);
                // Arguments (positional for now)
                for (Expr arg : ce.args) genExpr(arg);
                // Build constructor descriptor
                String desc = "(";
                if (classes.containsKey(id.name)) {
                    ClassDecl cd = classes.get(id.name);
                    FuncDecl constructor = selectConstructor(cd.constructors, ce.args.size());
                    if (constructor != null) for (FuncDecl.Param p : constructor.params) desc += typeRefDesc(p.type);
                } else {
                    StructDecl sd = structs.get(id.name);
                    for (VarDecl field : sd.fields) {
                        VegaType fieldType = field.declaredType != null
                                ? field.declaredType : inferType(field.initializer);
                        desc += typeDesc(fieldType);
                    }
                }
                desc += ")V";
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", desc, false);
                return;
            }
            // User-defined function call
            FuncDecl fd = functions.get(id.name);
            if (fd != null) {
                for (Expr arg : ce.args) genExpr(arg);
                StringBuilder desc = new StringBuilder("(");
                for (FuncDecl.Param p : fd.params) desc.append(typeRefDesc(p.type));
                desc.append(")");
                desc.append(returnTypeDesc(fd.returnType));
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, jvmClassName, id.name, desc.toString(), false);
                return;
            }
            // Unknown function
            for (Expr arg : ce.args) genExpr(arg);
            mv.visitInsn(Opcodes.ACONST_NULL);
            return;
        }
        if (ce.callee instanceof MemberExpr me) {
            // Method call on object
            genExpr(me.target);
            for (Expr arg : ce.args) genExpr(arg);
            VegaType targetType = inferType(me.target);
            String owner = jvmNameFromType(targetType);
            // Try to find method
            String desc = "(";
            for (Expr arg : ce.args) desc += typeDesc(inferType(arg));
            VegaType retType = PrimitiveType.VOID;
            if (me.member.equals("toString")) {
                retType = ClassType.STRING;
            }
            if (targetType instanceof ClassType ct) {
                String simpleName = ct.jvmName.substring(ct.jvmName.lastIndexOf('/') + 1);
                StructDecl sd = structs.get(simpleName);
                if (sd != null) {
                    for (FuncDecl method : sd.methods) {
                        if (method.name.equals(me.member) && method.returnType != null) {
                            retType = VegaType.fromRef(method.returnType);
                            break;
                        }
                    }
                }
            }
            desc += ")" + typeDesc(retType);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, me.member, desc, false);
            return;
        }
        // Fallback
        for (Expr arg : ce.args) genExpr(arg);
    }

    private FuncDecl selectConstructor(List<FuncDecl> constructors, int argumentCount) {
        for (FuncDecl constructor : constructors) {
            if (constructor.params.size() == argumentCount) return constructor;
        }
        return constructors.isEmpty() ? null : constructors.get(0);
    }

    private void genMember(MemberExpr me) {
        genExpr(me.target);
        VegaType targetType = inferType(me.target);
        String owner = jvmNameFromType(targetType);
        // Look up field type
        VegaType fieldType = PrimitiveType.INT;
        if (targetType instanceof ClassType ct) {
            StructDecl sd = structs.get(ct.jvmName.contains("/") ? ct.jvmName.substring(ct.jvmName.lastIndexOf('/') + 1) : ct.jvmName);
            if (sd != null) {
                for (VarDecl f : sd.fields) {
                    if (f.name.equals(me.member)) {
                        fieldType = f.declaredType != null ? f.declaredType : PrimitiveType.INT;
                        break;
                    }
                }
            }
        }
        mv.visitFieldInsn(Opcodes.GETFIELD, owner, me.member, typeDesc(fieldType));
    }

    private void genIndex(IndexExpr ie) {
        genExpr(ie.target);
        genExpr(ie.index);
        mv.visitInsn(Opcodes.AALOAD);
        VegaType targetType = inferType(ie.target);
        if (targetType instanceof ArrayType at) unboxIfPrimitive(at.element);
    }

    private void genArrayLiteral(ArrayLiteral al) {
        mv.visitIntInsn(Opcodes.BIPUSH, al.elements.size());
        // Determine element type
        VegaType elemType = al.elements.isEmpty() ? ClassType.OBJECT : inferType(al.elements.get(0));
        mv.visitTypeInsn(Opcodes.ANEWARRAY, jvmNameFromType(elemType));
        for (int i = 0; i < al.elements.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitIntInsn(Opcodes.BIPUSH, i);
            genExpr(al.elements.get(i));
            boxIfPrimitive(inferType(al.elements.get(i)));
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private void genIfExpr(IfExpr ie) {
        genExpr(ie.condition);
        Label elseLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
        if (!ie.thenBody.isEmpty()) genExpr(ie.thenBody.get(ie.thenBody.size() - 1));
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        mv.visitLabel(elseLabel);
        if (ie.elseBody != null && !ie.elseBody.isEmpty()) genExpr(ie.elseBody.get(ie.elseBody.size() - 1));
        mv.visitLabel(endLabel);
    }

    private void genMatch(MatchExpr me) {
        genExpr(me.subject);
        // Store subject in a local variable for comparison
        int subjectSlot = localCounter;
        VegaType subjectType = inferType(me.subject);
        localCounter += typeSize(subjectType);
        storeLocal(mv, subjectType, subjectSlot);
        
        Label endLabel = new Label();
        List<Label> armLabels = new ArrayList<>();
        for (int i = 0; i < me.arms.size(); i++) {
            armLabels.add(new Label());
        }
        
        boolean hasWildcard = false;
        int wildcardIndex = -1;
        
        // First pass: process all arms
        for (int i = 0; i < me.arms.size(); i++) {
            MatchArm arm = me.arms.get(i);
            
            if (arm.pattern instanceof Identifier id && "_".equals(id.name)) {
                hasWildcard = true;
                wildcardIndex = i;
                continue; // Process wildcard last
            }
            
            // Load subject for comparison
            loadLocal(mv, subjectType, subjectSlot);
            
            boolean shouldJump = true;
            
            if (arm.pattern instanceof IntLiteral il) {
                // Integer pattern: subject == pattern
                mv.visitLdcInsn(il.value);
                mv.visitJumpInsn(Opcodes.IF_ICMPEQ, armLabels.get(i));
            } 
            else if (arm.pattern instanceof StringLiteral sl) {
                // String pattern: subject.equals(pattern)
                mv.visitLdcInsn(sl.value);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", 
                    "(Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(Opcodes.IFNE, armLabels.get(i));
            }
            else if (arm.pattern instanceof EnumAccessExpr ea) {
                // Enum pattern: Status.Active
                // Use reference equality for enum comparison
                String enumJvmName = packageName.replace('.', '/') + "/" + ea.enumName;
                mv.visitFieldInsn(Opcodes.GETSTATIC, enumJvmName, ea.variant, "L" + enumJvmName + ";");
                mv.visitJumpInsn(Opcodes.IF_ACMPEQ, armLabels.get(i));
            }
            else if (arm.pattern instanceof RangeExpr re) {
                // Range pattern: start <= subject <= end
                // For exclusive ranges: start <= subject < end
                Label nextArm = i + 1 < me.arms.size() ? armLabels.get(i + 1) : endLabel;
                Label matchLabel = armLabels.get(i);
                
                // Check lower bound: subject >= start
                loadLocal(mv, subjectType, subjectSlot);
                genExpr(re.start);
                mv.visitJumpInsn(Opcodes.IF_ICMPLT, nextArm);
                
                // Check upper bound: subject <= end (or < end if exclusive)
                loadLocal(mv, subjectType, subjectSlot);
                genExpr(re.end);
                if (re.exclusive) {
                    mv.visitJumpInsn(Opcodes.IF_ICMPGE, nextArm);
                } else {
                    mv.visitJumpInsn(Opcodes.IF_ICMPGT, nextArm);
                }
                
                // Both bounds match, jump to this arm
                mv.visitJumpInsn(Opcodes.GOTO, matchLabel);
                shouldJump = false; // Already jumped
            }
            else if (arm.pattern instanceof Identifier id && !hasWildcard) {
                // Binding pattern: just jump and bind the variable
                mv.visitInsn(Opcodes.POP); // Remove subject from stack
                mv.visitJumpInsn(Opcodes.GOTO, armLabels.get(i));
                shouldJump = false;
            }
            else {
                // Default: try to match as expression
                genExpr(arm.pattern);
                // String comparison for generic objects
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", 
                    "(Ljava/lang/Object;)Z", false);
                mv.visitJumpInsn(Opcodes.IFNE, armLabels.get(i));
            }
        }
        
        // Generate arm bodies
        for (int i = 0; i < me.arms.size(); i++) {
            if (i == wildcardIndex) continue; // Handle wildcard last
            
            MatchArm arm = me.arms.get(i);
            mv.visitLabel(armLabels.get(i));
            
            // Execute arm body
            if (arm.body.isEmpty()) {
                mv.visitInsn(Opcodes.ACONST_NULL);
            } else {
                for (int j = 0; j < arm.body.size(); j++) {
                    genExpr(arm.body.get(j));
                    // Pop intermediate expressions
                    if (j < arm.body.size() - 1) {
                        mv.visitInsn(Opcodes.POP);
                    }
                }
            }
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        }
        
        // Generate wildcard arm (if present)
        if (hasWildcard && wildcardIndex >= 0) {
            mv.visitLabel(armLabels.get(wildcardIndex));
            MatchArm arm = me.arms.get(wildcardIndex);
            
            if (arm.body.isEmpty()) {
                mv.visitInsn(Opcodes.ACONST_NULL);
            } else {
                for (int j = 0; j < arm.body.size(); j++) {
                    genExpr(arm.body.get(j));
                    if (j < arm.body.size() - 1) {
                        mv.visitInsn(Opcodes.POP);
                    }
                }
            }
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        }
        
        mv.visitLabel(endLabel);
    }

    private void genRange(RangeExpr re) {
        // Generate an int array for the range
        genExpr(re.start);
        genExpr(re.end);
        // Create array of appropriate size
        // For now, create an Integer array
        // start..end => [start, start+1, ..., end]
        // This is simplified
        mv.visitInsn(Opcodes.SWAP);
        mv.visitInsn(Opcodes.DUP2);
        mv.visitInsn(Opcodes.ISUB);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IADD);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Integer");
        // TODO: fill array
    }

    private void genNullCoalescing(NullCoalescingExpr nc) {
        genExpr(nc.left);
        Label notNull = new Label();
        Label end = new Label();
        mv.visitInsn(Opcodes.DUP);
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        mv.visitInsn(Opcodes.POP);
        genExpr(nc.right);
        mv.visitJumpInsn(Opcodes.GOTO, end);
        mv.visitLabel(notNull);
        mv.visitLabel(end);
    }

    private void genOptionalChaining(OptionalChainingExpr oc) {
        genExpr(oc.target);
        Label nullLabel = new Label();
        Label end = new Label();
        mv.visitInsn(Opcodes.DUP);
        mv.visitJumpInsn(Opcodes.IFNULL, nullLabel);
        // Load member
        // For now, just get field
        mv.visitFieldInsn(Opcodes.GETFIELD, "java/lang/Object", oc.member, "Ljava/lang/Object;");
        mv.visitJumpInsn(Opcodes.GOTO, end);
        mv.visitLabel(nullLabel);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitLabel(end);
    }

    private void genPipeline(PipelineExpr pe) {
        // left |> right => right(left)
        if (pe.right instanceof CallExpr ce) {
            // Prepend left as first argument
            List<Expr> newArgs = new ArrayList<>();
            newArgs.add(pe.left);
            newArgs.addAll(ce.args);
            genExpr(new CallExpr(ce.callee, newArgs, ce.argNames, pe.line, pe.col));
        } else if (pe.right instanceof Identifier id) {
            // left |> print => print(left)
            List<Expr> args = new ArrayList<>();
            args.add(pe.left);
            genExpr(new CallExpr(id, args, null, pe.line, pe.col));
        } else {
            genExpr(pe.right);
        }
    }

    private void genTuple(TupleExpr te) {
        // Tuples compile to Object arrays
        mv.visitIntInsn(Opcodes.BIPUSH, te.elements.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        for (int i = 0; i < te.elements.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitIntInsn(Opcodes.BIPUSH, i);
            genExpr(te.elements.get(i));
            boxIfPrimitive(inferType(te.elements.get(i)));
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private void genLambda(LambdaExpr le) {
        // Lambda expression: { x -> x * 2 }
        // Simplified implementation: treat lambda as an object wrapper
        // that can be called later
        
        // For now, generate the lambda body directly
        // Full implementation would create a callable wrapper
        
        if (!le.body.isEmpty()) {
            // Execute lambda body
            for (int i = 0; i < le.body.size(); i++) {
                genExpr(le.body.get(i));
                // Pop intermediate results except the last one
                if (i < le.body.size() - 1) {
                    mv.visitInsn(Opcodes.POP);
                }
            }
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL);
        }
    }

    private void genAwait(AwaitExpr ae) {
        // Simple synchronous implementation: just evaluate the expression
        // In a full implementation, this would handle real async/await with continuations
        genExpr(ae.expr);
    }

    private void generateInterpolatedString(List<Expr> parts) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        for (Expr part : parts) {
            if (part instanceof StringLiteral sl && !sl.isInterpolated) {
                mv.visitLdcInsn(sl.value);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            } else {
                genExpr(part);
                VegaType type = inferType(part);
                if (type == PrimitiveType.INT) {
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
                } else if (type == PrimitiveType.FLOAT) {
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(F)Ljava/lang/StringBuilder;", false);
                } else if (type == PrimitiveType.BOOL) {
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;", false);
                } else if (type == PrimitiveType.CHAR) {
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
                } else {
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
                }
            }
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }

    // ============= Type helpers =============

    private VegaType inferType(Expr e) {
        if (e == null) return PrimitiveType.VOID;
        if (e.inferredType != null) return e.inferredType;
        VegaType t = doInfer(e);
        e.inferredType = t;
        return t;
    }

    private VegaType doInfer(Expr e) {
        if (e instanceof IntLiteral) return PrimitiveType.INT;
        if (e instanceof FloatLiteral) return PrimitiveType.FLOAT;
        if (e instanceof StringLiteral) return ClassType.STRING;
        if (e instanceof CharLiteral) return PrimitiveType.CHAR;
        if (e instanceof BoolLiteral) return PrimitiveType.BOOL;
        if (e instanceof NullLiteral) return ClassType.OBJECT;
        if (e instanceof Identifier id) {
            if (localTypes.containsKey(id.name)) return localTypes.get(id.name);
            if (globalVars.containsKey(id.name)) return globalVars.get(id.name);
            if (enums.containsKey(id.name)) return new ClassType(packageName.replace('.', '/') + "/" + id.name);
            if (currentOwner != null) {
                StructDecl struct = structs.get(currentOwner.substring(currentOwner.lastIndexOf('/') + 1));
                if (struct != null) {
                    for (VarDecl field : struct.fields) {
                        if (field.name.equals(id.name)) {
                            return field.declaredType != null ? field.declaredType : inferType(field.initializer);
                        }
                    }
                }
            }
            return ClassType.OBJECT;
        }
        if (e instanceof BinaryExpr be) {
            if (be.op.equals("+")) {
                VegaType lt = inferType(be.left);
                VegaType rt = inferType(be.right);
                if (lt == ClassType.STRING || rt == ClassType.STRING) return ClassType.STRING;
                if (lt == PrimitiveType.FLOAT || rt == PrimitiveType.FLOAT) return PrimitiveType.FLOAT;
                return PrimitiveType.INT;
            }
            if (be.op.equals("-") || be.op.equals("*") || be.op.equals("/") || be.op.equals("%")) {
                VegaType lt = inferType(be.left);
                if (lt == PrimitiveType.FLOAT) return PrimitiveType.FLOAT;
                return PrimitiveType.INT;
            }
            if (be.op.equals("==") || be.op.equals("!=") || be.op.equals("<") || be.op.equals(">")
                    || be.op.equals("<=") || be.op.equals(">=") || be.op.equals("&&") || be.op.equals("||")) {
                return PrimitiveType.BOOL;
            }
        }
        if (e instanceof UnaryExpr ue) {
            if (ue.op.equals("!")) return PrimitiveType.BOOL;
            return inferType(ue.expr);
        }
        if (e instanceof CallExpr ce) {
            if (ce.callee instanceof Identifier id) {
                if (id.name.equals("print")) return PrimitiveType.VOID;
                FuncDecl fd = functions.get(id.name);
                if (fd != null && fd.returnType != null) return VegaType.fromRef(fd.returnType);
                if (structs.containsKey(id.name) || classes.containsKey(id.name)) {
                    return new ClassType(packageName.replace('.', '/') + "/" + id.name);
                }
            }
            return ClassType.OBJECT;
        }
        if (e instanceof MemberExpr me) {
            VegaType targetType = inferType(me.target);
            if (targetType instanceof ClassType ct) {
                String simpleName = ct.jvmName.contains("/") ? ct.jvmName.substring(ct.jvmName.lastIndexOf('/') + 1) : ct.jvmName;
                StructDecl sd = structs.get(simpleName);
                if (sd != null) {
                    for (VarDecl f : sd.fields) {
                        if (f.name.equals(me.member)) return f.declaredType;
                    }
                }
            }
            return ClassType.OBJECT;
        }
        if (e instanceof ArrayLiteral al) {
            if (al.elements.isEmpty()) return new ArrayType(ClassType.OBJECT);
            return new ArrayType(inferType(al.elements.get(0)));
        }
        if (e instanceof IfExpr ie) {
            if (ie.thenBody != null && !ie.thenBody.isEmpty()) return inferType(ie.thenBody.get(ie.thenBody.size() - 1));
            return ClassType.OBJECT;
        }
        if (e instanceof MatchExpr me) {
            if (me.arms != null && !me.arms.isEmpty() && !me.arms.get(0).body.isEmpty())
                return inferType(me.arms.get(0).body.get(0));
            return ClassType.OBJECT;
        }
        if (e instanceof IndexExpr) return ClassType.OBJECT;
        if (e instanceof TupleExpr) return ClassType.OBJECT;
        if (e instanceof NullCoalescingExpr nc) return inferType(nc.left);
        if (e instanceof OptionalChainingExpr) return ClassType.OBJECT;
        if (e instanceof PipelineExpr pe) {
            if (pe.right instanceof CallExpr ce) return inferType(ce);
            return ClassType.OBJECT;
        }
        return ClassType.OBJECT;
    }

    private String typeDesc(VegaType type) {
        if (type == null) return "Ljava/lang/Object;";
        if (type instanceof PrimitiveType pt) {
            return switch (pt.getName()) {
                case "int" -> "I";
                case "long" -> "J";
                case "float" -> "F";
                case "bool" -> "Z";
                case "char" -> "C";
                case "void" -> "V";
                default -> "Ljava/lang/Object;";
            };
        }
        if (type instanceof ClassType ct) {
            if (genericTypes.contains(ct.jvmName)) {
                return "Ljava/lang/Object;";
            }
            return "L" + ct.jvmName + ";";
        }
        if (type instanceof ArrayType at) return "[L" + jvmNameFromType(at.element) + ";";
        if (type instanceof OptionalType) return "Ljava/lang/Object;";
        return "Ljava/lang/Object;";
    }

    private String returnTypeDesc(VegaTypeRef ref) {
        if (ref == null || ref.name.equals("Void")) return "V";
        return typeDesc(VegaType.fromRef(ref));
    }

    private String typeRefDesc(VegaTypeRef ref) {
        return typeDesc(VegaType.fromRef(ref));
    }

    private String jvmNameFromType(VegaType type) {
        if (type == null) return "java/lang/Object";
        if (type instanceof PrimitiveType pt) {
            return switch (pt.getName()) {
                case "int" -> "java/lang/Integer";
                case "long" -> "java/lang/Long";
                case "float" -> "java/lang/Float";
                case "bool" -> "java/lang/Boolean";
                case "char" -> "java/lang/Character";
                default -> "java/lang/Object";
            };
        }
        if (type instanceof ClassType ct) {
            if (genericTypes.contains(ct.jvmName)) {
                return "java/lang/Object";
            }
            return ct.jvmName;
        }
        if (type instanceof ArrayType) return "java/lang/Object";
        return "java/lang/Object";
    }

    private int typeSize(VegaType type) {
        if (type == PrimitiveType.FLOAT || type == PrimitiveType.LONG) return 2;
        return 1;
    }

    private void loadLocal(MethodVisitor mv, VegaType type, int slot) {
        if (type == PrimitiveType.INT || type == PrimitiveType.BOOL || type == PrimitiveType.CHAR) {
            mv.visitVarInsn(Opcodes.ILOAD, slot);
        } else if (type == PrimitiveType.LONG) {
            mv.visitVarInsn(Opcodes.LLOAD, slot);
        } else if (type == PrimitiveType.FLOAT) {
            mv.visitVarInsn(Opcodes.FLOAD, slot);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, slot);
        }
    }

    private void storeLocal(MethodVisitor mv, VegaType type, int slot) {
        if (type == PrimitiveType.INT || type == PrimitiveType.BOOL || type == PrimitiveType.CHAR) {
            mv.visitVarInsn(Opcodes.ISTORE, slot);
        } else if (type == PrimitiveType.LONG) {
            mv.visitVarInsn(Opcodes.LSTORE, slot);
        } else if (type == PrimitiveType.FLOAT) {
            mv.visitVarInsn(Opcodes.FSTORE, slot);
        } else {
            mv.visitVarInsn(Opcodes.ASTORE, slot);
        }
    }

    private void boxIfPrimitive(VegaType type) {
        if (type == PrimitiveType.INT) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (type == PrimitiveType.LONG) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        } else if (type == PrimitiveType.FLOAT) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
        } else if (type == PrimitiveType.BOOL) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        } else if (type == PrimitiveType.CHAR) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
        }
    }

    private void unboxIfPrimitive(VegaType type) {
        if (type == PrimitiveType.INT) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
        } else if (type == PrimitiveType.LONG) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        } else if (type == PrimitiveType.FLOAT) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
        } else if (type == PrimitiveType.BOOL) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        } else if (type == PrimitiveType.CHAR) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
        }
    }

    private int getAccessFlags(String visibility) {
        if (visibility == null) visibility = "internal";
        return switch (visibility) {
            case "public" -> Opcodes.ACC_PUBLIC;
            case "private" -> Opcodes.ACC_PRIVATE;
            case "protected" -> Opcodes.ACC_PROTECTED;
            case "internal" -> 0; // package-private in JVM (no access flag)
            default -> 0;
        };
    }

    private void writeClass(String outputDir, String jvmName, byte[] bytes) throws Exception {
        String path = outputDir + "/" + jvmName.replace('/', '/') + ".class";
        Path filePath = Paths.get(outputDir, jvmName.split("/"));
        Files.createDirectories(filePath.getParent());
        try (FileOutputStream fos = new FileOutputStream(outputDir + "/" + jvmName + ".class")) {
            fos.write(bytes);
        }
    }
}
