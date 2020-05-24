package dev.mee42.lexer

import dev.mee42.CompilerException
import dev.mee42.xpp.LabeledLine
import org.intellij.lang.annotations.Language

data class Token(val content: String, val line: Int, val index: Int, val lineContent: String, val type: TokenType) {
    override fun toString(): String {
        return type.name + "($content)"
    }
}

open class TokenException(lineContent: String, line: Int, index: Int, message: String, postfixMessage: String? = null): CompilerException(
        message = "$message at ($line:$index):\n $lineContent\n" + " ".repeat(index + 1) + "^" + postfixMessage?.let { "\n$it" }.orEmpty()
) {
    constructor(token: Token, message: String): this(token.lineContent, token.line, token.index, message)
}

enum class TokenType(val regex: Regex) {
    RETURN_KEYWORD("return"),
    STRUCT_KEYWORD("struct"),
    IF_KEYWORD("if"),
    WHILE_KEYWORD("while"),
    TRUE("true"),
    FALSE("false"),
    @Language("RegExp") CHARACTER(quote = false, str  = """'.'"""),
    OPEN_PARENTHESES("("),
    CLOSE_PARENTHESES(")"),
    OPEN_BRACKET("{"),
    CLOSE_BRACKET("}"),
    COMMA(","),
    COLON(":"),
    SEMICOLON(";"),
    OPEN_COMMENT("/*"),
    CLOSE_COMMENT("*/"),
    ASTERISK("*"),
    PLUS("+"),
    DOT("."),
    REF("&"),
    @Language("RegExp") STRING(quote = false, str = """"((\\\\)|(\\")|(\\n)|(\\t)|[^"\\]*)+""""),
    NEWLINE("\n"),
    @Language("RegExp") WHITESPACE(quote = false, str = """\s+"""),
    @Language("RegExp") ATTRIBUTE (quote = false, str = """@[a-zA-Z0-9_]*"""),
    @Language("RegExp") IDENTIFIER(quote = false, str = """[a-zA-Z][A-Za-z0-9_]*"""),
    @Language("RegExp") INTEGER   (quote = false, str = """(?:-?)[0-9]+(?:ub|us|ui|ul|b|s|i|l|u)?"""),
    MINUS("-"),
    SLASH("/"),
    EQUALS_EQUALS("=="),
    NOT_EQUALS("!="),
    ASSIGNMENT("="),
    NOT("!")
    ;
    constructor(str: String, quote: Boolean = true): this(Regex("^" + if(quote) Regex.escape(str) else str))
}

fun lex(lines: List<LabeledLine>): List<Token> {
    val tokens = mutableListOf<Token>()
    for((lineIndex, line) in lines.withIndex()) {
        var index = 0
        while(!line.content.substring(index).isBlank()) {
            var foundToken = false
            // okay, let's try and match this with some regexes
            for (possibleToken in TokenType.values()) {
                val lineContent = line.content.substring(index)
                // hopefully this goes in order...
                val maybe = possibleToken.regex.find(lineContent) ?: continue
                tokens.add(Token(content = maybe.value, line = lineIndex, index = index, type = possibleToken, lineContent = line.content))
                index += maybe.value.length
                foundToken = true
                break
            }
            if(!foundToken) {
                throw TokenException(lineContent = line.content, index = index, line = line.line, message = "can't find token")
            }
        }
        tokens.add(Token(content = "NEWLINE", line = lineIndex, index = line.content.length, type = TokenType.NEWLINE, lineContent = line.content))
    }
    tokens.removeIf { it.type == TokenType.WHITESPACE }
    return tokens
}


// ( hello . world )

// OPEN_PAREN IDENTIFIER DOT

