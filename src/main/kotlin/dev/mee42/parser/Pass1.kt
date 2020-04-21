package dev.mee42.parser

import dev.mee42.asm.ValueSize
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenType
import dev.mee42.splitBy
import java.util.*


data class Pass0Function(val name: String,
                         val arguments: List<Pass0Argument>,
                         val attributes: List<String>,
                         val returnType: List<Token>,
                         val content: List<Token>,
                         val id: String) {
    data class Pass0Argument(val name: String, val type: List<Token>, val identifierToken: Token)
}

class InitialStruct(val name: String, val fields: List<InitialField>) {
    class InitialField(val name: String, val type: List<Token>)
}

fun parsePass1(tokens: List<Token>): InitialAST {
    // so, the first token needs to a starterToken
    val queue = TokenQueue(ArrayDeque(tokens))
    val initialFunctions = mutableListOf<Pass0Function>()
    val initialStructs = mutableListOf<InitialStruct>()
    while(queue.isNotEmpty()) {
        val start = queue.peek()
        when(start.type) {
            TokenType.IDENTIFIER, TokenType.ATTRIBUTE -> {
                initialFunctions.add(initialPassParseFunction(queue))
            }
            TokenType.STRUCT_KEYWORD -> {
                initialStructs.add(parseInitialStruct(queue))
            }
            else -> throw ParseException("can't start a top-level statement with this token", start)
        }
    }
    // okay, now we can parse all the struct types
    val structs = parseStructTypes(initialStructs)
//    return InitialAST(functions = functions,structs = structs))
    val functions = initialFunctions.map { typeFunction(it, structs) }
    return InitialAST(functions = functions, structs = structs)
}

fun typeFunction(f: Pass0Function, structs: List<Struct>): InitialFunction {
    return InitialFunction(
            name = f.name,
            arguments = f.arguments.map { Argument(it.name, parseType(it.type, structs)) },
            attributes = f.attributes,
            content = f.content,
            id = f.id,
            returnType = parseType(f.returnType, structs)
    )
}


private fun initialPassParseFunction(tokens: TokenQueue): Pass0Function {
    // we already consumer the function keyword, lol
    // lets take all the attributes
    val attributes = tokens.removeWhile { it.type == TokenType.ATTRIBUTE }

    val headerPart = tokens.removeWhile { it.type != TokenType.OPEN_PARENTHESES }
    val identifier = headerPart.last().checkType(TokenType.IDENTIFIER, "function name must be an identifier")
    val returnType = headerPart.dropLast(1)
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
        return Pass0Function(name = functionName,
            arguments = parsedArguments,
            content = block,
            returnType = returnType,
            attributes = attributes.map(Token::content),
            id = "")
    } else {
        return Pass0Function(name = functionName,
            arguments = parsedArguments,
            content = block,
            returnType = returnType,
            attributes = attributes.map(Token::content),
            id = InitialFunction.genRandomID())
    }
}
private fun parseArgument(tokens: List<Token>): Pass0Function.Pass0Argument {
    // ALL TOKENS MUST BE IDENTIFIERS
    if(tokens.isEmpty()) error("whatT")
    if(tokens.size == 1) throw ParseException("missing type", tokens[0])
    val identifier = tokens.last().checkType(TokenType.IDENTIFIER, "must be an identifier")
    return Pass0Function.Pass0Argument(identifier.content, tokens.dropLast(1), identifierToken = identifier)
}

fun parseType(tokens: List<Token>, initialAST: InitialAST):Type = parseType(tokens, initialAST.structs)

fun parseType(tokens: List<Token>, structs: List<Struct>): Type {
    if(tokens.isEmpty()) throw ParseException("Can't find type")
    if(tokens.last().content in listOf("*","+")){
        return PointerType(parseType(tokens.dropLast(1), structs), tokens.last().type == TokenType.ASTERISK)
    }
    if(tokens.size > 1) throw ParseException("idk what you want, crashing", tokens.first())
    val identifier = tokens[0]
    return TypeEnum.values().firstOrNull {
        it.names.any { typeIdentifier ->
            typeIdentifier == identifier.content
        }
    }?.let { BaseType(it) } ?: throw ParseException("can't find type \"$identifier\"", identifier)
}


fun parseInitialStruct(queue: TokenQueue): InitialStruct {
    queue.remove().checkType(TokenType.STRUCT_KEYWORD, "hmmm")
    val structName = queue.remove().checkType(TokenType.IDENTIFIER, "struct name must be an identifier").content
    queue.remove().checkType(TokenType.OPEN_BRACKET,"excepting an opening bracket")
    val fields = mutableListOf<InitialStruct.InitialField>()
    while(true) {
        val next = queue.remove()
        if(next.type == TokenType.CLOSE_BRACKET) break
        val tokens = mutableListOf<Token>(next)
        while(queue.peekWithNewline().type != TokenType.NEWLINE && queue.peek().type != TokenType.SEMICOLON){
            tokens.add(queue.remove())
        }
        if(tokens.isEmpty()) continue
        if(tokens.size == 1) throw ParseException("Not sure what you're doing with a single identifier", tokens[0])
        val type = tokens.dropLast(1)
        val identifier = tokens.last().checkType(TokenType.IDENTIFIER, "expecting the variable identifier");
        fields.add(InitialStruct.InitialField(identifier.content, type))
    }
    return InitialStruct(structName, fields)
}

fun parseStructTypes(initialStructs: List<InitialStruct>): List<Struct> {
    val structs = mutableListOf<MutableStruct>()
    for(unparsed in initialStructs) {
        structs.add(MutableStruct(name = unparsed.name, fields = mutableListOf()))
    }
    for(struct in structs) {
        for(field in initialStructs.first { it.name == struct.name }.fields) {
            // parse the field
            fun parseField(tokens: List<Token>):Type {
                if(tokens.last().content in listOf("+","*")) {
                    return PointerType(parseField(tokens.dropLast(1)), tokens.last().content == "*")
                }
                if(tokens.size > 1) throw ParseException("???", tokens[0])
                if(tokens.isEmpty()) throw ParseException("???")
                val identifier = tokens.first().checkType(TokenType.IDENTIFIER, "expecting identifier").content
                return TypeEnum.values().firstOrNull { it.names.any { name -> name == identifier } }?.let { BaseType(it) }
                        ?: structs.firstOrNull { it.name == identifier }?.type
                        ?: throw ParseException("can't find type name \"$identifier\"")
            }
            struct.fields.add(Field(name = field.name, type = parseField(field.type)))

        }
    }
    val goodStructs = mutableListOf<Struct>()
    while(structs.isNotEmpty()){
        structs.forEach { it.refreshSize() }
        for(struct in structs.toList()) {
            if(struct.refreshSize()) {
                // if it's successfull
                goodStructs.add(struct)
                structs.remove(struct)
            }
        }
    }
    return goodStructs
}


interface Struct {
    val name: String
    val fields: List<Field>
    val type: StructType
    val size: ValueSize
}
class Field(val name: String, val type: Type)

class MutableStruct(override val name: String, override val fields: MutableList<Field>): Struct {
    override var size = ValueSize(-1)
    fun refreshSize(): Boolean {
        if(size.bytes != -1) return true
        if(fields.any { it.type.size.bytes == -1 }) return false
        size = ValueSize(fields.sumBy { it.type.size.bytes })
        return true
    }
    override val type = StructType(this)
}