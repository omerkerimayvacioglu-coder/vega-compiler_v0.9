package com.vega.compiler.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int col = 1;

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
        Map.entry("let", TokenType.LET),
        Map.entry("var", TokenType.VAR),
        Map.entry("fn", TokenType.FN),
        Map.entry("struct", TokenType.STRUCT),
        Map.entry("class", TokenType.CLASS),
        Map.entry("enum", TokenType.ENUM),
        Map.entry("protocol", TokenType.PROTOCOL),
        Map.entry("actor", TokenType.ACTOR),
        Map.entry("extension", TokenType.EXTENSION),
        Map.entry("if", TokenType.IF),
        Map.entry("else", TokenType.ELSE),
        Map.entry("match", TokenType.MATCH),
        Map.entry("for", TokenType.FOR),
        Map.entry("while", TokenType.WHILE),
        Map.entry("in", TokenType.IN),
        Map.entry("return", TokenType.RETURN),
        Map.entry("defer", TokenType.DEFER),
        Map.entry("public", TokenType.PUBLIC),
        Map.entry("private", TokenType.PRIVATE),
        Map.entry("protected", TokenType.PROTECTED),
        Map.entry("internal", TokenType.INTERNAL),
        Map.entry("import", TokenType.IMPORT),
        Map.entry("package", TokenType.PACKAGE),
        Map.entry("as", TokenType.AS),
        Map.entry("module", TokenType.MODULE),
        Map.entry("init", TokenType.INIT),
        Map.entry("override", TokenType.OVERRIDE),
        Map.entry("operator", TokenType.OPERATOR),
        Map.entry("async", TokenType.ASYNC),
        Map.entry("await", TokenType.AWAIT),
        Map.entry("spawn", TokenType.SPAWN),
        Map.entry("throw", TokenType.THROW),
        Map.entry("throws", TokenType.THROWS),
        Map.entry("try", TokenType.TRY),
        Map.entry("catch", TokenType.CATCH),
        Map.entry("annotation", TokenType.ANNOTATION),
        Map.entry("error", TokenType.ERROR),
        Map.entry("where", TokenType.WHERE),
        Map.entry("implements", TokenType.IMPLEMENTS),
        Map.entry("extends", TokenType.EXTENDS),
        Map.entry("true", TokenType.TRUE),
        Map.entry("false", TokenType.FALSE),
        Map.entry("null", TokenType.NULL),
        // Built-in types
        Map.entry("Int", TokenType.INT_TYPE),
        Map.entry("Float", TokenType.FLOAT_TYPE),
        Map.entry("Bool", TokenType.BOOL_TYPE),
        Map.entry("String", TokenType.STRING_TYPE),
        Map.entry("Char", TokenType.CHAR_TYPE),
        Map.entry("Void", TokenType.VOID_TYPE),
        Map.entry("Any", TokenType.ANY_TYPE),
        Map.entry("Result", TokenType.RESULT),
        Map.entry("Ok", TokenType.OK),
        Map.entry("Err", TokenType.ERR),
        Map.entry("print", TokenType.PRINT)
    );

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", line, col));
        return tokens;
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    private char peekNext() {
        return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
    }

    private char advance() {
        char c = source.charAt(current++);
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        current++;
        col++;
        return true;
    }

    private void scanToken() {
        start = current;
        char c = peek();
        if (Character.isWhitespace(c)) {
            advance();
            return;
        }
        if (c == '/' && peekNext() == '/') {
            while (!isAtEnd() && peek() != '\n') advance();
            return;
        }
        if (c == '/' && peekNext() == '*') {
            advance();
            advance();
            while (!isAtEnd() && !(peek() == '*' && peekNext() == '/')) advance();
            if (!isAtEnd()) { advance(); advance(); }
            return;
        }

        if (Character.isDigit(c)) {
            scanNumber();
            return;
        }
        if (Character.isLetter(c) || c == '_') {
            scanIdentifier();
            return;
        }
        if (c == '"') {
            scanString();
            return;
        }
        if (c == '\'') {
            scanChar();
            return;
        }
        if (c == '@') {
            advance();
            if (Character.isLetter(peek()) || peek() == '_') {
                start = current;
                while (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '.') advance();
                addToken(TokenType.IDENT, "@" + source.substring(start, current));
                return;
            }
            error("Expected annotation name after '@'");
            return;
        }

        scanOperator();
    }

    private void scanNumber() {
        while (Character.isDigit(peek())) advance();
        if (peek() == '.' && Character.isDigit(peekNext())) {
            advance();
            while (Character.isDigit(peek())) advance();
            addToken(TokenType.FLOAT_LITERAL);
        } else {
            addToken(TokenType.INT_LITERAL);
        }
    }

    private void scanIdentifier() {
        while (Character.isLetterOrDigit(peek()) || peek() == '_') advance();
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.get(text);
        if (type == null) {
            addToken(TokenType.IDENT);
        } else {
            addToken(type);
        }
    }

    private void scanString() {
        advance(); // opening "
        scanStringContent();
    }

    private void scanStringContent() {
        StringBuilder sb = new StringBuilder();
        boolean hasInterpolation = false;
        while (!isAtEnd() && peek() != '"') {
            char ch = peek();
            if (ch == '\\') {
                advance();
                char esc = advance();
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    case '\\' -> sb.append('\\');
                    case '0' -> sb.append('\0');
                    case '{' -> sb.append('{');
                    case '}' -> sb.append('}');
                    default -> { sb.append('\\'); sb.append(esc); }
                }
            } else if (ch == '{') {
                // String interpolation
                hasInterpolation = true;
                addToken(TokenType.STRING_LITERAL, sb.toString());
                sb = new StringBuilder();
                advance(); // consume {
                addToken(TokenType.STRING_INTERP_START, "{");
                // Scan expression tokens until matching }
                int braceDepth = 1;
                while (!isAtEnd() && braceDepth > 0) {
                    char ec = peek();
                    if (ec == '{') { braceDepth++; scanToken(); }
                    else if (ec == '}') {
                        braceDepth--;
                        if (braceDepth == 0) {
                            advance();
                            addToken(TokenType.STRING_INTERP_END, "}");
                        } else {
                            scanToken();
                        }
                    } else if (ec == '"') {
                        // Nested string inside interpolation
                        scanString();
                    } else {
                        scanToken();
                    }
                }
            } else {
                sb.append(advance());
            }
        }
        if (isAtEnd()) {
            error("Unterminated string");
            return;
        }
        advance(); // closing "
        addToken(TokenType.STRING_LITERAL, sb.toString());
    }

    private void scanChar() {
        advance(); // opening '
        StringBuilder sb = new StringBuilder();
        if (peek() == '\\') {
            advance();
            char esc = advance();
            switch (esc) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case '"' -> sb.append('"');
                case '\'' -> sb.append('\'');
                case '\\' -> sb.append('\\');
                case '0' -> sb.append('\0');
                default -> { sb.append('\\'); sb.append(esc); }
            }
        } else {
            sb.append(advance());
        }
        if (peek() != '\'') {
            error("Unterminated char literal");
            return;
        }
        advance(); // closing '
        addToken(TokenType.CHAR_LITERAL, sb.toString());
    }

    private void scanOperator() {
        char c = advance();
        switch (c) {
            case '(' -> addToken(TokenType.LPAREN);
            case ')' -> addToken(TokenType.RPAREN);
            case '{' -> addToken(TokenType.LBRACE);
            case '}' -> addToken(TokenType.RBRACE);
            case '[' -> addToken(TokenType.LBRACKET);
            case ']' -> addToken(TokenType.RBRACKET);
            case ',' -> addToken(TokenType.COMMA);
            case ':' -> addToken(TokenType.COLON);
            case ';' -> addToken(TokenType.SEMICOLON);
            case '.' -> {
                if (peek() == '.') { advance(); addToken(TokenType.DOT_DOT); }
                else if (peek() == '<') { advance(); addToken(TokenType.DOT_LT); }
                else addToken(TokenType.DOT);
            }
            case '+' -> addToken(TokenType.PLUS);
            case '-' -> {
                if (peek() == '>') { advance(); addToken(TokenType.ARROW); }
                else addToken(TokenType.MINUS);
            }
            case '*' -> addToken(TokenType.STAR);
            case '/' -> addToken(TokenType.SLASH);
            case '%' -> addToken(TokenType.PERCENT);
            case '=' -> {
                if (peek() == '=') { advance(); addToken(TokenType.EQ_EQ); }
                else if (peek() == '>') { advance(); addToken(TokenType.FAT_ARROW); }
                else addToken(TokenType.EQ);
            }
            case '!' -> {
                if (peek() == '=') { advance(); addToken(TokenType.BANG_EQ); }
                else addToken(TokenType.BANG);
            }
            case '<' -> {
                if (peek() == '=') { advance(); addToken(TokenType.LT_EQ); }
                else addToken(TokenType.LT);
            }
            case '>' -> {
                if (peek() == '=') { advance(); addToken(TokenType.GT_EQ); }
                else addToken(TokenType.GT);
            }
            case '&' -> {
                if (peek() == '&') { advance(); addToken(TokenType.AND_AND); }
                else error("Bitwise & not supported in Vega 0.1");
            }
            case '|' -> {
                if (peek() == '>') { advance(); addToken(TokenType.PIPE_GT); }
                else error("Bitwise | not supported in Vega 0.1");
            }
            case '?' -> {
                if (peek() == '?') { advance(); addToken(TokenType.QUESTION_QUESTION); }
                else addToken(TokenType.QUESTION);
            }
            default -> error("Unexpected character: '" + c + "'");
        }
    }

    private void addToken(TokenType type) {
        addToken(type, source.substring(start, current));
    }

    private void addToken(TokenType type, String lexeme) {
        tokens.add(new Token(type, lexeme, line, col));
    }

    private void error(String message) {
        throw new LexException(message + " at line " + line + ":" + col);
    }

    public static class LexException extends RuntimeException {
        public LexException(String message) { super(message); }
    }
}
