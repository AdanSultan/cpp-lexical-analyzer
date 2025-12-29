import java.io.*;
import java.util.*;

public class Lexer {
    enum TokenType {
        KEYWORD, IDENTIFIER, OPERATOR, LITERAL, SEPARATOR, CONSTANT, ERROR, UNKNOWN
    }

    static class LexicalError {
        String token;
        String message;
        int line;
        int column;

        LexicalError(String token, String message, int line, int column) {
            this.token = token;
            this.message = message;
            this.line = line;
            this.column = column;
        }

        @Override
        public String toString() {
            return "Lexical Error at line " + line + ", column " + column + ": " + message + " [" + token + "]";
        }
    }

    static class SyntaxError {
        String message;
        int line;
        int column;

        SyntaxError(String message, int line, int column) {
            this.message = message;
            this.line = line;
            this.column = column;
        }

        @Override
        public String toString() {
            return "Syntax Error at line " + line + ", column " + column + ": " + message;
        }
    }

    static class TokenInfo {
        String token;
        int line;
        int column;
        TokenInfo(String token, int line, int column) {
            this.token = token;
            this.line = line;
            this.column = column;
        }
    }

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "int", "float", "double", "char", "if", "else", "while", "for", "return", 
            "do", "string", "main", "cout", "endl", "std", "cin", "include", "iostream"));
    
    private static final Set<String> OPERATORS = new HashSet<>(Arrays.asList(
            "+", "-", "*", "/", "=", "<", ">", "!", "==", "!=", "<=", ">=", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "&&", "||", "%", "<<", ">>", "::", "&", "|"));
    
    private static final Map<Character, Set<Character>> COMPOUND_OPS = new HashMap<>();
    static {
        COMPOUND_OPS.put('+', new HashSet<>(Arrays.asList('+', '=')));
        COMPOUND_OPS.put('-', new HashSet<>(Arrays.asList('-', '=')));
        COMPOUND_OPS.put('*', new HashSet<>(Arrays.asList('=')));
        COMPOUND_OPS.put('/', new HashSet<>(Arrays.asList('=')));
        COMPOUND_OPS.put('%', new HashSet<>(Arrays.asList('=')));
        COMPOUND_OPS.put('=', new HashSet<>(Arrays.asList('=')));
        COMPOUND_OPS.put('!', new HashSet<>(Arrays.asList('=')));
        COMPOUND_OPS.put('<', new HashSet<>(Arrays.asList('=', '<')));
        COMPOUND_OPS.put('>', new HashSet<>(Arrays.asList('=', '>')));
        COMPOUND_OPS.put('&', new HashSet<>(Arrays.asList('&')));
        COMPOUND_OPS.put('|', new HashSet<>(Arrays.asList('|')));
        COMPOUND_OPS.put(':', new HashSet<>(Arrays.asList(':')));
    }
    
    private static final Set<Character> SEPARATORS = new HashSet<>(Arrays.asList(
            '(', ')', '{', '}', '[', ']', ';', ',', '#', '<', '>'));
    
    static final Map<String, Integer> TYPE_SIZES = new HashMap<>();
    static {
        TYPE_SIZES.put("int", 4);
        TYPE_SIZES.put("float", 4);
        TYPE_SIZES.put("double", 8);
        TYPE_SIZES.put("char", 1);
        TYPE_SIZES.put("string", 0);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide the path to the input file.");
            return;
        }

        String filePath = args[0];
        List<TokenInfo> tokens = new ArrayList<>();
        List<LexicalError> lexicalErrors = new ArrayList<>();
        List<SyntaxError> syntaxErrors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
            tokenizeWithErrors(fileContent.toString(), tokens, lexicalErrors);
            analyzeSyntax(tokens, syntaxErrors);
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return;
        }

        File outputFile = new File("f:/cpp-lexical-analyzer/output.txt");
        if (outputFile.exists()) outputFile.delete();

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("f:/cpp-lexical-analyzer/output.txt")))) {
            Map<TokenType, Map<String, Integer>> tokenCounts = new HashMap<>();
            Map<String, String[]> symbolTable = new HashMap<>();

            processTokensForSymbolTable(tokens, symbolTable);
            countTokens(tokens, tokenCounts);

            writer.println("TOKEN TABLE");
            writer.println("----------------------------------------");
            writer.println("Token Type      Lexeme         Count");
            writer.println("----------------------------------------");
            int totalTokens = printTokenTypeGroup(writer, tokenCounts, TokenType.OPERATOR) +
                            printTokenTypeGroup(writer, tokenCounts, TokenType.IDENTIFIER) +
                            printTokenTypeGroup(writer, tokenCounts, TokenType.KEYWORD) +
                            printTokenTypeGroup(writer, tokenCounts, TokenType.CONSTANT) +
                            printTokenTypeGroup(writer, tokenCounts, TokenType.LITERAL) +
                            printTokenTypeGroup(writer, tokenCounts, TokenType.SEPARATOR);
            writer.println("----------------------------------------");
            writer.println("Total Tokens: " + totalTokens);

            writer.println("\nSYMBOL TABLE");
            writer.println("----------------------------------------");
            writer.println("Name           Type           Size     Dimension    Line of Declaration  Line of Usage     Address ");
            writer.println("----------------------------------------");
            for (Map.Entry<String, String[]> entry : symbolTable.entrySet()) {
                String identifier = entry.getKey();
                String[] info = entry.getValue();
                String type = info[0];
                String line = info[2];
                int size = TYPE_SIZES.getOrDefault(type, 0);
                String lineOfUsage = line;
                String address = "0x" + Integer.toHexString(identifier.hashCode() & 0xFFFF);
                writer.printf("%-15s %-15s %-8d %-12d %-18s %-15s %-10s\n", identifier, type, size, 0, line, lineOfUsage, address);
            }

            if (!lexicalErrors.isEmpty()) {
                writer.println("\nLexical Errors Found (" + lexicalErrors.size() + "):");
                for (LexicalError error : lexicalErrors) {
                    writer.println(error);
                }
            }

            if (!syntaxErrors.isEmpty()) {
                writer.println("\nSyntax Errors Found (" + syntaxErrors.size() + "):");
                for (SyntaxError error : syntaxErrors) {
                    writer.println(error);
                }
            }

            if (lexicalErrors.isEmpty() && syntaxErrors.isEmpty()) {
                writer.println("No error found");
            }

            writer.flush();
            System.out.println("Analysis complete. Results written to output.txt");
        } catch (IOException e) {
            System.err.println("Error writing to output file: " + e.getMessage());
        }
    }

    static void tokenizeWithErrors(String input, List<TokenInfo> tokenInfos, List<LexicalError> errors) {
        StringBuilder currentToken = new StringBuilder();
        boolean inString = false, inCharLiteral = false, inSingleLineComment = false, 
                inMultiLineComment = false, inPreprocessor = false;
        int line = 1, column = 1, tokenStartLine = 1, tokenStartColumn = 1;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (ch == '\n') {
                line++;
                column = 1;
                if (inSingleLineComment) inSingleLineComment = false;
                if (inPreprocessor) inPreprocessor = false;
                if (currentToken.length() > 0) {
                    addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                    currentToken.setLength(0);
                }
                // Don't add newline as a token
                continue;
            } else {
                column++;
            }

            if (inMultiLineComment) {
                if (ch == '*' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
                    inMultiLineComment = false;
                    i++;
                    column++;
                }
                continue;
            }

            if (inSingleLineComment) continue;

            if (!inString && !inCharLiteral && !inSingleLineComment && !inMultiLineComment) {
                if (ch == '#' && (i == 0 || input.charAt(i - 1) == '\n')) {
                    inPreprocessor = true;
                    if (currentToken.length() > 0) {
                        addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                        currentToken.setLength(0);
                    }
                    tokenInfos.add(new TokenInfo("#", line, column));
                    continue;
                }
                if (inPreprocessor) continue;
            }

            if (!inString && !inCharLiteral && !inPreprocessor) {
                if (ch == '/' && i + 1 < input.length()) {
                    if (input.charAt(i + 1) == '*') {
                        if (currentToken.length() > 0) {
                            addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                            currentToken.setLength(0);
                        }
                        inMultiLineComment = true;
                        i++;
                        column++;
                        continue;
                    } else if (input.charAt(i + 1) == '/') {
                        if (currentToken.length() > 0) {
                            addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                            currentToken.setLength(0);
                        }
                        inSingleLineComment = true;
                        i++;
                        column++;
                        continue;
                    }
                }
            }

            if (ch == '"' && !inCharLiteral) {
                if (!inString) {
                    if (currentToken.length() > 0) {
                        addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                        currentToken.setLength(0);
                    }
                    tokenStartLine = line;
                    tokenStartColumn = column;
                    inString = true;
                    currentToken.append(ch);
                } else {
                    currentToken.append(ch);
                    addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                    currentToken.setLength(0);
                    inString = false;
                }
                continue;
            }

            if (ch == '\'' && !inString) {
                if (!inCharLiteral) {
                    if (currentToken.length() > 0) {
                        addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                        currentToken.setLength(0);
                    }
                    tokenStartLine = line;
                    tokenStartColumn = column;
                    inCharLiteral = true;
                    currentToken.append(ch);
                } else {
                    currentToken.append(ch);
                    addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                    currentToken.setLength(0);
                    inCharLiteral = false;
                }
                continue;
            }

            if ((inString || inCharLiteral) && ch == '\n') {
                String type = inString ? "string" : "character";
                errors.add(new LexicalError(currentToken.toString(), "Unterminated " + type + " literal", tokenStartLine, tokenStartColumn));
                currentToken.setLength(0);
                inString = inCharLiteral = false;
                continue;
            }

            if (inString || inCharLiteral) {
                currentToken.append(ch);
                continue;
            }

            if (Character.isWhitespace(ch)) {
                if (currentToken.length() > 0) {
                    addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                    currentToken.setLength(0);
                }
                continue;
            }

            if (COMPOUND_OPS.containsKey(ch) && i + 1 < input.length() && COMPOUND_OPS.get(ch).contains(input.charAt(i + 1))) {
                if (currentToken.length() > 0) {
                    addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                    currentToken.setLength(0);
                }
                tokenStartLine = line;
                tokenStartColumn = column;
                String compoundOp = String.valueOf(ch) + input.charAt(i + 1);
                tokenInfos.add(new TokenInfo(compoundOp, line, column));
                i++;
                column++;
            } else if (OPERATORS.contains(String.valueOf(ch)) || SEPARATORS.contains(ch)) {
                if (currentToken.length() > 0) {
                    addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
                    currentToken.setLength(0);
                }
                tokenStartLine = line;
                tokenStartColumn = column;
                tokenInfos.add(new TokenInfo(String.valueOf(ch), line, column));
            } else {
                if (currentToken.length() == 0) {
                    tokenStartLine = line;
                    tokenStartColumn = column;
                }
                currentToken.append(ch);
            }
        }

        if (inString) errors.add(new LexicalError(currentToken.toString(), "Unterminated string literal", tokenStartLine, tokenStartColumn));
        if (inCharLiteral) errors.add(new LexicalError(currentToken.toString(), "Unterminated character literal", tokenStartLine, tokenStartColumn));
        if (inMultiLineComment) errors.add(new LexicalError("/*", "Unterminated multi-line comment", tokenStartLine, tokenStartColumn));
        if (currentToken.length() > 0) addTokenWithValidation(currentToken.toString(), tokenInfos, errors, tokenStartLine, tokenStartColumn);
    }

    private static void addTokenWithValidation(String token, List<TokenInfo> tokenInfos, List<LexicalError> errors, int line, int column) {
        if (token.isEmpty()) return;
        TokenType type = getTokenType(token);
        if (type == TokenType.ERROR) {
            errors.add(new LexicalError(token, "Invalid token", line, column));
        } else if (type == TokenType.IDENTIFIER && !isValidIdentifier(token)) {
            errors.add(new LexicalError(token, "Invalid identifier", line, column));
        } else if (type == TokenType.LITERAL && isCharLiteral(token) && token.length() > 3) {
            // Check for char literals with multiple characters
            errors.add(new LexicalError(token, "Invalid char literal: char can only hold a single character", line, column));
        }
        tokenInfos.add(new TokenInfo(token, line, column));
    }

    private static boolean isValidIdentifier(String token) {
        if (token.isEmpty()) return false;
        if (!Character.isLetter(token.charAt(0)) && token.charAt(0) != '_') return false;
        for (int i = 1; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

    private static boolean isCharLiteral(String token) {
        return token.startsWith("'") && token.endsWith("'");
    }

    static TokenType getTokenType(String token) {
        if (KEYWORDS.contains(token)) return TokenType.KEYWORD;
        if (isValidIdentifier(token)) return TokenType.IDENTIFIER;
        if (token.matches("[0-9]+")) return TokenType.CONSTANT;
        if (token.matches("[0-9]*\\.[0-9]+")) return TokenType.LITERAL;
        if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) 
            return TokenType.LITERAL;
        if (OPERATORS.contains(token)) return TokenType.OPERATOR;
        if (SEPARATORS.contains(token.charAt(0)) && token.length() == 1) return TokenType.SEPARATOR;
        return TokenType.ERROR;
    }

    static void processTokensForSymbolTable(List<TokenInfo> tokens, Map<String, String[]> symbolTable) {
        String currentType = "Unknown";
        boolean isAfterEquals = false;
        String currentIdentifier = "";
        int currentLine = 1;

        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo tokenInfo = tokens.get(i);
            String token = tokenInfo.token;
            TokenType type = getTokenType(token);

            if (token.equals(";")) currentLine++;

            if (type == TokenType.KEYWORD && isDataType(token)) {
                currentType = token;
                continue;
            }

            if (type == TokenType.IDENTIFIER && !token.equals("main")) {
                currentIdentifier = token;
                if (!symbolTable.containsKey(token)) {
                    symbolTable.put(token, new String[]{currentType, "Not initialized", String.valueOf(currentLine)});
                }
                if (i + 1 < tokens.size() && tokens.get(i + 1).token.equals("=")) {
                    isAfterEquals = true;
                }
                continue;
            }

            if (isAfterEquals && (type == TokenType.CONSTANT || type == TokenType.LITERAL || type == TokenType.IDENTIFIER)) {
                if (!currentIdentifier.isEmpty() && symbolTable.containsKey(currentIdentifier)) {
                    symbolTable.get(currentIdentifier)[1] = token;
                }
                isAfterEquals = false;
            }
        }
    }

    private static void countTokens(List<TokenInfo> tokens, Map<TokenType, Map<String, Integer>> tokenCounts) {
        for (TokenInfo tokenInfo : tokens) {
            String token = tokenInfo.token;
            TokenType type = getTokenType(token);
            tokenCounts.computeIfAbsent(type, k -> new HashMap<>()).merge(token, 1, Integer::sum);
        }
    }

    private static int printTokenTypeGroup(PrintWriter writer, Map<TokenType, Map<String, Integer>> tokenCounts, TokenType type) {
        if (!tokenCounts.containsKey(type)) return 0;
        int count = 0;
        for (Map.Entry<String, Integer> entry : tokenCounts.get(type).entrySet()) {
            writer.printf("%-15s %-15s %-5d\n", type, entry.getKey(), entry.getValue());
            count += entry.getValue();
        }
        return count;
    }

    private static boolean isDataType(String keyword) {
        return KEYWORDS.contains(keyword) && TYPE_SIZES.containsKey(keyword);
    }

    static void analyzeSyntax(List<TokenInfo> tokens, List<SyntaxError> errors) {
        int line = 1;
        int column = 1;
        Stack<String> blockStack = new Stack<>();
        boolean inFunction = false;
        String lastType = null;
        String lastIdentifier = null;
        boolean expectingSemicolon = false;
        boolean hasMainFunction = false;
        boolean inDeclaration = false;
        int parenCount = 0;
        boolean inIfBlock = false;
        boolean inElseBlock = false;
        boolean inComment = false;
        boolean inString = false;
        boolean inChar = false;
        String currentFunction = null;
        boolean inMultiLineComment = false;
        Set<String> declaredVariables = new HashSet<>();
        Set<String> initializedVariables = new HashSet<>();
        boolean inIfStatement = false;
        boolean foundIfCondition = false;
        // --- Strict C++-compliant if-else matching logic ---
        class IfInfo { boolean matched; IfInfo() { matched = false; } }
        Stack<IfInfo> ifStack = new Stack<>();

        for (int i = 0; i < tokens.size(); i++) {
            TokenInfo tokenInfo = tokens.get(i);
            String token = tokenInfo.token;
            TokenType type = getTokenType(token);

            // --- Robust check for empty if condition (if () ...) ---
            if (token.equals("if")) {
                int idx = i + 1;
                // Skip newlines/whitespace
                while (idx < tokens.size() && tokens.get(idx).token.equals("\n")) idx++;
                if (idx < tokens.size() && tokens.get(idx).token.equals("(")) {
                    int idx2 = idx + 1;
                    while (idx2 < tokens.size() && tokens.get(idx2).token.equals("\n")) idx2++;
                    if (idx2 < tokens.size() && tokens.get(idx2).token.equals(")")) {
                        errors.add(new SyntaxError("Error: if statement without condition", tokenInfo.line, tokenInfo.column));
                    }
                }
            }

            // Track block depth
            if (token.equals("{")) {
                blockStack.push("{");
            } else if (token.equals("}")) {
                if (blockStack.isEmpty()) {
                    errors.add(new SyntaxError("Unmatched closing brace", tokenInfo.line, tokenInfo.column));
                } else {
                    blockStack.pop();
                }
                if (inIfBlock) inIfBlock = false;
                if (inElseBlock) inElseBlock = false;
            }

            // Check for if statement
            if (token.equals("if")) {
                inIfStatement = true;
                foundIfCondition = false;
                ifStack.push(new IfInfo());
            }

            // Check for else statement
            if (token.equals("else")) {
                // Allow else if (should not count as double else)
                boolean isElseIf = (i + 1 < tokens.size() && tokens.get(i + 1).token.equals("if"));
                // Find the nearest unmatched if
                int matchIdx = -1;
                for (int j = ifStack.size() - 1; j >= 0; j--) {
                    IfInfo info = ifStack.get(j);
                    if (!info.matched) {
                        matchIdx = j;
                        break;
                    }
                }
                if (matchIdx == -1) {
                    errors.add(new SyntaxError("Error: else without matching if", tokenInfo.line, tokenInfo.column));
                } else {
                    IfInfo matchedIf = ifStack.get(matchIdx);
                    if (matchedIf.matched) {
                        errors.add(new SyntaxError("Error: multiple else for one if", tokenInfo.line, tokenInfo.column));
                    } else {
                        matchedIf.matched = true;
                    }
                }
                // --- NEW LOGIC: Check for valid statement or block after else ---
                int next = i + 1;
                // Skip newlines
                while (next < tokens.size() && tokens.get(next).token.equals("\n")) next++;
                if (next < tokens.size()) {
                    String nextToken = tokens.get(next).token;
                    if (!nextToken.equals("{")) {
                        // Not a block, should be a statement ending with semicolon
                        int stmtEnd = next + 1;
                        boolean foundSemicolon = false;
                        while (stmtEnd < tokens.size()) {
                            if (tokens.get(stmtEnd).token.equals(";")) {
                                foundSemicolon = true;
                                break;
                            }
                            // If we hit a block or another control keyword, break
                            if (tokens.get(stmtEnd).token.equals("{") || tokens.get(stmtEnd).token.equals("if") || tokens.get(stmtEnd).token.equals("else")) break;
                            stmtEnd++;
                        }
                        if (!foundSemicolon) {
                            errors.add(new SyntaxError("Missing semicolon or invalid statement after else", tokens.get(next).line, tokens.get(next).column));
                        }
                    }
                }
            }

            // Reset if statement tracking when we find the closing brace
            if (inIfStatement && token.equals("}")) {
                inIfStatement = false;
                foundIfCondition = false;
            }

            // --- THIS BLOCK IS NOW THE FIRST LOGIC IN THE LOOP ---
            if (type == TokenType.KEYWORD && i > 0) {
                String prevToken = tokens.get(i - 1).token;
                TokenType prevType = getTokenType(prevToken);
                if (prevType == TokenType.KEYWORD) {
                    // Allow only 'int main' as consecutive keywords
                    if (!(prevToken.equals("int") && token.equals("main"))) {
                        errors.add(new SyntaxError("Invalid consecutive keywords: '" + prevToken + " " + token + "'. Only 'int main' is allowed as consecutive keywords.", tokenInfo.line, tokenInfo.column));
                    }
                }
            }

            // Track line and column for error reporting
            if (token.equals("\n")) {
                line++;
                column = 1;
                continue;
            }
            column += token.length();

            // Handle comments
            if (token.equals("//")) {
                inComment = true;
                continue;
            }
            if (inComment && token.equals("\n")) {
                inComment = false;
                continue;
            }
            if (token.equals("/*")) {
                inMultiLineComment = true;
                continue;
            }
            if (token.equals("*/")) {
                inMultiLineComment = false;
                continue;
            }
            if (inComment || inMultiLineComment) continue;

            // Handle block structure BEFORE skipping string/char
            if (token.equals("{")) {
                blockStack.push("{");
            } else if (token.equals("}")) {
                if (blockStack.isEmpty()) {
                    errors.add(new SyntaxError("Unmatched closing brace", tokenInfo.line, tokenInfo.column));
                } else {
                    blockStack.pop();
                    if (inIfBlock) inIfBlock = false;
                    if (inElseBlock) inElseBlock = false;
                }
            }

            // Handle string and char literals
            if (token.startsWith("\"")) inString = !inString;
            if (token.startsWith("'")) inChar = !inChar;
            if (inString || inChar) continue;

            // Special handling for main function
            if (token.equals("main") && i > 0 && tokens.get(i-1).token.equals("int")) {
                hasMainFunction = true;
                currentFunction = "main";
                // Skip validation for main function
                continue;
            }

            // Check for undeclared variables
            if (type == TokenType.IDENTIFIER && !token.equals("main")) {
                // Check if this is a variable declaration
                boolean isDeclaration = i > 0 && isDataType(tokens.get(i-1).token);
                if (!isDeclaration && !declaredVariables.contains(token)) {
                    errors.add(new SyntaxError("Variable '" + token + "' is not declared", tokenInfo.line, tokenInfo.column));
                }
            }

            // Check variable declarations
            if (isDataType(token)) {
                lastType = token;
                inDeclaration = true;
                if (i + 1 >= tokens.size()) {
                    errors.add(new SyntaxError("Incomplete variable declaration", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                String nextToken = tokens.get(i + 1).token;
                TokenInfo nextTokenInfo = tokens.get(i + 1);
                // Disallow 'main' as a variable name (unless it's a function declaration)
                boolean isFunctionDecl = nextToken.equals("main") && (i + 2 < tokens.size() && tokens.get(i + 2).token.equals("("));
                if (nextToken.equals("main") && !isFunctionDecl) {
                    errors.add(new SyntaxError("'main' is a reserved keyword and cannot be used as a variable name", nextTokenInfo.line, nextTokenInfo.column));
                } else if (!isValidIdentifier(nextToken) && !nextToken.equals("main")) {
                    errors.add(new SyntaxError("Invalid variable name: " + nextToken, nextTokenInfo.line, nextTokenInfo.column));
                }
                // Only check for missing semicolon if this is NOT a function declaration
                if (!isFunctionDecl) {
                    // Check for multiple declarations in the same scope
                    if (declaredVariables.contains(nextToken)) {
                        errors.add(new SyntaxError("Multiple declaration of variable '" + nextToken + "' in the same scope", nextTokenInfo.line, nextTokenInfo.column));
                    }
                    // Check for missing semicolon
                    int j = i + 2;
                    boolean foundSemicolon = false;
                    while (j < tokens.size() && !tokens.get(j).token.equals(";")) {
                        if (tokens.get(j).token.equals("\n")) break;
                        j++;
                    }
                    if (j >= tokens.size() || !tokens.get(j).token.equals(";")) {
                        // Report error at the end of the declaration line
                        int errorLine = nextTokenInfo.line;
                        int errorCol = nextTokenInfo.column + nextToken.length();
                        errors.add(new SyntaxError("Missing semicolon in variable declaration", errorLine, errorCol));
                    }
                }
                continue;
            }

            // Check variable initialization
            if (inDeclaration && type == TokenType.IDENTIFIER) {
                lastIdentifier = token;
                declaredVariables.add(token);
                if (i + 1 < tokens.size()) {
                    String nextToken = tokens.get(i + 1).token;
                    TokenInfo nextTokenInfo = tokens.get(i + 1);
                    if (nextToken.equals("=")) {
                        // Check if variable is already initialized
                        if (initializedVariables.contains(token)) {
                            errors.add(new SyntaxError("Variable '" + token + "' is already initialized", nextTokenInfo.line, nextTokenInfo.column));
                        } else {
                            initializedVariables.add(token);
                        }
                        if (i + 2 >= tokens.size() || getTokenType(tokens.get(i + 2).token) == TokenType.SEPARATOR) {
                            errors.add(new SyntaxError("Initialization without a value", nextTokenInfo.line, nextTokenInfo.column));
                        } else {
                            // Check for char type initialization
                            if (lastType != null && lastType.equals("char")) {
                                String valueToken = tokens.get(i + 2).token;
                                if (isCharLiteral(valueToken) && valueToken.length() > 3) {
                                    errors.add(new SyntaxError("Invalid char initialization: char can only hold a single character", nextTokenInfo.line, nextTokenInfo.column));
                                }
                            }
                            // Check for string type initialization
                            if (lastType != null && lastType.equals("string")) {
                                String valueToken = tokens.get(i + 2).token;
                                if (isCharLiteral(valueToken)) {
                                    errors.add(new SyntaxError("String values must be enclosed in double quotes (\"...\") in C++.", nextTokenInfo.line, nextTokenInfo.column));
                                }
                            }
                        }
                    }
                }
                inDeclaration = false;
                continue;
            }

            // Enhanced assignment statement checks
            if (token.equals("=") && i > 0) {
                String lhs = tokens.get(i - 1).token;
                TokenType lhsType = getTokenType(lhs);
                // 1. LHS must be a valid identifier, not a keyword, constant, or function name
                if (lhsType != TokenType.IDENTIFIER || lhs.equals("main") || KEYWORDS.contains(lhs)) {
                    errors.add(new SyntaxError("Invalid assignment target: '" + lhs + "'", tokenInfo.line, tokenInfo.column - 1));
                } else if (!declaredVariables.contains(lhs)) {
                    errors.add(new SyntaxError("Assignment without prior declaration: " + lhs, tokenInfo.line, tokenInfo.column - 1));
                }
                // 2. RHS must exist and be a valid value
                if (i + 1 >= tokens.size()) {
                    errors.add(new SyntaxError("Incomplete assignment statement", tokenInfo.line, tokenInfo.column));
                } else {
                    String rhs = tokens.get(i + 1).token;
                    TokenType rhsType = getTokenType(rhs);
                    if (!(rhsType == TokenType.IDENTIFIER || rhsType == TokenType.CONSTANT || rhsType == TokenType.LITERAL)) {
                        errors.add(new SyntaxError("Invalid assignment value: '" + rhs + "'", tokens.get(i + 1).line, tokens.get(i + 1).column));
                    }
                }
                // 3. Check for missing semicolon after assignment
                int j = i + 2;
                boolean foundSemicolon = false;
                while (j < tokens.size() && !tokens.get(j).token.equals(";")) {
                    if (tokens.get(j).token.equals("\n")) break;
                    j++;
                }
                if (j >= tokens.size() || !tokens.get(j).token.equals(";")) {
                    int errorLine = tokenInfo.line;
                    int errorCol = tokenInfo.column + 1;
                    errors.add(new SyntaxError("Missing semicolon after assignment statement", errorLine, errorCol));
                }
                continue;
            }

            // Flag 'if-else' as invalid
            if (token.equals("if-else")) {
                errors.add(new SyntaxError("'if-else' is not a valid statement. Use 'if (...) { ... } else { ... }' instead.", tokenInfo.line, tokenInfo.column));
                continue;
            }

            // Enhanced if statement checks
            if (token.equals("if")) {
                inIfBlock = true;
                // Check for opening parenthesis
                if (i + 1 >= tokens.size() || !tokens.get(i + 1).token.equals("(")) {
                    errors.add(new SyntaxError("if condition without proper parentheses", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                // Find matching closing parenthesis
                int parenDepth = 1;
                int j = i + 2;
                boolean foundClosingParen = false;
                while (j < tokens.size()) {
                    String t = tokens.get(j).token;
                    if (t.equals("(")) parenDepth++;
                    else if (t.equals(")")) parenDepth--;
                    if (parenDepth == 0) {
                        foundClosingParen = true;
                        break;
                    }
                    j++;
                }
                if (!foundClosingParen) {
                    errors.add(new SyntaxError("Missing closing parenthesis in if condition", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                // Check for opening brace after condition
                int k = j + 1;
                while (k < tokens.size() && tokens.get(k).token.equals("\n")) k++; // skip newlines
                if (k >= tokens.size() || !tokens.get(k).token.equals("{")) {
                    errors.add(new SyntaxError("Missing opening brace after if condition", tokenInfo.line, tokenInfo.column));
                }
                continue;
            }

            // Enhanced for loop checks
            if (token.equals("for")) {
                // Check for opening parenthesis
                if (i + 1 >= tokens.size() || !tokens.get(i + 1).token.equals("(")) {
                    errors.add(new SyntaxError("for loop missing opening parenthesis '('", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                // Find matching closing parenthesis and check for semicolons
                int parenDepth = 1;
                int j = i + 2;
                int semicolonCount = 0;
                boolean foundClosingParen = false;
                boolean foundFirstSemicolon = false, foundSecondSemicolon = false;
                while (j < tokens.size()) {
                    String t = tokens.get(j).token;
                    if (t.equals("(")) parenDepth++;
                    else if (t.equals(")")) parenDepth--;
                    if (t.equals(";")) {
                        if (!foundFirstSemicolon) foundFirstSemicolon = true;
                        else if (!foundSecondSemicolon) foundSecondSemicolon = true;
                        semicolonCount++;
                    }
                    if (parenDepth == 0) {
                        foundClosingParen = true;
                        break;
                    }
                    j++;
                }
                if (!foundClosingParen) {
                    errors.add(new SyntaxError("for loop missing closing parenthesis ')'", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                if (semicolonCount < 2) {
                    errors.add(new SyntaxError("for loop must have two semicolons in header (for(init;cond;inc))", tokenInfo.line, tokenInfo.column));
                }
                // Check for opening brace after condition
                int k = j + 1;
                while (k < tokens.size() && tokens.get(k).token.equals("\n")) k++; // skip newlines
                if (k >= tokens.size() || !tokens.get(k).token.equals("{")) {
                    errors.add(new SyntaxError("for loop missing opening brace '{' after header", tokenInfo.line, tokenInfo.column));
                }
                continue;
            }

            // Enhanced while loop checks
            if (token.equals("while")) {
                // Check for opening parenthesis
                if (i + 1 >= tokens.size() || !tokens.get(i + 1).token.equals("(")) {
                    errors.add(new SyntaxError("while loop missing opening parenthesis '('", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                // Find matching closing parenthesis
                int parenDepth = 1;
                int j = i + 2;
                boolean foundClosingParen = false;
                boolean foundCondition = false;
                while (j < tokens.size()) {
                    String t = tokens.get(j).token;
                    if (t.equals("(")) parenDepth++;
                    else if (t.equals(")")) parenDepth--;
                    if (!t.equals("(") && !t.equals(")") && parenDepth == 1) foundCondition = true;
                    if (parenDepth == 0) {
                        foundClosingParen = true;
                        break;
                    }
                    j++;
                }
                if (!foundClosingParen) {
                    errors.add(new SyntaxError("while loop missing closing parenthesis ')'", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                if (!foundCondition) {
                    errors.add(new SyntaxError("while loop missing condition in parentheses", tokenInfo.line, tokenInfo.column));
                }
                // Check for opening brace after condition
                int k = j + 1;
                while (k < tokens.size() && tokens.get(k).token.equals("\n")) k++; // skip newlines
                if (k >= tokens.size() || !tokens.get(k).token.equals("{")) {
                    errors.add(new SyntaxError("while loop missing opening brace '{' after condition", tokenInfo.line, tokenInfo.column));
                }
                continue;
            }

            // Enhanced do-while loop checks
            if (token.equals("do")) {
                // Check for opening brace after do
                int j = i + 1;
                while (j < tokens.size() && tokens.get(j).token.equals("\n")) j++; // skip newlines
                if (j >= tokens.size() || !tokens.get(j).token.equals("{")) {
                    errors.add(new SyntaxError("do-while loop missing opening brace '{' after 'do'", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                // Find matching closing brace
                int braceDepth = 1;
                int k = j + 1;
                boolean foundClosingBrace = false;
                while (k < tokens.size()) {
                    String t = tokens.get(k).token;
                    if (t.equals("{")) braceDepth++;
                    else if (t.equals("}")) braceDepth--;
                    if (braceDepth == 0) {
                        foundClosingBrace = true;
                        break;
                    }
                    k++;
                }
                if (!foundClosingBrace) {
                    errors.add(new SyntaxError("do-while loop missing closing brace '}'", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                // After closing brace, expect 'while', '(', condition, ')', and ';'
                int m = k + 1;
                while (m < tokens.size() && tokens.get(m).token.equals("\n")) m++; // skip newlines
                if (m >= tokens.size() || !tokens.get(m).token.equals("while")) {
                    errors.add(new SyntaxError("do-while loop missing 'while' after block", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                if (m + 1 >= tokens.size() || !tokens.get(m + 1).token.equals("(")) {
                    errors.add(new SyntaxError("do-while loop missing opening parenthesis '(' after 'while'", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                // Find matching closing parenthesis for while
                int parenDepth = 1;
                int n = m + 2;
                boolean foundClosingParen = false;
                boolean foundCondition = false;
                while (n < tokens.size()) {
                    String t = tokens.get(n).token;
                    if (t.equals("(")) parenDepth++;
                    else if (t.equals(")")) parenDepth--;
                    if (!t.equals("(") && !t.equals(")") && parenDepth == 1) foundCondition = true;
                    if (parenDepth == 0) {
                        foundClosingParen = true;
                        break;
                    }
                    n++;
                }
                if (!foundClosingParen) {
                    errors.add(new SyntaxError("do-while loop missing closing parenthesis ')' after 'while'", tokenInfo.line, tokenInfo.column));
                    continue;
                }
                if (!foundCondition) {
                    errors.add(new SyntaxError("do-while loop missing condition in parentheses after 'while'", tokenInfo.line, tokenInfo.column));
                }
                // After closing parenthesis, expect semicolon
                int p = n + 1;
                while (p < tokens.size() && tokens.get(p).token.equals("\n")) p++; // skip newlines
                if (p < tokens.size() && tokens.get(p).token.equals("{")) {
                    errors.add(new SyntaxError("Unexpected brace after while in do-while loop. In C++, the condition after while should be followed by a semicolon, not a block.", tokenInfo.line, tokenInfo.column));
                } else if (p >= tokens.size() || !tokens.get(p).token.equals(";")) {
                    errors.add(new SyntaxError("do-while loop missing semicolon ';' after while condition", tokenInfo.line, tokenInfo.column));
                }
                // Advance i to after the while (...);
                i = p;
                continue;
            }

            // Check for common keyword typos
            if (type == TokenType.IDENTIFIER) {
                if (token.equals("el")) {
                    errors.add(new SyntaxError("Unknown or invalid keyword 'el'. Did you mean 'else'?", tokenInfo.line, tokenInfo.column));
                }
                // You can add more typo checks here if needed
            }
        }

        // Check for unmatched braces at end of file
        if (!blockStack.isEmpty()) {
            errors.add(new SyntaxError("Unmatched opening brace", line, column));
        }

        // Check if main function exists and is unique
        boolean foundMain = false;
        int mainCount = 0;
        for (int i = 0; i < tokens.size(); i++) {
            // Look for 'int main' followed by '(' (allowing for whitespace/newlines)
            if (tokens.get(i).token.equals("int")) {
                int j = i + 1;
                while (j < tokens.size() && tokens.get(j).token.equals("\n")) j++;
                if (j < tokens.size() && tokens.get(j).token.equals("main")) {
                    int k = j + 1;
                    while (k < tokens.size() && tokens.get(k).token.equals("\n")) k++;
                    // Check for opening parenthesis
                    if (k >= tokens.size() || !tokens.get(k).token.equals("(")) {
                        errors.add(new SyntaxError("main function missing opening parenthesis '('", tokens.get(j).line, tokens.get(j).column));
                        continue;
                    }
                    // Check for closing parenthesis
                    int parenDepth = 1;
                    int m = k + 1;
                    boolean foundClosingParen = false;
                    while (m < tokens.size()) {
                        String t = tokens.get(m).token;
                        if (t.equals("(")) parenDepth++;
                        else if (t.equals(")")) parenDepth--;
                        if (parenDepth == 0) {
                            foundClosingParen = true;
                            break;
                        }
                        m++;
                    }
                    if (!foundClosingParen) {
                        errors.add(new SyntaxError("main function missing closing parenthesis ')'", tokens.get(j).line, tokens.get(j).column));
                        continue;
                    }
                    // Check for opening brace after main()
                    int n = m + 1;
                    while (n < tokens.size() && tokens.get(n).token.equals("\n")) n++;
                    if (n >= tokens.size() || !tokens.get(n).token.equals("{")) {
                        errors.add(new SyntaxError("main function missing opening brace '{' after main()", tokens.get(j).line, tokens.get(j).column));
                        continue;
                    }
                    foundMain = true;
                    mainCount++;
                }
            }
        }
        if (!foundMain) {
            errors.add(new SyntaxError("Missing main function", 1, 1));
        } else if (mainCount > 1) {
            errors.add(new SyntaxError("Multiple main functions detected. Only one main function is allowed.", 1, 1));
        }
    }
}