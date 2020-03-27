package dev.mee42.parser

import dev.mee42.ConsumableQueue
import dev.mee42.InternalCompilerException
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenType
import dev.mee42.lexer.TokenType.*
import kotlin.math.exp

private data class LocalVariable(val name: String, val type: Type, val isFinal: Boolean)

fun parsePass2(initialAST: InitialAST): AST {
    // this does all the type checking. After this, no more token manipulation should be needed
    val functions = initialAST.functions.map { parseFunction(it, initialAST) }
    return AST(functions = functions)
}

private fun parseFunction(it: InitialFunction, initialAST: InitialAST): Function {
    if(it.content == null) {
        // it's an assembly function
        return AssemblyFunction(
            name = it.name, returnType = it.returnType, arguments = it.arguments, id = it.id
        )
    }
    if(it.content.first().type != OPEN_BRACKET || it.content.last().type != CLOSE_BRACKET) {
        throw InternalCompilerException(it.content.toString())
    }
    // this is a block, it has both the open and close brackets as tokens
    val arguments = it.arguments.map { LocalVariable(it.name, it.type, isFinal = true) }
    val block = parseBlock(ConsumableQueue(it.content), initialAST, arguments)
    return XenonFunction(name = it.name, content = block, returnType = it.returnType, arguments = it.arguments, id = it.id)
}
/** this assumes that tokens starts with a { and ends the block with a } */
private fun parseBlock(tokens: ConsumableQueue<Token>, initialAST: InitialAST, localVariables: List<LocalVariable>): Block {
    tokens.remove().checkType(OPEN_BRACKET,"parseBlock expects token stream to start with an opening bracket")
    val statements = mutableListOf<Statement>()
    val scopedLocalVariables = mutableListOf<LocalVariable>()
    while(tokens.peek()!!.type != CLOSE_BRACKET) {
        val statement = parseStatement(tokens, initialAST, localVariables + scopedLocalVariables)
        if(statement is DeclareVariableStatement){
            scopedLocalVariables.add(LocalVariable(
                name = statement.variableName,
                type = statement.expression.type,
                isFinal = statement.final
            ))
        }
        statements.add(statement)
    }
    tokens.remove().checkType(CLOSE_BRACKET,"illegal state")
    return Block(statements)
}

private fun parseExpression(tokens: ConsumableQueue<Token>,  initialAST: InitialAST, localVariables: List<LocalVariable>): Expression {
    // okay, wonderful
    val first = tokens.remove()
    fun checkForOperator(expression: Expression): Expression {
        val next = tokens.peek() ?: return expression
        // so it's a variable access, and possibly has an operator after it
        // ex:
        return when(next.type) {
            OPERATOR, ASTERISK -> {
                tokens.remove() // consume it
                val math = MathType.values().firstOrNull { it.symbol == next.content }
                if(math != null) {
                     checkForOperator(MathExpression(expression, parseExpression(tokens, initialAST, localVariables), math))
                } else {
                    if(next.content == "=="){
                        EqualsExpression(expression, parseExpression(tokens, initialAST, localVariables))
                    } else TODO("can't handle any other operators")
                }
            }
            OPEN_PARENTHESES -> {
                if(first.type != IDENTIFIER) throw ParseException("not a functional language, yet", next)
                TODO()
            }
            CLOSE_PARENTHESES, COMMA, SEMICOLON, CLOSE_BRACKET, IDENTIFIER, RETURN_KEYWORD, IF_KEYWORD, OPEN_BRACKET -> expression
            DOT -> TODO("member access not supported yet")
            else -> throw ParseException("unexpected token type ${next.type.name} while parsing expression", next)
        }
    }
    return when (first.type) {
        INTEGER -> {
            val str = first.content
            // int32 is the only option as of rn
            str.toIntOrNull()?.let { IntegerValueExpression(it, BaseType(TypeEnum.INT32)) }
                ?: throw ParseException("can't support that type of integer", first)
        }
        OPEN_PARENTHESES -> {
            val enclosed = parseExpression(tokens, initialAST, localVariables)
            tokens.remove().checkType(CLOSE_PARENTHESES, "expecting a closed parentheses")
            checkForOperator(enclosed)
        }
        ASTERISK -> {
            val dereferencedPointer = parseExpression(tokens, initialAST, localVariables)
            if(dereferencedPointer.type !is PointerType) {
                throw ParseException("Can't derefrence a non-pointer type " + dereferencedPointer.type, first)
            }
            DereferencePointerExpression(dereferencedPointer)
        }
        IDENTIFIER -> {
            if (tokens.peek()?.type == OPEN_PARENTHESES) {
                val arguments = mutableListOf<Expression>()
                val paranth = tokens.remove().checkType(OPEN_PARENTHESES, "popped token changed from last peek")
                while (true) {
                    if (arguments.isEmpty()) {
                        val token = tokens.peek() ?: error("no")
                        if (token.type == CLOSE_PARENTHESES) {
                            tokens.remove() // remove the close parentheses
                            break
                        }
                        if (token.type == COMMA) throw ParseException("not expecting a comma", token)
                    } else {
                        val token = tokens.remove()
                        if (token.type == CLOSE_PARENTHESES) break
                        if (token.type != COMMA) throw ParseException("expecting a comma $arguments", token)
                    }
                    arguments.add(parseExpression(tokens, initialAST, localVariables))
                }
                // ok, let's find the function with that name
                val functionOptions = initialAST.functions.filter { it.name == first.content }
                if(functionOptions.isEmpty()) throw ParseException("Can't find function named \"${first.content}\"", first)
                val function = functionOptions.firstOrNull {
                    it.arguments.size == arguments.size && arguments.map { w -> w.type } == it.arguments.map { w -> w.type }
                } ?: throw ParseException("Can't find function named \"${first.content}\" where arguments are ${arguments.map { it.type }}\n" +
                    "Possible options:\n" +
                    functionOptions.map { "\t - " + it.toDefString() + "\n"}, first)

                checkForOperator(FunctionCallExpression(
                    arguments = arguments,
                    functionIdentifier = function.identifier,
                    returnType = function.returnType,
                    argumentNames = function.arguments.map { it.name }))
            } else {
                val firstAsLocalVariable = VariableAccessExpression(first.content,
                    type = localVariables.firstOrNull { it.name == first.content }?.type
                        ?: throw ParseException("can't find variable ${first.content}", first)
                )
                checkForOperator(firstAsLocalVariable)
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
            val block = parseBlock(tokens, initialAST, localVariables)
            IfStatement(conditional, block)
        }
        RETURN_KEYWORD -> {
            val value = parseExpression(tokens, initialAST, localVariables)
            ReturnStatement(value)
        }
        IDENTIFIER -> {
            // well, it might be a function call, or it might be a equals-type thing
            // check the next character
            if(firstToken.content == "val" || firstToken.content == "var") {
                val isFinal = firstToken.content == "val"
                val identifier = tokens.remove().checkType(IDENTIFIER, "looking for variable identifier").content
                var nextToken = tokens.remove()
                val type = if (nextToken.type == OPERATOR && nextToken.content == "<") {
                    // typedef
                    val typeTokens = tokens.removeWhile { !(it.type == OPERATOR && it.content == ">") }
                    val type = parseType(typeTokens, nextToken)
                    if(tokens.remove().content != ">") error("hm")
                    nextToken = tokens.remove()
                    type
                } else null

                if(!(nextToken.type == OPERATOR && nextToken.content == "=")){
                    throw ParseException("expecting an equal sign", nextToken)
                }
                val expression = parseExpression(tokens, initialAST, localVariables)
                if(type != null) {
                    if(expression is IntegerValueExpression && type is BaseType && type.type != expression.type.type) {
                        // okay, we gotta make sure types are good
                        // because they aren't the same
                        // if expression is unsigned but type is signed, error
                        if(expression.type.type.isUnsigned && !type.type.isUnsigned){
                            throw ParseException("can't implicit cast from ${expression.type} to $type",nextToken)
                        } else {
                            TODO("I just can't deal oof")
                        }
                    }
                    if(expression.type != type) {
                        throw ParseException("type failure - looking for $type but got ${expression.type}", nextToken)
                    }
                }
                DeclareVariableStatement(
                    variableName = identifier,
                    final = isFinal,
                    expression = expression
                )
            } else if(tokens.peek()!!.type == OPERATOR) {
                val operator = tokens.remove()
                if(operator.content == "=") {
                    val variable = localVariables.firstOrNull { it.name == firstToken.content } ?: throw ParseException("can't find variable ${firstToken.content}", firstToken)
                    if(variable.isFinal) throw ParseException("variable is final", firstToken)
                    val expression = parseExpression(tokens, initialAST, localVariables)
                    AssignVariableStatement(
                        variableName = variable.name,
                        expression = expression
                    )
                } else {
                    TODO("can't support operators after identifiers as a statement")
                }
            } else {
                tokens.shove(firstToken)
                ExpressionStatement(parseExpression(tokens, initialAST, localVariables))
            }
            // it's an expression! can we like, shove the token back into the stream and parse it as an expression?
            // yes
        }
        else -> throw ParseException("can't support statements that start with ${firstToken.content}", firstToken)
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
            else -> {}
        }
        list.add(it)
        true
    }
    val ending = this.remove().checkType(end, "illegal state")
    if(includeSurrounding) list.add(ending)
    return list
}