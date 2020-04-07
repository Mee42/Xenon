package dev.mee42.parser

import dev.mee42.InternalCompilerException
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenType
import dev.mee42.lexer.TokenType.*
import dev.mee42.parser.TypeEnum.*
import java.util.*

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
            name = it.name, returnType = it.returnType, arguments = it.arguments, id = it.id, attributes = it.attributes
        )
    }
    if(it.content.first().type != OPEN_BRACKET || it.content.last().type != CLOSE_BRACKET) {
        throw InternalCompilerException(it.content.toString())
    }
    // this is a block, it has both the open and close brackets as tokens
    val arguments = it.arguments.map { LocalVariable(it.name, it.type, isFinal = false) }
    val block = parseBlock(TokenQueue(ArrayDeque(it.content)), initialAST, arguments)
    return XenonFunction(name = it.name, content = block, returnType = it.returnType, arguments = it.arguments, id = it.id, attributes = it.attributes)
}
/** this assumes that tokens starts with a { and ends the block with a } */
private fun parseBlock(tokens: TokenQueue, initialAST: InitialAST, localVariables: List<LocalVariable>): Block {
    tokens.remove().checkType(OPEN_BRACKET,"parseBlock expects token stream to start with an opening bracket")
    val statements = mutableListOf<Statement>()
    val scopedLocalVariables = mutableListOf<LocalVariable>()
    while(tokens.peek().type != CLOSE_BRACKET) {
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

private fun parseExpression(tokens: TokenQueue,  initialAST: InitialAST, localVariables: List<LocalVariable>): Expression {
    // okay, wonderful
    val first = tokens.remove()
    fun checkForOperator(expression: Expression): Expression {
        val trueNext = tokens.peekWithNewline()
        val next = tokens.peek()
        if(trueNext.type == NEWLINE && next.type in listOf(ASTERISK, OPEN_PARENTHESES)) { // this list should expand in the future
            return expression
        }
        return when(next.type) {
            OPERATOR, ASTERISK -> {
                tokens.remove() // consume it
                val math = MathType.values().firstOrNull { it.symbol == next.content }
                if(math != null) {
                     checkForOperator(MathExpression(expression, parseExpression(tokens, initialAST, localVariables), math))
                } else {
                    if(next.content == "==" || next.content == "!="){
                        EqualsExpression(expression, parseExpression(tokens, initialAST, localVariables), next.content == "!=")
                    } else TODO("can't handle any other operators")
                }
            }
            OPEN_PARENTHESES -> {
                if(first.type != IDENTIFIER) throw ParseException("not a functional language, yet", next)
                TODO()
            }
            CLOSE_PARENTHESES, COMMA, SEMICOLON, CLOSE_BRACKET, IDENTIFIER, RETURN_KEYWORD, IF_KEYWORD, WHILE_KEYWORD, OPEN_BRACKET -> expression
            DOT -> TODO("member access not supported yet")
            else -> throw ParseException("unexpected token type ${next.type.name} while parsing expression", next)
        }
    }
    return when (first.type) {
        INTEGER -> {
            val str = first.content
            val typeChars = str.takeLastWhile { it in listOf('b','s','i','l','u') }
            val type = when(typeChars){
                "b" -> INT8
                "s" -> INT16
                "i" -> INT32
                "l" -> INT64
                "ub" -> UINT8
                "us" -> UINT16
                "ui", "u" -> UINT32
                "ul" -> UINT64
                else -> INT32
            }
            val number = str.dropLast(typeChars.length)
            checkForOperator(number.toLongOrNull()?.let { IntegerValueExpression(it, BaseType(type)) }
                ?: throw ParseException("can't support that type of integer", first))
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
        TRUE, FALSE -> {
            IntegerValueExpression(if(first.type == TRUE) 1 else 0, BaseType(TypeEnum.BOOLEAN))
        }
        IDENTIFIER -> {
            if (tokens.peek().type == OPEN_PARENTHESES) {
                val arguments = mutableListOf<Expression>()
                tokens.remove().checkType(OPEN_PARENTHESES, "popped token changed from last peek")
                while (true) {
                    if (arguments.isEmpty()) {
                        val token = tokens.peek()
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
        CHARACTER -> {
            val str = first.content[1]
            IntegerValueExpression(str.toByte().toLong(), BaseType(UINT8))
        }
        STRING -> {
            StringLiteralExpression(first.content.substring(1, first.content.lastIndex)
                    .replace("\\n","\n")
                    .replace("\\t","\t"))
        }

        else -> TODO("can't support expressions that start with type ${first.type}")
    }
}

private fun parseStatement(tokens: TokenQueue, initialAST: InitialAST, localVariables: List<LocalVariable>): Statement {
    // this compiles ONE SINGLE STATEMENT
    var firstToken = tokens.remove()
    return when (firstToken.type) {
        SEMICOLON -> NoOpStatement
        IF_KEYWORD -> {
            // parse an if statement
            val conditional = parseExpression(tokens, initialAST, localVariables)
            val block = parseBlock(tokens, initialAST, localVariables)
            IfStatement(conditional, block)
        }
        ASTERISK -> {
            // it's a deref
            val nextToken = tokens.remove()
            val ptr = when (nextToken.type) {
                OPEN_PARENTHESES -> {
                    val expression = parseExpression(tokens, initialAST, localVariables)
                    tokens.remove().checkType(CLOSE_PARENTHESES, "expecting a close parentheses here")
                    expression
                }
                IDENTIFIER -> {
                    val variable = localVariables.firstOrNull { it.name == nextToken.content } ?: throw ParseException("can't find variable ${nextToken.content}", nextToken)
                    if(variable.type !is PointerType) {
                        throw ParseException("variable must be of pointer type to be dereferenced", nextToken)
                    }
                    VariableAccessExpression(variable.name, variable.type)
                }
                else -> throw ParseException("unexpected token", nextToken)
            }
            val equals =  tokens.remove()
            if(equals.type != OPERATOR || equals.content != "=") {
                throw ParseException("expecting a = token", equals)
            }
            val value = parseExpression(tokens, initialAST, localVariables)
            // typecheck
            if(ptr.type !is PointerType) throw ParseException("pointer value needs to be of type pointer")
            val pointerValue = ptr.type as PointerType
            if(pointerValue.type != value.type) throw ParseException("expecting value of type ${pointerValue.type}, got ${value.type}", equals)
            MemoryWriteStatement(ptr, value)
        }
        WHILE_KEYWORD -> {
            val conditional = parseExpression(tokens, initialAST, localVariables)
            val block = parseBlock(tokens, initialAST, localVariables)
            WhileStatement(conditional, block)
        }
        RETURN_KEYWORD -> {
            val value = parseExpression(tokens, initialAST, localVariables)
            ReturnStatement(value)
        }
        IDENTIFIER -> {
            // well, it might be a function call, or it might be a equals-type thing
            // check the next character
            val isMutable = if(firstToken.content == "mut") {
                firstToken = tokens.remove()
                true
            } else false

            if(firstToken.content == "val") {
                val identifier = tokens.remove().checkType(IDENTIFIER, "looking for variable identifier").content
                val nextToken = tokens.remove()
                if (!(nextToken.type == OPERATOR && nextToken.content == "=")) {
                    throw ParseException("expecting an equal sign", nextToken)
                }
                val expression = parseExpression(tokens, initialAST, localVariables)
                DeclareVariableStatement(
                        variableName = identifier,
                        final = !isMutable,
                        expression = expression
                )
            } else if(firstToken.content in initialAST.structs.map { it.name }.plus(TypeEnum.values().flatMap { it.names })) {
                val firstTokens = listOf(firstToken) + tokens.removeWhile { it.type != OPERATOR}
                val identifier = firstTokens.last()
                val type = parseType(firstTokens.dropLast(1), identifier)
                val nextToken = tokens.remove()
                if (!(nextToken.type == OPERATOR && nextToken.content == "=")) {
                    throw ParseException("expecting an equal sign", nextToken)
                }
                val expression = parseExpression(tokens, initialAST, localVariables)
                if(expression.type != type) {
                    throw ParseException("mismatched types: expecting $type but got ${expression.type}",nextToken)
                }
                DeclareVariableStatement(
                        variableName = identifier.content,
                        final = !isMutable,
                        expression = expression
                )
            } else if(tokens.peek().type == OPERATOR) {
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
        }
        else -> throw ParseException("can't support statements that start with ${firstToken.content}", firstToken)
    }
}

fun TokenQueue.takeAllNestedIn(beginning: TokenType, end: TokenType, includeSurrounding: Boolean = false): List<Token> {
    val opening = this.remove().checkType(beginning, "must start with the proper opening token, ${beginning.name}")
    var count = 0
    val list = mutableListOf<Token>()
    if(includeSurrounding) list.add(opening)
    this.removeWhile(includeNewlines = true) {
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