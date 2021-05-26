package dev.mee42


sealed interface Token {
    data class Bracketed(val char: BracketChar, val contents: List<Token>): Token
    data class VariableIdentifier(val identifier: dev.mee42.VariableIdentifier): Token
    data class TypeIdentifier(val identifier: dev.mee42.TypeIdentifier): Token
    data class Symbol(val op: String): Token {
        override fun toString(): String {
            return "Symbol($op)"
        }
    }
    data class Keyword(val keyword: dev.mee42.Keyword): Token
    data class StringLiteral(val str: String): Token
    data class NumericalLiteral(val str: String): Token
    data class CharLiteral(val c: Char): Token
    data class Label(val label: LabelIdentifier): Token
}
enum class Keyword(val str: String) {
    RETURN("return"), FUNC("func"), STRUCT("struct"), VAL("val"), VAR("var"),
    IF("if"), ELSE("else"), WHILE("while"), LOOP("loop"), YIELD("yield"), CONTINUE("continue")
}

enum class BracketChar(val open: Char, val close: Char) {
    PAREN('(', ')'),
    SQUARE_BRACKETS('[',']'),
    CURLY_BRACKETS('{', '}'),
}
// constants
private val lowerLetters = ('a'..'z').toSet()
private val upperLetters = lowerLetters.map(Char::uppercaseChar).toSet()
private val numbers = ('0'..'9').toSet()

// TODO Operator enum?? this is just for lexing tho we should be fine
private val operators = listOf(".","->", "+", "-", "*", "/", "%", "<", ">", "++", ".", "->", "!", "&", "::",",", ";","==", "=")
private val openBrackets = BracketChar.values().map { it.open }.toSet()
private val closeBrackets = BracketChar.values().map { it.close }.toSet()
private val keywords = Keyword.values().map { it.str }
private val variableIdentifierAllowedFirstChars = lowerLetters
private val typeIdentifierAllowedFirstChars = upperLetters
private val identifierAllowedChars = lowerLetters + upperLetters + numbers + setOf('_')
private val numberChars = numbers + lowerLetters + setOf('.')
private val numberCharsInner = numberChars + "iulb".toSet()
// allowed postfixes:
/*
17ui
17ub
17ul
17i
17b
17l

 */


// returns the leftover string, and $previous plus the tokens that were lexed out.
// if the returned string is present, it will start with a closing bracket.
fun lexOne(str: String): Pair<String, Token> {
    if(str.isEmpty()) error("called lex() with an empty string")
    
    if(str[0] in setOf(' ','\n', '\t')) return lexOne(str.trimStart(' ', '\n', '\t'))
    
    val (rest, token) = when {
        operators.any { str.startsWith(it) } -> {
            val operator = operators.first { str.startsWith(it) }
            val rest = str.substring(operator.length)
            rest to Token.Symbol(operator)
        }
        keywords.any { str.startsWith(it) } -> {
            val keyword = Keyword.values().first { str.startsWith(it.str) }
            str.substring(keyword.str.length) to Token.Keyword(keyword)
        }
        str[0] in openBrackets -> {
            val bracket = BracketChar.values().first { it.open == str[0] }
            val tokens = mutableListOf<Token>()
            var rest = str.substring(1).trimStart(' ', '\n', '\t')
            while(rest.isNotBlank() && rest[0] !in closeBrackets) {
                lexOne(rest).let { (newRest, token) ->
                    tokens += token
                    rest = newRest.trimStart(' ', '\n', '\t')
                }
            }
            // verify that the rest is the same exact char
            if(rest[0] != bracket.close) {
                error("expecting '${bracket.close}', but got '${rest[0]}'")
            }
            // substring lets you
            rest.substring(1) to Token.Bracketed(bracket, tokens)
        }
        str[0] in variableIdentifierAllowedFirstChars -> {
            val id = str.takeWhile { it in identifierAllowedChars }
            str.substring(id.length) to Token.VariableIdentifier(id)
        }
        str[0] in typeIdentifierAllowedFirstChars -> {
            val id = str.takeWhile { it in identifierAllowedChars }
            str.substring(id.length) to Token.TypeIdentifier(id)
        }
        str[0] in numbers -> {
            val number = str.takeWhile { it in numberCharsInner }
            str.substring(number.length) to Token.NumericalLiteral(number)
        }
        str[0] == '\'' -> {
            if(str[2] != '\'') error("expecting single quote, found '${str[2]}'")
            str.substring(3) to Token.CharLiteral(str[1])
        }
        str[0] == '"' -> {
            val content = str.substring(1).takeWhile { it != '"' }
            str.substring(content.length + 2) to Token.StringLiteral(content)
        }
        str[0] == '@' -> {
            val label =  str.substring(1).takeWhile { it in identifierAllowedChars }
            str.substring(label.length + 1) to Token.Label(label)
        }
        else -> error("no clue what happened but I can't lex this: \"$str\"")
    }
    // somehow
    return rest to token
}


fun lex(str: String): List<Token> {
    var rest = str.split("\n")
        .map { if(it.contains("//")) it.substring(0, it.indexOf("//")) else it }
        .joinToString("\n")
    val tokens = mutableListOf<Token>()
    while(rest.isNotEmpty()) {
        lexOne(rest).let { (newRest, token) -> 
            rest = newRest.trimStart(' ', '\n', '\t')
            tokens += token
        }
    }
    return tokens
}





