package dev.mee42.parser

import dev.mee42.CompilerException
import dev.mee42.ConsumableQueue
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenException
import dev.mee42.lexer.TokenType
import dev.mee42.splitBy


class ParseException private constructor(): CompilerException() {
    override var message: String = "hmm"
        private set
    constructor(message: String, token: Token): this(){
        this.message = TokenException(token, message).message ?: "(unknown token)"
    }
    constructor(message: String) : this() {
        this.message = message
    }
}


fun parse(tokens: List<Token>): AST {
    val initialAST = parsePass1(tokens)
    return parsePass2(initialAST)
}

fun Token.checkType(type: TokenType, message: String): Token {
    if(this.type != type) throw ParseException(message, this)
    return this
}





