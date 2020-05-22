package dev.mee42.parser

import dev.mee42.CompilerException
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenException
import dev.mee42.lexer.TokenType


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


fun Token.checkType(type: TokenType, message: String): Token {
    if(this.type != type) throw ParseException(message, this)
    return this
}





