package com.vega.compiler.parser;

import com.vega.compiler.lexer.Lexer;
import com.vega.compiler.lexer.Token;
import com.vega.compiler.lexer.TokenType;
import com.vega.compiler.ast.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Parser {
    private final List<Token> tokens;
    private int pos = 0;
    private String currentPackage = "vega";
    private final Set<String> declaredEnums = new HashSet<>();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Program parse() {
        List<Stmt> stmts = new ArrayList<>();
        // First pass: collect enum names
        for (Stmt s : stmts) {
            if (s instanceof EnumDecl ed) {
                declaredEnums.add(ed.name);
            }
        }
        while (!isAtEnd()) {
            stmts.add(parseTopLevel());
        }
        Program program = new Program(stmts);
        program.packageName = currentPackage;
        return program;
    }

    private Stmt parseTopLevel() {
        if (check(TokenType.PACKAGE)) return parsePackage();
        if (check(TokenType.IMPORT)) return parseImport();
        if (checkToken(TokenType.IDENT, s -> s.startsWith("@"))) return parseAnnotationThenDecl();
        return parseDeclaration(null);
    }

    private Stmt parsePackage() {
        Token t = advance();
        StringBuilder sb = new StringBuilder();
        sb.append(consumeIdent("package name").lexeme);
        while (match(TokenType.DOT)) {
            sb.append(".");
            sb.append(consumeIdent("package name").lexeme);
        }
        return new PackageStmt(sb.toString(), t.line, t.col);
    }

    private Stmt parseImport() {
        Token t = advance();
        StringBuilder sb = new StringBuilder();
        sb.append(consumeIdent("import module").lexeme);
        while (match(TokenType.DOT)) {
            sb.append(".");
            if (check(TokenType.IDENT)) sb.append(advance().lexeme);
            else if (check(TokenType.STAR)) { sb.append(advance().lexeme); break; }
        }
        String module = sb.toString();
        String symbol = null;
        String alias = null;
        // import models.user.User
        // We already consumed the full path; let's split
        int lastDot = module.lastIndexOf('.');
        if (lastDot > 0 && !module.endsWith(".*")) {
            symbol = module.substring(lastDot + 1);
            module = module.substring(0, lastDot);
        }
        if (match(TokenType.AS)) {
            alias = consumeIdent("alias name").lexeme;
        }
        return new ImportStmt(module, symbol, alias, t.line, t.col);
    }

    private Stmt parseAnnotationThenDecl() {
        // Skip annotation(s)
        while (checkToken(TokenType.IDENT, s -> s.startsWith("@"))) {
            advance();
        }
        return parseDeclaration(null);
    }

    private Stmt parseDeclaration(String visibility) {
        if (check(TokenType.PUBLIC)) { visibility = "public"; advance(); }
        else if (check(TokenType.PRIVATE)) { visibility = "private"; advance(); }
        else if (check(TokenType.PROTECTED)) { visibility = "protected"; advance(); }
        else if (check(TokenType.INTERNAL)) { visibility = "internal"; advance(); }
        if (visibility == null) visibility = "internal";

        if (check(TokenType.LET) || check(TokenType.VAR)) return parseVarDecl(visibility);
        if (check(TokenType.FN)) return parseFuncDecl(visibility, false, false, false);
        if (check(TokenType.STRUCT)) return parseStructDecl(visibility);
        if (check(TokenType.CLASS)) return parseClassDecl(visibility);
        if (check(TokenType.ENUM)) return parseEnumDecl(visibility);
        if (check(TokenType.PROTOCOL)) return parseProtocolDecl(visibility);
        if (check(TokenType.EXTENSION)) return parseExtensionDecl();
        if (check(TokenType.ERROR)) return parseErrorDecl();
        if (check(TokenType.ANNOTATION)) return parseAnnotationDecl();
        if (check(TokenType.ACTOR)) return parseActorDecl(visibility);
        return parseStatement();
    }

    private VarDecl parseVarDecl(String visibility) {
        Token t = advance();
        boolean mutable = t.type == TokenType.VAR;
        String name = consumeIdent("variable name").lexeme;
        VegaTypeRef typeRef = null;
        if (match(TokenType.COLON)) {
            typeRef = parseTypeRef();
        }
        Expr init = null;
        if (match(TokenType.EQ)) {
            init = parseExpr();
        }
        return new VarDecl(mutable, name, typeRef != null ? resolveType(typeRef) : null, init, visibility, t.line, t.col);
    }

    private FuncDecl parseFuncDecl(String visibility, boolean isOverride, boolean isOperator, boolean isAsync) {
        Token t = advance(); // fn
        String name = consumeIdent("function name").lexeme;
        List<String> genericParams = new ArrayList<>();
        if (match(TokenType.LT)) {
            do { genericParams.add(consumeIdent("generic param").lexeme); }
            while (match(TokenType.COMMA));
            expect(TokenType.GT, ">");
        }
        expect(TokenType.LPAREN, "(");
        List<FuncDecl.Param> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                String pname = consumeIdent("parameter name").lexeme;
                expect(TokenType.COLON, ":");
                VegaTypeRef ptype = parseTypeRef();
                params.add(new FuncDecl.Param(pname, ptype));
            } while (match(TokenType.COMMA));
        }
        expect(TokenType.RPAREN, ")");
        VegaTypeRef returnType = null;
        boolean throwsError = false;
        if (match(TokenType.ARROW)) {
            returnType = parseTypeRef();
        }
        if (match(TokenType.THROWS)) {
            throwsError = true;
        }
        
        // Parse where clause for generic constraints (optional)
        // where T: Printable & Identifiable
        if (match(TokenType.WHERE)) {
            while (check(TokenType.IDENT)) {
                String typeParam = advance().lexeme;
                expect(TokenType.COLON, ":");
                List<String> bounds = new ArrayList<>();
                do {
                    bounds.add(consumeIdent("protocol name").lexeme);
                } while (match(TokenType.AND_AND));
                // Store constraints (for future validation)
                // For now, just skip
            }
        }
        
        List<Stmt> body = new ArrayList<>();
        if (match(TokenType.LBRACE)) {
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                body.add(parseStatement());
            }
            expect(TokenType.RBRACE, "}");
        }
        // Set package context
        return new FuncDecl(name, params, returnType, body, isAsync, throwsError,
                visibility, isOverride, isOperator, genericParams, t.line, t.col);
    }

    private StructDecl parseStructDecl(String visibility) {
        Token t = advance();
        String name = consumeIdent("struct name").lexeme;
        List<String> genericParams = new ArrayList<>();
        if (match(TokenType.LT)) {
            do { genericParams.add(consumeIdent("generic param").lexeme); }
            while (match(TokenType.COMMA));
            expect(TokenType.GT, ">");
        }
        List<String> implementsList = new ArrayList<>();
        if (match(TokenType.IMPLEMENTS)) {
            do { implementsList.add(consumeIdent("protocol name").lexeme); }
            while (match(TokenType.COMMA));
        }
        
        // Parse where clause (optional)
        if (match(TokenType.WHERE)) {
            while (check(TokenType.IDENT)) {
                String typeParam = advance().lexeme;
                expect(TokenType.COLON, ":");
                List<String> bounds = new ArrayList<>();
                do {
                    bounds.add(consumeIdent("protocol name").lexeme);
                } while (match(TokenType.AND_AND));
                // Store constraints (for future validation)
            }
        }
        
        expect(TokenType.LBRACE, "{");
        List<VarDecl> fields = new ArrayList<>();
        List<FuncDecl> methods = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            String vis = null;
            if (check(TokenType.PUBLIC)) { vis = "public"; advance(); }
            else if (check(TokenType.PRIVATE)) { vis = "private"; advance(); }
            if (check(TokenType.LET) || check(TokenType.VAR)) {
                fields.add(parseVarDecl(vis != null ? vis : "internal"));
            } else if (check(TokenType.FN)) {
                methods.add(parseFuncDecl(vis != null ? vis : "internal", false, false, false));
            } else {
                throw error(peek(), "Expected field or method in struct");
            }
        }
        expect(TokenType.RBRACE, "}");
        return new StructDecl(name, fields, methods, implementsList, genericParams, visibility, t.line, t.col);
    }

    private ClassDecl parseClassDecl(String visibility) {
        Token t = advance();
        String name = consumeIdent("class name").lexeme;
        List<String> genericParams = new ArrayList<>();
        if (match(TokenType.LT)) {
            do { genericParams.add(consumeIdent("generic param").lexeme); }
            while (match(TokenType.COMMA));
            expect(TokenType.GT, ">");
        }
        String superClass = null;
        if (match(TokenType.EXTENDS)) {
            superClass = consumeIdent("superclass name").lexeme;
        }
        List<String> implementsList = new ArrayList<>();
        if (match(TokenType.IMPLEMENTS)) {
            do { implementsList.add(consumeIdent("protocol name").lexeme); }
            while (match(TokenType.COMMA));
        }
        expect(TokenType.LBRACE, "{");
        List<VarDecl> fields = new ArrayList<>();
        List<FuncDecl> methods = new ArrayList<>();
        List<FuncDecl> constructors = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            boolean isOverride = false;
            boolean isOperator = false;
            String vis = null;
            while (true) {
                if (check(TokenType.OVERRIDE)) { isOverride = true; advance(); continue; }
                if (check(TokenType.OPERATOR)) { isOperator = true; advance(); continue; }
                if (check(TokenType.PUBLIC)) { vis = "public"; advance(); continue; }
                if (check(TokenType.PRIVATE)) { vis = "private"; advance(); continue; }
                if (check(TokenType.PROTECTED)) { vis = "protected"; advance(); continue; }
                break;
            }
            if (check(TokenType.INIT)) {
                advance();
                expect(TokenType.LPAREN, "(");
                List<FuncDecl.Param> params = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    do {
                        String pname = consumeIdent("parameter name").lexeme;
                        expect(TokenType.COLON, ":");
                        VegaTypeRef ptype = parseTypeRef();
                        params.add(new FuncDecl.Param(pname, ptype));
                    } while (match(TokenType.COMMA));
                }
                expect(TokenType.RPAREN, ")");
                List<Stmt> body = new ArrayList<>();
                expect(TokenType.LBRACE, "{");
                while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) body.add(parseStatement());
                expect(TokenType.RBRACE, "}");
                constructors.add(new FuncDecl("init", params, null, body, false, false,
                        vis != null ? vis : "internal", false, false, new ArrayList<>(), 0, 0));
            } else if (check(TokenType.LET) || check(TokenType.VAR)) {
                fields.add(parseVarDecl(vis != null ? vis : "internal"));
            } else if (check(TokenType.FN)) {
                methods.add(parseFuncDecl(vis != null ? vis : "internal", isOverride, isOperator, false));
            } else {
                throw error(peek(), "Expected field, method, or init in class");
            }
        }
        expect(TokenType.RBRACE, "}");
        return new ClassDecl(name, superClass, fields, methods, constructors, implementsList,
                genericParams, visibility, t.line, t.col);
    }

   private EnumDecl parseEnumDecl(String visibility) {
    Token t = advance();
    String name = consumeIdent("enum name").lexeme;

    // Generic parameters: enum Result<T>
    if (match(TokenType.LT)) {
        do {
            consumeIdent("generic param");
        } while (match(TokenType.COMMA));
        expect(TokenType.GT, ">");
    }

    declaredEnums.add(name);
    expect(TokenType.LBRACE, "{");

    List<String> variants = new ArrayList<>();
    while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
        variants.add(consumeIdent("enum variant").lexeme);
    }

    expect(TokenType.RBRACE, "}");
    return new EnumDecl(name, variants, visibility, t.line, t.col);
}

    private ProtocolDecl parseProtocolDecl(String visibility) {
        Token t = advance();
        String name = consumeIdent("protocol name").lexeme;
        expect(TokenType.LBRACE, "{");
        List<FuncDecl> sigs = new ArrayList<>();
        List<FuncDecl> defaults = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            FuncDecl fn = parseFuncDecl("public", false, false, false);
            if (fn.body != null && !fn.body.isEmpty()) {
                defaults.add(fn);
            } else {
                sigs.add(fn);
            }
        }
        expect(TokenType.RBRACE, "}");
        return new ProtocolDecl(name, sigs, defaults, visibility, t.line, t.col);
    }

    private ExtensionDecl parseExtensionDecl() {
        Token t = advance();
        String targetType = consumeIdent("type name").lexeme;
        expect(TokenType.LBRACE, "{");
        List<FuncDecl> methods = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            methods.add(parseFuncDecl("public", false, false, false));
        }
        expect(TokenType.RBRACE, "}");
        return new ExtensionDecl(targetType, methods, t.line, t.col);
    }

    private ErrorDecl parseErrorDecl() {
        Token t = advance();
        String name = consumeIdent("error name").lexeme;
        List<VarDecl> fields = new ArrayList<>();

        if (match(TokenType.LBRACE)) {
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
                Token fieldToken = consumeIdent("error field name");
                expect(TokenType.COLON, ":");
                VegaTypeRef typeRef = parseTypeRef();

                fields.add(new VarDecl(
                        false,
                        fieldToken.lexeme,
                        resolveType(typeRef),
                        null,
                        "internal",
                        fieldToken.line,
                        fieldToken.col
                ));
            }
            expect(TokenType.RBRACE, "}");
        }

        return new ErrorDecl(name, fields, t.line, t.col);
    }

    private Stmt parseAnnotationDecl() {
        advance(); // annotation keyword
        String name = consumeIdent("annotation name").lexeme;
        // Just register it; no body needed for now
        return new ExprStmt(new Identifier(name, 0, 0), 0, 0);
    }

    private Stmt parseActorDecl(String visibility) {
        Token t = advance();
        String name = consumeIdent("actor name").lexeme;
        expect(TokenType.LBRACE, "{");
        List<VarDecl> fields = new ArrayList<>();
        List<FuncDecl> methods = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            if (check(TokenType.VAR) || check(TokenType.LET)) {
                fields.add(parseVarDecl("private"));
            } else if (check(TokenType.FN)) {
                methods.add(parseFuncDecl("public", false, false, false));
            }
        }
        expect(TokenType.RBRACE, "}");
        // Treat actor as a class for compilation purposes
       return new ActorDecl(
        name,
        fields,
        methods,
        visibility,
        t.line,
        t.col
);
    }

    // ============= Statements =============

    private Stmt parseStatement() {
        if (check(TokenType.LET) || check(TokenType.VAR)) return parseVarDecl("internal");
        if (check(TokenType.IF)) return parseIfStmt();
        if (check(TokenType.FOR)) return parseForStmt();
        if (check(TokenType.WHILE)) return parseWhileStmt();
        if (check(TokenType.RETURN)) return parseReturnStmt();
        if (check(TokenType.DEFER)) return parseDeferStmt();
        if (check(TokenType.TRY)) return parseTryCatchStmt();
        if (check(TokenType.THROW)) return parseThrowStmt();
        if (check(TokenType.FN)) return parseFuncDecl("internal", false, false, false);
        if (check(TokenType.PRINT)) return parsePrintStmt();
        if (check(TokenType.ASYNC)) {
            advance();
            if (check(TokenType.FN)) return parseFuncDecl("internal", false, false, true);
        }
        return parseExprOrAssignStmt();
    }

    private Stmt parseIfStmt() {
        Token t = advance();
        
        // Check for optional binding: if let varName
        if (check(TokenType.LET)) {
            advance();
            String varName = consumeIdent("variable name").lexeme;
            expect(TokenType.EQ, "=");
            Expr optionalExpr = parseExpr();
            expect(TokenType.LBRACE, "{");
            List<Stmt> thenBody = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) thenBody.add(parseStatement());
            expect(TokenType.RBRACE, "}");
            
            List<Stmt> elseBody = null;
            if (match(TokenType.ELSE)) {
                if (check(TokenType.IF)) {
                    elseBody = new ArrayList<>();
                    elseBody.add(parseIfStmt());
                } else {
                    expect(TokenType.LBRACE, "{");
                    elseBody = new ArrayList<>();
                    while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) elseBody.add(parseStatement());
                    expect(TokenType.RBRACE, "}");
                }
            }
            
            return new IfLetStmt(varName, optionalExpr, thenBody, elseBody, t.line, t.col);
        }
        
        // Regular if statement
        Expr condition = parseExpr();
        expect(TokenType.LBRACE, "{");
        List<Stmt> thenBody = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) thenBody.add(parseStatement());
        expect(TokenType.RBRACE, "}");
        List<Stmt> elseBody = null;
        if (match(TokenType.ELSE)) {
            if (check(TokenType.IF)) {
                elseBody = new ArrayList<>();
                elseBody.add(parseIfStmt());
            } else {
                expect(TokenType.LBRACE, "{");
                elseBody = new ArrayList<>();
                while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) elseBody.add(parseStatement());
                expect(TokenType.RBRACE, "}");
            }
        }
        return new IfStmt(condition, thenBody, elseBody, t.line, t.col);
    }

    private Stmt parseForStmt() {
        Token t = advance();
        String varName = consumeIdent("loop variable").lexeme;
        expect(TokenType.IN, "in");
        Expr iterable = parseExpr();
        expect(TokenType.LBRACE, "{");
        List<Stmt> body = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) body.add(parseStatement());
        expect(TokenType.RBRACE, "}");
        return new ForStmt(varName, iterable, body, t.line, t.col);
    }

    private Stmt parseWhileStmt() {
        Token t = advance();
        Expr condition = parseExpr();
        expect(TokenType.LBRACE, "{");
        List<Stmt> body = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) body.add(parseStatement());
        expect(TokenType.RBRACE, "}");
        return new WhileStmt(condition, body, t.line, t.col);
    }

    private Stmt parseReturnStmt() {
        Token t = advance();
        Expr value = null;
        if (!check(TokenType.RBRACE) && !check(TokenType.EOF) && !check(TokenType.SEMICOLON)) {
            value = parseExpr();
        }
        return new ReturnStmt(value, t.line, t.col);
    }

    private Stmt parseDeferStmt() {
        Token t = advance();
        expect(TokenType.LBRACE, "{");
        List<Stmt> body = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) body.add(parseStatement());
        expect(TokenType.RBRACE, "}");
        return new DeferStmt(body, t.line, t.col);
    }

    private Stmt parseTryCatchStmt() {
        Token t = advance();
        expect(TokenType.LBRACE, "{");
        List<Stmt> tryBody = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) tryBody.add(parseStatement());
        expect(TokenType.RBRACE, "}");
        expect(TokenType.CATCH, "catch");
        String errorVar = "error";
        if (check(TokenType.IDENT) || check(TokenType.ERROR)) errorVar = advance().lexeme;
        expect(TokenType.LBRACE, "{");
        List<Stmt> catchBody = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) catchBody.add(parseStatement());
        expect(TokenType.RBRACE, "}");
        return new TryCatchStmt(tryBody, errorVar, catchBody, t.line, t.col);
    }

    private Stmt parseThrowStmt() {
        Token t = advance();
        Expr error = parseExpr();
        return new ThrowStmt(error, t.line, t.col);
    }

    private Stmt parsePrintStmt() {
        Token t = advance();
        expect(TokenType.LPAREN, "(");
        List<Expr> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do { args.add(parseExpr()); } while (match(TokenType.COMMA));
        }
        expect(TokenType.RPAREN, ")");
        return new ExprStmt(new CallExpr(new Identifier("print", t.line, t.col), args, null, t.line, t.col), t.line, t.col);
    }

    private Stmt parseExprOrAssignStmt() {
        Token t = peek();
        Expr expr = parseExpr();
        if (match(TokenType.EQ)) {
            Expr value = parseExpr();
            return new AssignStmt(expr, value, t.line, t.col);
        }
        return new ExprStmt(expr, t.line, t.col);
    }

    // ============= Expressions =============

    private Expr parseExpr() {
        return parsePipeline();
    }

    private Expr parsePipeline() {
        Expr left = parseOr();
        while (check(TokenType.PIPE_GT)) {
            Token t = advance();
            Expr right = parseOr();
            left = new PipelineExpr(left, right, t.line, t.col);
        }
        return left;
    }

    private Expr parseOr() {
        Expr left = parseAnd();
        while (check(TokenType.OR_OR)) {
            Token t = advance();
            Expr right = parseAnd();
            left = new BinaryExpr("||", left, right, t.line, t.col);
        }
        return left;
    }

    private Expr parseAnd() {
        Expr left = parseEquality();
        while (check(TokenType.AND_AND)) {
            Token t = advance();
            Expr right = parseEquality();
            left = new BinaryExpr("&&", left, right, t.line, t.col);
        }
        return left;
    }

    private Expr parseEquality() {
        Expr left = parseComparison();
        while (check(TokenType.EQ_EQ) || check(TokenType.BANG_EQ)) {
            Token t = advance();
            String op = t.type == TokenType.EQ_EQ ? "==" : "!=";
            Expr right = parseComparison();
            left = new BinaryExpr(op, left, right, t.line, t.col);
        }
        return left;
    }

    private Expr parseComparison() {
        Expr left = parseRange();
        while (check(TokenType.LT) || check(TokenType.GT) || check(TokenType.LT_EQ) || check(TokenType.GT_EQ)) {
            Token t = advance();
            String op = switch (t.type) {
                case LT -> "<"; case GT -> ">"; case LT_EQ -> "<="; case GT_EQ -> ">=";
                default -> throw new IllegalStateException();
            };
            Expr right = parseRange();
            left = new BinaryExpr(op, left, right, t.line, t.col);
        }
        return left;
    }

    private Expr parseRange() {
        Expr left = parseAdditive();
        if (check(TokenType.DOT_DOT) || check(TokenType.DOT_LT)) {
            Token t = advance();
            Expr right = parseAdditive();
            boolean exclusive = t.type == TokenType.DOT_LT;
            return new RangeExpr(left, right, exclusive, t.line, t.col);
        }
        return left;
    }

    private Expr parseAdditive() {
        Expr left = parseMultiplicative();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token t = advance();
            String op = t.type == TokenType.PLUS ? "+" : "-";
            Expr right = parseMultiplicative();
            left = new BinaryExpr(op, left, right, t.line, t.col);
        }
        return left;
    }

    private Expr parseMultiplicative() {
        Expr left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            Token t = advance();
            String op = switch (t.type) {
                case STAR -> "*"; case SLASH -> "/"; case PERCENT -> "%";
                default -> throw new IllegalStateException();
            };
            Expr right = parseUnary();
            left = new BinaryExpr(op, left, right, t.line, t.col);
        }
        return left;
    }

    private Expr parseUnary() {
        if (check(TokenType.BANG) || check(TokenType.MINUS)) {
            Token t = advance();
            String op = t.type == TokenType.BANG ? "!" : "-";
            Expr expr = parseUnary();
            return new UnaryExpr(op, expr, t.line, t.col);
        }
        if (check(TokenType.AWAIT)) {
            Token t = advance();
            Expr expr = parseUnary();
            return new AwaitExpr(expr, t.line, t.col);
        }
        return parsePostfix();
    }

    private Expr parsePostfix() {
        Expr expr = parsePrimary();
        while (true) {
            if (check(TokenType.DOT)) {
                advance();
                String member = consumeIdent("member name").lexeme;
                if (check(TokenType.QUESTION)) {
                    advance();
                    expr = new OptionalChainingExpr(expr, member, expr.line, expr.col);
                } else {
                    // Check if this might be an enum access (expr.variant where expr is an identifier)
                    if (expr instanceof Identifier id && declaredEnums.contains(id.name)) {
                        expr = new EnumAccessExpr(id.name, member, expr.line, expr.col);
                    } else {
                        expr = new MemberExpr(expr, member, expr.line, expr.col);
                    }
                }
            } else if (check(TokenType.LPAREN)) {
                advance();
                List<Expr> args = new ArrayList<>();
                List<String> argNames = null;
                if (!check(TokenType.RPAREN)) {
                    do {
                        // Check for named argument: ident: expr
                        if (check(TokenType.IDENT) && peekNext().type == TokenType.COLON) {
                            if (argNames == null) argNames = new ArrayList<>();
                            argNames.add(advance().lexeme);
                            advance(); // colon
                        } else if (argNames != null) {
                            argNames.add(null);
                        }
                        args.add(parseExpr());
                    } while (match(TokenType.COMMA));
                }
                expect(TokenType.RPAREN, ")");
                expr = new CallExpr(expr, args, argNames, expr.line, expr.col);
            } else if (check(TokenType.LBRACKET)) {
                advance();
                Expr index = parseExpr();
                expect(TokenType.RBRACKET, "]");
                expr = new IndexExpr(expr, index, expr.line, expr.col);
            } else if (check(TokenType.QUESTION_QUESTION)) {
                Token t = advance();
                Expr right = parseUnary();
                expr = new NullCoalescingExpr(expr, right, expr.line, expr.col);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr parsePrimary() {
        Token t = peek();
        if (match(TokenType.INT_LITERAL)) return new IntLiteral(Integer.parseInt(t.lexeme), t.line, t.col);
        if (match(TokenType.FLOAT_LITERAL)) return new FloatLiteral(Double.parseDouble(t.lexeme), t.line, t.col);
        if (match(TokenType.STRING_LITERAL)) return parseStringContent(t);
        if (match(TokenType.CHAR_LITERAL)) return new CharLiteral(t.lexeme.charAt(0), t.line, t.col);
        if (match(TokenType.TRUE)) return new BoolLiteral(true, t.line, t.col);
        if (match(TokenType.FALSE)) return new BoolLiteral(false, t.line, t.col);
        if (match(TokenType.NULL)) return new NullLiteral(t.line, t.col);
        if (match(TokenType.PRINT)) return new Identifier("print", t.line, t.col);
        if (match(TokenType.IF)) return parseIfExpr();
        if (match(TokenType.MATCH)) return parseMatchExpr();
        if (match(TokenType.LBRACKET)) return parseArrayLiteral();
        if (match(TokenType.LPAREN)) return parseParenOrTupleOrLambda();
        if (match(TokenType.LBRACE)) return parseLambda();
        if (check(TokenType.INT_TYPE) || check(TokenType.FLOAT_TYPE) || check(TokenType.BOOL_TYPE)
                || check(TokenType.STRING_TYPE) || check(TokenType.CHAR_TYPE) || check(TokenType.VOID_TYPE)
                || check(TokenType.ANY_TYPE) || check(TokenType.RESULT) || check(TokenType.OK)
                || check(TokenType.ERR) || check(TokenType.IDENT)) {
            advance();
            // Check for enum access: Status.Active
            if (check(TokenType.DOT) && (t.type == TokenType.IDENT)) {
                // Could be enum access or member access or constructor call
                // We handle it in postfix
                return new Identifier(t.lexeme, t.line, t.col);
            }
            return new Identifier(t.lexeme, t.line, t.col);
        }
        throw error(t, "Unexpected token: " + t.type + " ('" + t.lexeme + "')");
    }

    private Expr parseStringContent(Token t) {
        // Check if this is an interpolated string (followed by STRING_INTERP_START)
        if (check(TokenType.STRING_INTERP_START)) {
            List<Expr> parts = new ArrayList<>();
            // Add the initial string part (may be empty)
            if (!t.lexeme.isEmpty()) {
                parts.add(new StringLiteral(t.lexeme, t.line, t.col));
            }
            while (match(TokenType.STRING_INTERP_START)) {
                // Parse the interpolation expression
                Expr expr = parseExpr();
                expect(TokenType.STRING_INTERP_END, "}");
                parts.add(expr);
                // After INTERP_END, there should be another STRING_LITERAL
                if (check(TokenType.STRING_LITERAL)) {
                    Token next = advance();
                    if (!next.lexeme.isEmpty()) {
                        parts.add(new StringLiteral(next.lexeme, next.line, next.col));
                    }
                }
            }
            return new StringLiteral(parts, t.line, t.col);
        }
        return new StringLiteral(t.lexeme, t.line, t.col);
    }

    private Expr parseIfExpr() {
        Token t = peek();
        // already consumed IF
        Expr condition = parseExpr();
        expect(TokenType.LBRACE, "{");
        List<Expr> thenBody = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) thenBody.add(parseExpr());
        expect(TokenType.RBRACE, "}");
        List<Expr> elseBody = null;
        if (match(TokenType.ELSE)) {
            if (check(TokenType.IF)) {
                advance();
                elseBody = new ArrayList<>();
                elseBody.add(parseIfExpr());
            } else {
                expect(TokenType.LBRACE, "{");
                elseBody = new ArrayList<>();
                while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) elseBody.add(parseExpr());
                expect(TokenType.RBRACE, "}");
            }
        }
        return new IfExpr(condition, thenBody, elseBody, t.line, t.col);
    }

    private Expr parseMatchExpr() {
        Token t = peek();
        // already consumed MATCH
        Expr subject = parseExpr();
        expect(TokenType.LBRACE, "{");
        List<MatchArm> arms = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            Expr pattern;
            if (match(TokenType.BANG)) {
                pattern = new Identifier("_", t.line, t.col);
            } else if (check(TokenType.IDENT)) {
                Token id = peek();
                if (id.lexeme.equals("_")) {
                    advance();
                    pattern = new Identifier("_", id.line, id.col);
                } else if (peekNext().type == TokenType.FAT_ARROW) {
                    advance();
                    pattern = new Identifier(id.lexeme, id.line, id.col);
                } else {
                    pattern = parseExpr();
                }
            } else {
                pattern = parseExpr();
            }
            expect(TokenType.FAT_ARROW, "=>");
            List<Expr> body = new ArrayList<>();
            if (check(TokenType.LBRACE)) {
                advance();
                while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) body.add(parseExpr());
                expect(TokenType.RBRACE, "}");
            } else {
                body.add(parseExpr());
            }
            arms.add(new MatchArm(pattern, body));
        }
        expect(TokenType.RBRACE, "}");
        return new MatchExpr(subject, arms, t.line, t.col);
    }

    private Expr parseArrayLiteral() {
        Token t = peek();
        List<Expr> elements = new ArrayList<>();
        if (!check(TokenType.RBRACKET)) {
            do { elements.add(parseExpr()); } while (match(TokenType.COMMA));
        }
        expect(TokenType.RBRACKET, "]");
        return new ArrayLiteral(elements, t.line, t.col);
    }

    private Expr parseParenOrTupleOrLambda() {
        // Could be: (expr), (tuple), (named tuple), or lambda params for { a, b -> ... }
        // Since we already consumed LPAREN, we need to handle these
        Token t = peek();
        // Check for empty tuple
        if (match(TokenType.RPAREN)) {
            return new TupleExpr(new ArrayList<>(), new ArrayList<>(), t.line, t.col);
        }
        // Parse first expr
        // Check if it's a named tuple element: ident : expr
        List<String> names = new ArrayList<>();
        List<Expr> elements = new ArrayList<>();
        boolean isTuple = false;
        boolean isNamed = false;
        if (check(TokenType.IDENT) && peekNext().type == TokenType.COLON) {
            names.add(advance().lexeme);
            advance(); // colon
            elements.add(parseExpr());
            isNamed = true;
            isTuple = true;
        } else {
            Expr first = parseExpr();
            elements.add(first);
        }
        while (match(TokenType.COMMA)) {
            isTuple = true;
            if (check(TokenType.IDENT) && peekNext().type == TokenType.COLON) {
                names.add(advance().lexeme);
                advance();
                elements.add(parseExpr());
                isNamed = true;
            } else {
                if (!isNamed) names.add(null);
                elements.add(parseExpr());
            }
        }
        expect(TokenType.RPAREN, ")");
        if (isTuple) {
            if (!isNamed) names = new ArrayList<>();
            return new TupleExpr(names, elements, t.line, t.col);
        }
        // Single element in parens: just the expression
        return elements.get(0);
    }

    private Expr parseLambda() {
        Token t = peek();
        // Already consumed LBRACE
        // Check for params: { x -> ... } or { a, b -> ... }
        // or { x: Type -> ... } (no, Vega uses untyped lambda params)
        List<String> params = new ArrayList<>();
        // Look ahead to find -> before }
        int savedPos = pos;
        if (check(TokenType.IDENT)) {
            params.add(advance().lexeme);
            while (match(TokenType.COMMA)) {
                params.add(consumeIdent("lambda param").lexeme);
            }
            if (match(TokenType.ARROW)) {
                // It's a lambda with params
                List<Expr> body = new ArrayList<>();
                while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) body.add(parseExpr());
                expect(TokenType.RBRACE, "}");
                return new LambdaExpr(params, body, t.line, t.col);
            }
            // Not a lambda; restore
            pos = savedPos;
        }
        // Lambda with no params: { -> ... }
        if (match(TokenType.ARROW)) {
            List<Expr> body = new ArrayList<>();
            while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) body.add(parseExpr());
            expect(TokenType.RBRACE, "}");
            return new LambdaExpr(new ArrayList<>(), body, t.line, t.col);
        }
        // Just a block expression
        List<Expr> body = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) body.add(parseExpr());
        expect(TokenType.RBRACE, "}");
        // Return last expr as a block
        if (body.size() == 1) return body.get(0);
        // Multiple expressions as a block - return last
        return body.isEmpty() ? new NullLiteral(t.line, t.col) : body.get(body.size() - 1);
    }

    // ============= Types =============

    private VegaTypeRef parseTypeRef() {
        Token t = peek();
        String name;
        if (check(TokenType.IDENT) || check(TokenType.INT_TYPE) || check(TokenType.FLOAT_TYPE)
                || check(TokenType.BOOL_TYPE) || check(TokenType.STRING_TYPE) || check(TokenType.CHAR_TYPE)
                || check(TokenType.VOID_TYPE) || check(TokenType.ANY_TYPE) || check(TokenType.RESULT)) {
            name = advance().lexeme;
        } else {
            throw error(t, "Expected type name, got " + t.type);
        }
        List<VegaTypeRef> genericArgs = new ArrayList<>();
        if (match(TokenType.LT)) {
            do { genericArgs.add(parseTypeRef()); } while (match(TokenType.COMMA));
            expect(TokenType.GT, ">");
        }
        boolean isOptional = false;
        if (match(TokenType.QUESTION)) isOptional = true;
        // Array type [T] is represented as Array<T>
        return new VegaTypeRef(name, genericArgs, isOptional, t.line, t.col);
    }

    // ============= Helpers =============

    private com.vega.compiler.types.VegaType resolveType(VegaTypeRef ref) {
        // Defer to a simple resolver
        return com.vega.compiler.types.VegaType.fromRef(ref);
    }

    private boolean check(TokenType type) {
        return !isAtEnd() && peek().type == type;
    }

    private boolean checkToken(TokenType type, java.util.function.Predicate<String> pred) {
        return check(type) && pred.test(peek().lexeme);
    }

    private boolean match(TokenType type) {
        if (check(type)) { advance(); return true; }
        return false;
    }

    private Token advance() {
        if (!isAtEnd()) pos++;
        return tokens.get(pos - 1);
    }

    private boolean isAtEnd() { return peek().type == TokenType.EOF; }
    private Token peek() { return tokens.get(pos); }
    private Token peekNext() { return pos + 1 >= tokens.size() ? tokens.get(tokens.size() - 1) : tokens.get(pos + 1); }

    private Token expect(TokenType type, String display) {
        if (check(type)) return advance();
        throw error(peek(), "Expected '" + display + "' but got " + peek().type + " ('" + peek().lexeme + "') at line " + peek().line);
    }

    private Token consumeIdent(String what) {
        if (check(TokenType.IDENT)) return advance();
        // Also accept type keywords as identifiers in some contexts
        if (check(TokenType.INT_TYPE) || check(TokenType.FLOAT_TYPE) || check(TokenType.BOOL_TYPE)
                || check(TokenType.STRING_TYPE) || check(TokenType.CHAR_TYPE) || check(TokenType.VOID_TYPE)
                || check(TokenType.ANY_TYPE) || check(TokenType.RESULT) || check(TokenType.OK)
                || check(TokenType.ERR) || check(TokenType.PRINT)) {
            return advance();
        }
        throw error(peek(), "Expected " + what + " but got " + peek().type + " ('" + peek().lexeme + "')");
    }

    private ParseException error(Token t, String message) {
        return new ParseException(message + " (line " + t.line + ":" + t.col + ")");
    }

    public static class ParseException extends RuntimeException {
        public ParseException(String message) { super(message); }
    }
}
