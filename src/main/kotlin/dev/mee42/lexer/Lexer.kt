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
    FUNCTION_KEYWORD("function"),
    RETURN_KEYWORD("return"),
    IF_KEYWORD("if"),
    WHILE_KEYWORD("while"),
    OPEN_PARENTHESES("("),
    CLOSE_PARENTHESES(")"),
    OPEN_BRACKET("{"),
    CLOSE_BRACKET("}"),
    COMMA(","),
    COLON(":"),
    SEMICOLON(";"),
    ASTERISK("*"),
    DOT("."),
    @Language("RegExp") WHITESPACE(quote = false, str = """\s+"""),
    @Language("RegExp") ATTRIBUTE (quote = false, str = """@[a-zA-Z0-9_]*"""),
    @Language("RegExp") IDENTIFIER(quote = false, str = """[a-z][A-Za-z0-9_]*"""),
    @Language("RegExp") OPERATOR  (quote = false, str = """[+\-/><=!*%]+"""),
    @Language("RegExp") INTEGER   (quote )
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
                throw TokenException(lineContent = line.content, index = index, line = line.index, message = "can't find token")
            }
        }
    }
    tokens.removeIf { it.type == TokenType.WHITESPACE }
    return tokens
}