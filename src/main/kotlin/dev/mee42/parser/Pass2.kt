package dev.mee42.parser

import dev.mee42.ConsumableQueue
import dev.mee42.InternalCompilerException
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenType
import dev.mee42.lexer.TokenType.*

private data class LocalVariable(val name: String, val type: Type, val isFinal: Boolean)

fun parsePass2(initialAST: InitialAST): AST {
    // this does all the type checking. After this, no more token manipulation should be needed
    val functions = initialAST.functions.map { parseFunction(it, initialAST) }
    return AST(functions = functions)
}

private fun parseFunction(it: InitialFunction, initialAST: InitialAST): Function {
    if(it.content.first().type != OPEN_BRACKET || it.content.last().type != CLOSE_BRACKET) {
        throw InternalCompilerException(it.content.toString())
    }
    // this is a block, it has both the open and close brackets as tokens
    val arguments = it.arguments.map { LocalVariable(it.name, it.type, isFinal = true) }
    val block = parseBlock(ConsumableQueue(it.content), initialAST, arguments)
    return Function(name = it.name, content = block, returnType = it.returnType, arguments = it.arguments)
}
/** this assumes that tokens starts with a { and ends the block with a } */
private fun parseBlock(tokens: ConsumableQueue<Token>, initialAST: InitialAST, localVariables: List<LocalVariable>): Block {
    tokens.remove().checkType(OPEN_BRACKET,"parseBlock expects token stream to start with an opening bracket")
    val statements = mutableListOf<Statement>()
    while(tokens.peek()!!.type != CLOSE_BRACKET) {
        statements.add(parseStatement(tokens, initialAST, localVariables))
    }
    tokens.remove().checkType(CLOSE_BRACKET,"illegal state")
    return Block(statements)
}

private fun parseExpression(tokens: ConsumableQueue<Token>,  initialAST: InitialAST, localVariables: List<LocalVariable>): Expression {
    // okay, wonderful
    val first = tokens.remove()
    return when (first.type) {
        OPEN_PARENTHESES -> {
            val enclosed = parseExpression(tokens, initialAST, localVariables)
            tokens.remove().checkType(CLOSE_PARENTHESES, "expecting a closed parentheses")
            enclosed
        }
        ASTERISK -> {
            val dereferencedPointer = parseExpression(tokens, initialAST, localVariables)
            DereferencePointerExpression(dereferencedPointer)
        }
        IDENTIFIER -> {
            val firstAsLocalVariable = VariableAccessExpression(first.content,
                type = localVariables.firstOrNull { it.name == first.content }?.type
                    ?: throw ParseException("can't find variable ${first.content}", first)
            )

            val next = tokens.peek() ?: return firstAsLocalVariable
            // so it's a variable access, and possibly has an operator after it
            // ex:
            when(next.type) {
                OPERATOR -> {
                    tokens.remove() // consume it
                    when (next.content) {
                        "+" -> AddExpression(firstAsLocalVariable, parseExpression(tokens, initialAST, localVariables))
                        "-" -> SubExpression(firstAsLocalVariable, parseExpression(tokens, initialAST, localVariables))
                        else -> TODO("only addition supported right now")
                    }
                }
                OPEN_PARENTHESES -> TODO("function calls not supported yet")
                CLOSE_PARENTHESES, COMMA, SEMICOLON, CLOSE_BRACKET -> firstAsLocalVariable
                DOT -> TODO("member access not supported yet")
                else -> throw ParseException("unexpected token type ${next.type.name} while parsing expression", next)
            }
        }
        else -> TODO("can't support expressions that start with type ${first.type}")
    }
}

private fun parseStatement(tokens: ConsumableQueue<Token>, initialAST: InitialAST, localVariables: List<LocalVariable>): Statement {
    // this compiles ONE SINGLE STATEMENT
    val firstToken = tokens.remove()
    return when (firstToken.type) {
        SEMICOLON -> NoOpStatement
        IF_KEYWORD -> {
            // parse an if statement
            val conditional = parseExpression(tokens, initialAST, localVariables)
            TODO("if statements are not done yet. Conditional: $conditional")
        }
        RETURN_KEYWORD -> {
            val value = parseExpression(tokens, initialAST, localVariables)
            return ReturnStatement(value)
        }
        else -> TODO("can't support statements that start with ${firstToken.content}")
    }
}

fun ConsumableQueue<Token>.takeAllNestedIn(beginning: TokenType, end: TokenType, includeSurrounding: Boolean = false): List<Token> {
    val opening = this.remove().checkType(beginning, "must start with the proper opening token, ${beginning.name}")
    var count = 0
    val list = mutableListOf<Token>()
    if(includeSurrounding) list.add(opening)
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
    val ending = this.remove().checkType(end, "illegal state")
    if(includeSurrounding) list.add(ending)
    return list
}