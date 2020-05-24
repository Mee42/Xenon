package dev.mee42.parser

import dev.mee42.InternalCompilerException
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenType
import dev.mee42.lexer.TokenType.*
import dev.mee42.lexer.lex
import dev.mee42.type
import dev.mee42.xpp.LabeledLine
import java.lang.ClassCastException
import java.util.*

private data class LocalVariable(val name: String, val type: Type, val isFinal: Boolean)

fun parsePass2(initialAST: InitialAST): AST {
    // this does all the type checking. After this, no more token manipulation should be needed
    val functions = initialAST.functions.map { parseFunction(it, initialAST) }
    return AST(functions = functions, structs = initialAST.structs)
}

private fun parseFunction(it: InitialFunction, initialAST: InitialAST): Function {
    if(it.content == null) {
        // it's an assembly function
        return AssemblyFunction(
            name = it.name, returnType = it.returnType.typed(), arguments = it.arguments.map { it.typed() }, id = it.id, attributes = it.attributes
        )
    }
    if(it.content.first().type != OPEN_BRACKET || it.content.last().type != CLOSE_BRACKET) {
        throw InternalCompilerException(it.content.toString())
    }
    // this is a block, it has both the open and close brackets as tokens
    val arguments = it.arguments.map { LocalVariable(it.typed().name, it.typed().type, isFinal = false) }
    val partBlock = parsePartBlock(TokenQueue(ArrayDeque(it.content)), initialAST)
    val block = parseBlock(partBlock, arguments, initialAST)
    return XenonFunction(
            name = it.name,
            content = block,
            returnType = it.returnType.typed(),
            arguments = it.arguments.map { it.typed() },
            id = it.id,
            attributes = it.attributes)
}


private fun parseBlock(partBlock: PartBlock, localVariables: List<LocalVariable>, ast: InitialAST): Block {
    val scopedLocalVariables = mutableListOf<LocalVariable>()
    val statements = partBlock.statements.map { statement ->
        when(statement) {
            is PartVariableDef -> {
                val expr = parseExpression(statement.value, localVariables + scopedLocalVariables, ast)
                if(statement.typeDef != null && statement.typeDef != expr.type) {
                    throw ParseException("mismatched types. Specified ${statement.typeDef}, got ${expr.type}")
                }
                if(scopedLocalVariables.any { it.name ==  statement.name } || localVariables.any { it.name == statement.name }) {
                    throw ParseException("variable \"${statement.name}\" already defined")
                }
                scopedLocalVariables.add(LocalVariable(name = statement.name, type = expr.type, isFinal = !statement.mutable))
                DeclareVariableStatement(statement.name, !statement.mutable, expr)
            }
            is PartIfStatement -> {
                val conditional = parseExpression(statement.conditional, localVariables + scopedLocalVariables, ast)
                if(conditional.type != type("bool")) {
                    throw ParseException("if statement conditional should be of type boolean, not of type ${conditional.type}")
                }
                val block = parseBlock(statement.block, localVariables + scopedLocalVariables, ast)
                IfStatement(conditional, block)
            }
            is PartWhileStatement -> {
                val conditional = parseExpression(statement.conditional, localVariables + scopedLocalVariables, ast)
                if(conditional.type != type("bool")) {
                    throw ParseException("while statement conditional should be of type boolean, not of type ${conditional.type}")
                }
                val block = parseBlock(statement.block, localVariables + scopedLocalVariables, ast)
                WhileStatement(conditional, block)
            }
            is PartReturn -> {
                ReturnStatement(parseExpression(statement.value,localVariables + scopedLocalVariables,ast))
            }
            is PartExpressionStatement -> {
                ExpressionStatement(parseExpression(statement.expression, localVariables + scopedLocalVariables, ast))
            }
        }
    }
    return Block(statements)
}
private fun parseExpression(expression: PartExpression, localVariables: List<LocalVariable>, ast: InitialAST): Expression {
    return when(expression) {
        is IdentifierPartExpression -> {
            // variable time
            val variable = localVariables.firstOrNull { it.name == expression.id } ?: throw ParseException("can't find variable \"${expression.id}\"", expression.token)
            VariableAccessExpression(variableName = variable.name, type = variable.type, isMutable = !variable.isFinal)
        }
        is IntegerValuePartExpression -> {
            IntegerValueExpression(expression.v, expression.type)
        }
        is StringValuePartExpression -> {
            StringLiteralExpression(expression.s)
        }
        is PrefixOperatorExpression -> when(expression.prefix){
            "!" -> TODO()
            "-" -> TODO()
            "*" -> {
                try {
                    DereferencePointerExpression(parseExpression(expression.expression, localVariables, ast))
                }catch(e: ClassCastException) {
                    e.printStackTrace() // TODO remove this please
                    throw ParseException("something broke", expression.prefixToken)
                }
            }
            "&" -> {
                val expr = parseExpression(expression.expression, localVariables, ast)
                if(expr !is LValueExpression) {
                    error("expression must be an lvalue in order to reference it")
                }
                RefExpression(expr, expr.isMutableLValue())
            }
            else -> error("Non-exhaustive pattern in compiler")
        }
        is InfixOperatorExpression -> when(expression.operator){
            "+","-","*","/","==","!=" -> ComparisonExpression(
                    var1 = parseExpression(expression.left, localVariables, ast),
                    mathType = MathType.values().firstOrNull { it.symbol == expression.operator } ?: error("pattern bullshit"),
                    var2 = parseExpression(expression.right, localVariables, ast))
            "=" -> {
                val var1 = parseExpression(expression.left, localVariables, ast)
                if(var1 !is LValueExpression) error("$var1 must be an lvalue expression to be set")
                val var2 = parseExpression(expression.right, localVariables, ast)
                if(var1.type != var2.type) error("mismatched types. Attempting to set ${var1.type} but found ${var2.type}")
                AssigmentExpression(var1, var2)
            }
            else -> error("didn't cover everything")
        }
        is FunctionCallPartExpression -> {
            val functionName = expression.value
            val identifier = if(functionName is IdentifierPartExpression) {
                functionName.id
            } else {
                TODO("structs - coming soon!")
            }
            val arguments = expression.arguments.map { parseExpression(it, localVariables, ast) }
            val possibleFunctions = ast.functions.filter { it.name == identifier }
            if(possibleFunctions.isEmpty()) throw ParseException("can't find function \"$identifier\"")
            val realFunction = possibleFunctions.firstOrNull { f ->
                f.arguments.size == arguments.size && f.arguments.map { it.typed().type } == arguments.map { it.type } }
                    ?: error("can't find function with type signature " + arguments.joinToString(", ", "(", ")") { it.type.toString() } +
                            "\nPossible alternitives with the same name:\n" + possibleFunctions.joinToString("\n","","") { f ->
                        f.name + " " + f.arguments.joinToString(", ","(",")") { it.typed().type.toString() }
                    })
            FunctionCallExpression(
                    functionIdentifier = realFunction.identifier,
                    returnType = realFunction.returnType.typed(),
                    arguments = arguments
            )
        }
        is PartStructInitExpression -> {
            val struct = ast.structs.firstOrNull { it.name == expression.structName }
                    ?: error("can't find struct with the name of of ${expression.structName}")
            StructInitExpression(struct.type)
        }
        is PartMemberAccessExpression -> {
            val struct = parseExpression(expression.struct, localVariables, ast)
            MemberAccessExpression(struct, expression.memberName)
        }
    }
}

/** this expects { and } to be on the queue */
private fun parsePartBlock(tokens: TokenQueue, initialAST: InitialAST): PartBlock {
    tokens.remove().checkType(OPEN_BRACKET,"expecting {")
    val statements = mutableListOf<PartStatement>()
    while(tokens.peek().type != CLOSE_BRACKET) {
        tokens.removeWhile { it.type == SEMICOLON }
        statements.add(parsePartStatement(tokens, initialAST))
    }
    tokens.remove().checkType(CLOSE_BRACKET,"expecting }")
    return PartBlock(statements)
}


// ast is needed for structs later
private fun parsePartStatement(tokens: TokenQueue, initialAST: InitialAST): PartStatement {
    val firstToken = tokens.remove()
    return when(firstToken.type) {
        RETURN_KEYWORD -> PartReturn(parseExpressionPart(tokens))
        IF_KEYWORD -> PartIfStatement(parseExpressionPart(tokens), parsePartBlock(tokens, initialAST))
        WHILE_KEYWORD -> {
            val expr = parseExpressionPart(tokens)
            PartWhileStatement(expr, parsePartBlock(tokens, initialAST))
        }
        IDENTIFIER -> {
            val (nextToken, isMutable) = if (firstToken.content == "mut") {
                tokens.remove() to true
            } else firstToken to false
            if(nextToken.content == "val") {
                val identifier = tokens.remove().checkType(IDENTIFIER, "looking for variable identifier").content
                tokens.remove().checkType(ASSIGNMENT, "looking for equal sign")
                val expression = parseExpressionPart(tokens)
                PartVariableDef(typeDef = null, name = identifier, mutable = isMutable, value = expression)
            } else {
                // is it a type?
                val type = TypeEnum.values().firstOrNull { it.names.contains(nextToken.content) }
                        ?.let { BaseType(it) }
                        ?: initialAST.structs.firstOrNull { it.name == nextToken.content }?.type
                if(type == null) {
                    // must be an expression or function or smth
                    tokens.shove(nextToken)
                    val expression = parseExpressionPart(tokens)
                    PartExpressionStatement(expression)
                } else {
                    var pointerType: Type = type
                    while(tokens.peek().type in listOf(ASTERISK, PLUS)) {
                        pointerType = PointerType(pointerType, tokens.peek().type == ASTERISK)
                    }
                    val identifier = tokens.remove().checkType(IDENTIFIER, "expecting a variable identifier here")
                    tokens.remove().checkType(ASSIGNMENT, "expecting an equals sign here")
                    val expression = parseExpressionPart(tokens)
                    PartVariableDef(typeDef = pointerType, name = identifier.content, mutable = isMutable, value = expression)
                }
            }
        }
        ASTERISK, OPEN_PARENTHESES -> {
            tokens.shove(firstToken)
            PartExpressionStatement(parseExpressionPart(tokens))
        }
        else -> throw ParseException("can't start statement with this token", firstToken)
    }
}






// ======= parse part ========
sealed class PartStatement
data class PartIfStatement(val conditional: PartExpression, val block: PartBlock): PartStatement()
data class PartWhileStatement(val conditional: PartExpression, val block: PartBlock): PartStatement()
data class PartBlock(val statements: List<PartStatement>)
data class PartReturn(val value: PartExpression): PartStatement()
data class PartVariableDef(val typeDef: Type?, val name: String, val mutable: Boolean, val value: PartExpression): PartStatement()
data class PartExpressionStatement(val expression: PartExpression): PartStatement()

sealed class PartExpression(val str: String)
data class IdentifierPartExpression(val id: String, val token: Token): PartExpression(id)
data class IntegerValuePartExpression(val v: Int, val type: BaseType): PartExpression(v.toString())
data class StringValuePartExpression(val s: String): PartExpression(s)
data class PrefixOperatorExpression(val prefix: String, val expression: PartExpression, val prefixToken: Token): PartExpression("$prefix${expression.str}")
data class InfixOperatorExpression(val operator: String, val left: PartExpression, val right: PartExpression): PartExpression("(${left.str} $operator ${right.str})")
data class FunctionCallPartExpression(val value: PartExpression, val arguments: List<PartExpression>):
        PartExpression(value.str + arguments.joinToString(",","(", ")") { it.str } )

data class PartStructInitExpression(val structName: String): PartExpression("${structName}{}")
data class PartMemberAccessExpression(val struct: PartExpression, val memberName: String): PartExpression("($struct).$memberName")

fun parseInfixOp(queue: TokenQueue, left: PartExpression, token: Token, precedence: Precedence) =
        InfixOperatorExpression(token.content, left, parseExpressionPart(queue, precedence.oneLess()))


private class InfixParser(val type: TokenType, val runner: (TokenQueue, PartExpression, Token, Precedence) -> PartExpression, val precedence: Precedence)
private class PrefixParser(val type: TokenType, val runner: (TokenQueue, Token) -> PartExpression)

private fun parser(type: TokenType, precedence: Precedence, runner: (TokenQueue, PartExpression, Token, Precedence) -> PartExpression) =
        InfixParser(type, runner, precedence)
private fun parser(type: TokenType, runner: (TokenQueue, Token) -> PartExpression = ::prefixOp) =
        PrefixParser(type, runner)

fun prefixOp(queue: TokenQueue, token: Token) =
        PrefixOperatorExpression(token.content, parseExpressionPart(queue, Precedence.PREFIX), token)

private val prefixParserMap = listOf(
        parser(ASTERISK),
        parser(MINUS),
        parser(REF),
        parser(NOT),
        parser(TRUE) { _, _ -> IntegerValuePartExpression(1, BaseType(TypeEnum.BOOLEAN)) },
        parser(FALSE) { _, _ -> IntegerValuePartExpression(0, BaseType(TypeEnum.BOOLEAN)) },
        parser(IDENTIFIER) { _, t -> IdentifierPartExpression(t.content, t) },
        parser(STRING) { _, t -> StringValuePartExpression(t.content.substring(1, t.content.length - 1)) },
        parser(INTEGER) { _, t ->
            val type = when(t.content.last()) {
                'l' -> BaseType(TypeEnum.INT64)
                'b' -> BaseType(TypeEnum.INT8)
                else -> null
            }
            val content = if(type == null) t.content else t.content.dropLast(1)
            IntegerValuePartExpression(content.toInt(), type ?: BaseType(TypeEnum.INT32)) },
        parser(OPEN_PARENTHESES) { queue, _ ->
            val expr = parseExpressionPart(queue, Precedence.NONE)
            queue.remove().checkType(CLOSE_PARENTHESES, "expecting a close parenthesese")
            expr
        }
)
private val infixParserMap = listOf(
        parser(PLUS, Precedence.SUM, ::parseInfixOp),
        parser(MINUS, Precedence.SUM, ::parseInfixOp),
        parser(ASTERISK, Precedence.PRODUCT, ::parseInfixOp),
        parser(SLASH, Precedence.PRODUCT, ::parseInfixOp),
        parser(EQUALS_EQUALS, Precedence.CONDITIONAL, ::parseInfixOp),
        parser(NOT_EQUALS, Precedence.CONDITIONAL, ::parseInfixOp),
        parser(ASSIGNMENT, Precedence.ASSIGNMENT) { queue ,left, token, _ ->
            InfixOperatorExpression(token.content, left, parseExpressionPart(queue, Precedence.NONE))
        },
        parser(OPEN_BRACKET, Precedence.STRUCT_INIT) { queue, left, _, _ ->
            queue.remove().checkType(CLOSE_BRACKET, "expecting close bracket")
            if(left !is IdentifierPartExpression) error("expecting an identifier on the the left of the thing")
            PartStructInitExpression(left.id)
        },
        parser(DOT, Precedence.STRUCT_MEMBER_ACCESS) { queue, left, _, _ ->
            val member = queue.remove().checkType(IDENTIFIER, "excpeting an identifier next").content
            PartMemberAccessExpression(left, member)
        },
        parser(OPEN_PARENTHESES, Precedence.CALL) { queue, left, token, _ ->
            val arguments = mutableListOf<PartExpression>()
            token.checkType(OPEN_PARENTHESES, "this better be an open parenth")
            var first = true

            loop@ while(true) {
                val next = queue.remove()
                if(first) {
                    first = false
                    if(next.type == CLOSE_PARENTHESES) break@loop
                    queue.shove(next)
                    arguments.add(parseExpressionPart(queue, Precedence.NONE))
                } else {
                    when (next.type) {
                        CLOSE_PARENTHESES -> break@loop
                        COMMA -> arguments.add(parseExpressionPart(queue, Precedence.NONE))
                        else -> throw ParseException("not sure what you want here", next)
                    }
                }
            }
//            println("arguments: $arguments")
            FunctionCallPartExpression(left, arguments)
        }
)

enum class Precedence(val order: Int) {
    NONE(0),
    ASSIGNMENT(1),
    CONDITIONAL(2),
    SUM(3),
    PRODUCT(4),
    PREFIX(5),
    CALL(6),
    STRUCT_INIT(6),
    STRUCT_MEMBER_ACCESS(7)
    ;

    fun oneLess():Precedence {
        val x = if(order == 0) 0 else order - 1
        return values().first { it.order == x }
    }
}

// *ptr + 1
// left: *(ptr + 1)
private fun parseExpressionPart(tokens: TokenQueue, precedence: Precedence = Precedence.NONE): PartExpression {
    val next = tokens.remove()
    val prefixParser = prefixParserMap.firstOrNull { it.type == next.type } ?: throw ParseException("not a prefix operator", next)
    var left =  prefixParser.runner(tokens, next)
    while(true) {
        val newline = tokens.peekWithNewline()
        if (newline.type == NEWLINE) return left
        val peek = tokens.peek()
        // if the precedence is >= whatever the precedence of the next infix expression would be
        if(precedence.order >= (infixParserMap.firstOrNull { peek.type == it.type }?.precedence ?: Precedence.NONE).order) break
        val infixParser = infixParserMap.firstOrNull { it.type == peek.type } ?: return left
        val removed = tokens.remove() // remove the token

        if(infixParser.type == OPEN_BRACKET && tokens.peekWithNewline().type != CLOSE_BRACKET) {
            tokens.shove(removed)
            break
        } // while i == b { }

        left = infixParser.runner(tokens, left, peek, infixParser.precedence /*might be 'precedence'*/)
    }
    return left
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