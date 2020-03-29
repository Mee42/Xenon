package dev.mee42.parser

import dev.mee42.ConsumableQueue
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenType
import dev.mee42.splitBy

fun parsePass1(tokens: List<Token>): InitialAST {
    // so, the first token needs to a starterToken
    val queue = ConsumableQueue(tokens)
    val functions = mutableListOf<InitialFunction>()
    while(queue.isNotEmpty()) {
        val start = queue.peek()!!
        when(start.type) {
            TokenType.IDENTIFIER -> {
                functions.add(initialPassParseFunction(queue))
            }
            TokenType.STRUCT_KEYWORD -> {
                TODO("structs are not a thing yet")
            }
            else -> throw ParseException("can't start a top-level statement with this token", start)
        }
    }
    return InitialAST(functions = functions,structs =  emptyList())
}


// function @attributes @foo @pure @const foo(int* a, int b, int c) int { list; of; expressions; }
private fun initialPassParseFunction(tokens: ConsumableQueue<Token>): InitialFunction {
    // we already consumer the function keyword, lol
    // lets take all the attributes
    val attributes = tokens.removeWhile { it.type == TokenType.ATTRIBUTE }

    val headerPart = tokens.removeWhile { it.type != TokenType.OPEN_PARENTHESES }
    val identifier = headerPart.last().checkType(TokenType.IDENTIFIER, "function name must be an identifier")
    val returnType = parseType(headerPart.dropLast(1), identifier)
    val functionName = identifier.content

    tokens.remove().checkType(TokenType.OPEN_PARENTHESES, "function identifier must be followed by a parentheses")

    val arguments = tokens.removeWhile { it.type != TokenType.CLOSE_PARENTHESES }
        .splitBy(true) { it.type == TokenType.COMMA }
        .mapIndexed { index, list ->
            if(index == 0) {
                if(list.isEmpty()) null
                else list
            } else {
                if(list.size == 1) throw ParseException("can't have empty argument list", list[0])
                list.subList(2, list.size)
            }
        }.filterNotNull()

    val parsedArguments = arguments.map(::parseArgument)
    tokens.remove().checkType(TokenType.CLOSE_PARENTHESES, "illegal state")

    val block: List<Token> = tokens.takeAllNestedIn(beginning = TokenType.OPEN_BRACKET, end = TokenType.CLOSE_BRACKET, includeSurrounding = true)
    // block contains the {} but they are removed from the queue
    if(functionName == "main" && arguments.isEmpty()){
        // it's the real name
        return InitialFunction(name = functionName,
            arguments = parsedArguments,
            content = block,
            returnType = returnType,
            attributes = attributes.map(Token::content),
            id = "")
    } else {
        return InitialFunction(name = functionName,
            arguments = parsedArguments,
            content = block,
            returnType = returnType,
            attributes = attributes.map(Token::content),
            id = InitialFunction.genRandomID())
    }
}

private fun parseArgument(tokens: List<Token>): Argument {
    // ALL TOKENS MUST BE IDENTIFIERS
    if(tokens.isEmpty()) error("whatT")
    if(tokens.size == 1) throw ParseException("missing type", tokens[0])
    val identifier = tokens.last()
    return Argument(name = identifier.checkType(TokenType.IDENTIFIER,"argument name must be an identifier").content,
        type = parseType(tokens.subList(0, tokens.size - 1), identifier))
}

fun parseType(tokens: List<Token>, nearbyToken: Token): Type {
    if(tokens.isEmpty()) throw ParseException("Can't find type", nearbyToken)
    if(tokens.last().type == TokenType.ASTERISK) {
        return PointerType(parseType(tokens.dropLast(1), tokens.last()))
    }
    if(tokens.size > 1) throw ParseException("idk what you want, crashing", nearbyToken)
    val identifier = tokens[0]
    val type: TypeEnum = TypeEnum.values().firstOrNull {
        it.names.any { typeIdentifier ->
            typeIdentifier == identifier.content
        }
    } ?: throw ParseException("can't find type \"$identifier\"", identifier)
    return BaseType(type)
}
