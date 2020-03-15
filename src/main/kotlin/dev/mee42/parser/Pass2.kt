package dev.mee42.parser

import dev.mee42.ConsumableQueue
import dev.mee42.InternalCompilerException
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenType
import dev.mee42.splitBy

fun parsePass2(initialAST: InitialAST): AST {
    // this does all the type checking. After this, no more token manipulation should be needed
    val functions = initialAST.functions.map { parseFunction(it, initialAST) }
    return AST(functions = functions)
}

private fun parseFunction(it: InitialFunction, initialAST: InitialAST): Function {
    if(it.content.first().type != TokenType.OPEN_BRACKET || it.content.last().type != TokenType.CLOSE_BRACKET) {
        throw InternalCompilerException(it.content.toString())
    }
    // this is a block, it has both the open and close parentheses as tokens iirc

}
/** this assumes that tokens starts with a { and ends the block with a } */
private fun parseBlock(tokens: ConsumableQueue<Token>, initialAST: InitialAST): Block {
    tokens.remove().checkType(TokenType.OPEN_BRACKET,"parseBlock expects token stream to start with an opening bracket")
    TODO()
}
/** this DOES NOT consuming the endingToken - it might never hit it, either */
private fun parseExpression(tokens: ConsumableQueue<Token>, endingToken: Token,  initialAST: InitialAST): Expression {
    // okay, wonderful
}

private fun parseStatement(tokens: ConsumableQueue<Token>, initialAST: InitialAST): Statement {
    // this compiles ONE SINGLE STATEMENT
    val firstToken = tokens.remove()
    if(firstToken.type == TokenType.SEMICOLON) {
        return NoOpStatement
        // if it's any character, we're screwed
    } else if(firstToken.type == TokenType.IF_KEYWORD) {
        // parse an if statement
        parseStatement(tokens, ast)
    }
}

fun ConsumableQueue<Token>.takeAllNestedIn(beginning: TokenType, end: TokenType): List<Token> {
    this.remove().checkType(beginning, "must start with the proper opening token, ${beginning.name}")
    var count = 0
    val list = mutableListOf<Token>()
    this.removeWhile {
        when (it.type) {
            beginning -> {
                count++
            }
            end -> {
                if (count == 0) return@removeWhile false
                else count--
            }
            else -> list.add(it)
        }
        true
    }
    this.remove().checkType(end, "illegal state")
    return list
}