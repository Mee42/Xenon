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
        val start = queue.remove()
        when(start.type) {
            TokenType.FUNCTION_KEYWORD -> {
                functions.add(initialPassParseFunction(queue))
            }
            else -> throw ParseException("can't start a top-level statement with this token", start)
        }
    }
    return InitialAST(functions = functions)
}


// function @attributes @foo @pure @const foo(unsigned int a, int b, int c) int { list; of; expressions; }
private fun initialPassParseFunction(tokens: ConsumableQueue<Token>): InitialFunction {
    // we already consumer the function keyword, lol
    // lets take all the attributes
    val attributes = tokens.removeWhile { it.type == TokenType.ATTRIBUTE }

    val functionName = tokens.remove()
        .checkType(TokenType.IDENTIFIER, "function name must be an identifier").content
    val openParentheses =tokens.remove().checkType(TokenType.OPEN_PARENTHESES, "function identifier must be followed by a parentheses")

    val arguments = tokens.removeWhile { it.type != TokenType.CLOSE_PARENTHESES }
        .splitBy(true) { it.type == TokenType.COMMA }
        .mapIndexed { index, list ->
            if(index == 0) {
                if(list.isEmpty()) throw ParseException("can't have empty argument list", openParentheses)
                list
            } else {
                if(list.size == 1) throw ParseException("can't have empty argument list", list[0])
                list.subList(2, list.size)
            }
        }
    val parsedArguments = arguments.map(::parseArgument)
    val closeParentheses = tokens.remove().checkType(TokenType.CLOSE_PARENTHESES, "illegal state")
    val returnType = parseType(tokens.removeWhile { it.type != TokenType.OPEN_BRACKET }, closeParentheses)

    val block: List<Token> = tokens.takeAllNestedIn(beginning = TokenType.OPEN_BRACKET, end = TokenType.CLOSE_BRACKET, includeSurrounding = true)
    // block contains the {} but they are removed from the queue
    return InitialFunction(name = functionName,
        arguments = parsedArguments,
        content = block,
        returnType = returnType,
        attributes = attributes.map(Token::content))
}

private fun parseArgument(tokens: List<Token>): Argument {
    // ALL TOKENS MUST BE IDENTIFIERS
    if(tokens.size == 1) throw ParseException("missing type", tokens[0])
    val identifier = tokens.last()
    return Argument(name = identifier.checkType(TokenType.IDENTIFIER,"argument name must be an identifier").content,
        type = parseType(tokens.subList(0, tokens.size - 1), identifier))
}

private fun parseType(tokens: List<Token>, nearbyToken: Token? = null): Type {
    val attributes = tokens.takeWhile { it.type == TokenType.ATTRIBUTE }.map(Token::content)
    val identifiers = tokens.subList(attributes.size, tokens.size)
    if(identifiers.isEmpty()) {
        throw if(nearbyToken == null) ParseException("can't have a type with no identifiers")
        else                    ParseException("can't have a type with no identifiers",nearbyToken)
    }

    identifiers.forEach {
        it.checkType(TokenType.IDENTIFIER, "type identifiers must be identifiers. Attributes must come before any type identifiers")
    }
    // let's just find one - worry about structs later
    val type: TypeEnum = TypeEnum.values().firstOrNull {
        it.names.any { list ->
            list.size == identifiers.size && list.withIndex().all { (i, v) -> identifiers[i].content == v }
        }
    } ?: throw ParseException("can't find type \"${identifiers.fold(""){a,b -> "$a ${b.content}"}}\"", identifiers[0])
    return BaseType(type, attributes)
}

