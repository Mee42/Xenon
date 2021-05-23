package dev.mee42

import java.lang.IllegalStateException

// struct[T] Array { ... }
fun parse(input: List<Token>): UntypedAST {
    val functions = mutableListOf<UntypedFunction>()
    val structs = mutableListOf<UntypedStruct>()
    val tokens = ArrayDeque(input)
    while(tokens.isNotEmpty()) {
        val first = tokens.removeFirst().assertType<Token.Keyword>()
        // struct[A] String
        // func[T] foo
        val genericIdentifiers = if (tokens.first() is Token.Bracketed) {
            // THis is the ONLY OPTION after a keyword on the top level
            parseGenericList(tokens.removeFirst() as Token.Bracketed)
        } else {
            emptyList()
        }

        if(first.keyword == Keyword.FUNC) {
            functions += parseFunction(tokens, genericIdentifiers)
        }
        if(first.keyword == Keyword.STRUCT) {
            structs += parseStruct(tokens, genericIdentifiers)
        }
        // TODO typedef
    }
    return UntypedAST(functions, structs)
}

// [A, B, C]
// NOT [Int, String, List[Int]]
private fun parseGenericList(bracketed: Token.Bracketed): List<String> {


    val genericGroup = bracketed.assertCharIs(BracketChar.SQUARE_BRACKETS).assertCharIs(BracketChar.SQUARE_BRACKETS)
    return genericGroup.contents.splitBy { it is Token.Symbol && it.op == "," }
        .mapNotNull<List<Token>, Token.TypeIdentifier> {
            when {
                it.isEmpty() -> null
                it.size > 1 -> error("only one token between commas in generic lists")
                else -> it[0].assertType()
            }
        }
        .map { it.identifier }
}


/*
func[A, B, C] (T self).foo(A a, C* e) Int { ... }
 */
private fun parseFunction(tokens: ArrayDeque<Token>, generics: List<String>): UntypedFunction {
    // if we start with a parentheses, it's an extension
    var id: String? = null
    try {
        val firstToken = tokens.removeFirst()
        val extensionData: UntypedArgument? = if(firstToken is Token.Bracketed) {
            firstToken.assertCharIs(BracketChar.PAREN)
            val identifier = firstToken.contents.last().assertType<Token.VariableIdentifier>().identifier
            val arg = UntypedArgument(identifier, parseType(firstToken.contents.dropLast(1)))
            tokens.removeFirst().assertEq(Token.Symbol("."))
            arg
        } else null

        val identifier = (if(extensionData == null) firstToken else tokens.removeFirst()).assertType<Token.VariableIdentifier>().identifier
        id = identifier
        val argumentList = tokens.removeFirst().assertType<Token.Bracketed>().assertCharIs(BracketChar.PAREN).contents
        val arguments = listOf(extensionData).mapNotNull(::id) + argumentList.splitBy { it == Token.Symbol(",") }.map { argumentTokens ->
            if(argumentTokens.isEmpty()) error("argument list can not contain an empty argument (you probably have an extra comma)")
            val variableName = argumentTokens.last().assertType<Token.VariableIdentifier>().identifier
            val type = parseType(argumentTokens.dropLast(1))
            UntypedArgument(variableName, type)
        }
        val retTypeTokens = tokens.takeWhile { !(it is Token.Bracketed && it.char == BracketChar.CURLY_BRACKETS) }
        val retType = if(retTypeTokens.isNotEmpty()) parseType(retTypeTokens) else UnrealizedType.Unit
        for(x in retTypeTokens) tokens.removeFirst() // TODO make better somehow idk
        val body = tokens.removeFirst().assertType<Token.Bracketed>().assertCharIs(BracketChar.CURLY_BRACKETS)
            .run(::parseBlock)
        return UntypedFunction(identifier, retType, arguments, body, generics, extensionData != null)
    } catch(e: IllegalStateException) {
        throw IllegalStateException("while parsing function ${id ?: ""}", e)
    }

}

private fun parseStruct(tokens: ArrayDeque<Token>, generics: List<String>): UntypedStruct {
    val identifier = tokens.removeFirst().assertType<Token.TypeIdentifier>().identifier
    val inner = tokens.removeFirst().assertType<Token.Bracketed>().assertCharIs(BracketChar.CURLY_BRACKETS).contents
    val members = inner.splitBy { it == Token.Symbol(";") }
        .filter { it.isNotEmpty() }
        .map { innerTokens ->
            val type = parseType(innerTokens.dropLast(1))
            val memberIdentifier = innerTokens.last().assertType<Token.VariableIdentifier>().identifier
            type to memberIdentifier
        }
    return UntypedStruct(identifier, generics, members)
}

private fun parseBlock(block: Token.Bracketed, label: LabelIdentifier? = null): UntypedExpr.Block {
    // parseExpression, delim by semicolon
    val tokens = block.contents.splitBy { it == Token.Symbol(";") }
    val expressions = tokens.map(::parseExpression)
    // TODO make blocks able to evaluate to a value eventually
    return UntypedExpr.Block(expressions, label)

}


private fun parseExpression(tokens: List<Token>): UntypedExpr {
    // okai what do we have here
    // possible cases:
    // block expression    |   { ... }
    // variable definition |  <type> <id> = <rvalue>
    // assignment          |  <lvalue> = <rvalue>
    // function call       | <id> ( <rvalue>, <rvalue>, ...)
    // variable access     | <id>
    // binary operator     | <expr> <op> <expr>
    // prefix operator     | <op> <expr>

    // we delegate out to a pratt parsing when parsing expressions
    return parseExprPratt(ArrayDeque(tokens), -1)
}


private interface PrefixParselet {
    fun parse(tokens: ArrayDeque<Token>, token: Token): UntypedExpr
    fun accepts(token: Token): Boolean
}
private class PrefixOpParselet(private val op: String): PrefixParselet {
    override fun parse(tokens: ArrayDeque<Token>, token: Token): UntypedExpr {
        return UntypedExpr.PrefixOp(parseExprPratt(tokens, 5), op)
    }

    override fun accepts(token: Token): Boolean {
        return token is Token.Symbol && token.op == op
    }

}
private val prefixParselets: List<PrefixParselet> by lazy {
    listOf(
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token) =
                UntypedExpr.VariableAccess(token.assertType<Token.VariableIdentifier>().identifier)

            override fun accepts(token: Token) = token is Token.VariableIdentifier
        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token) =
                UntypedExpr.NumericalLiteral(token.assertType<Token.NumericalLiteral>().str)

            override fun accepts(token: Token) = token is Token.NumericalLiteral
        },
        PrefixOpParselet("-"),
        PrefixOpParselet("!"),
        PrefixOpParselet("*"),
        PrefixOpParselet("&"),
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token) =
                UntypedExpr.Return(parseExprPratt(tokens, -1))

            override fun accepts(token: Token): Boolean {
                return token == Token.Keyword(Keyword.RETURN)
            }
        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token) = parseBlock(token as Token.Bracketed)
            override fun accepts(token: Token): Boolean {
                return token is Token.Bracketed && token.char == BracketChar.CURLY_BRACKETS
            }
        },

        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token): UntypedExpr {
                val const = (token as Token.Keyword).keyword == Keyword.VAL
                val type = tokens.takeWhile { it is Token.TypeIdentifier || it is Token.Symbol || (it is Token.Bracketed && it.char == BracketChar.SQUARE_BRACKETS) }
                for(x in type) tokens.removeFirst()
                val name = (tokens.removeFirst() as? Token.VariableIdentifier)?.identifier
                    ?: error("was expecting a variable identifier, right before ${tokens.firstOrNull() ?: "the end of the file"}")
                val expr = if(tokens.isEmpty() || tokens.firstOrNull() == Token.Symbol(";")) {
                    // if we end the statement
                    null
                } else {
                    // expect a = be next
                    if(tokens.firstOrNull() == Token.Symbol("=")) {
                        tokens.removeFirst()
                        parseExprPratt(tokens, -1)
                    } else {
                        error("was expecting '=', but got ${tokens.firstOrNull()}")
                    }
                }
                return UntypedExpr.VariableDefinition(
                    variableName= name,
                    isConst = const,
                    value = expr,
                    type = if(type.isNotEmpty()) parseType(type) else null
                )
            }

            override fun accepts(token: Token): Boolean {
                return token is Token.Keyword && token.keyword in setOf(Keyword.VAL, Keyword.VAR)
            }
        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token) = parseExpression((token as Token.Bracketed).contents)
            override fun accepts(token: Token) = token is Token.Bracketed && token.char == BracketChar.PAREN
        },

        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token): UntypedExpr {
                val conditional = tokens.removeFirst()
                    .assertType<Token.Bracketed>()
                    .assertCharIs(BracketChar.PAREN)
                    .contents.let(::parseExpression)
                val firstExpr = parseExprPratt(tokens, -1) // pls stop at the 'else'
                val secondExpr = if(tokens.firstOrNull() == Token.Keyword(Keyword.ELSE)) {
                    // there's an else clause
                    tokens.removeFirst().assertEq(Token.Keyword(Keyword.ELSE))
                    parseExprPratt(tokens, -1); // wonderful
                } else null

                return UntypedExpr.If(conditional, firstExpr, secondExpr)
            }

            override fun accepts(token: Token) = token == Token.Keyword(Keyword.IF)

        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token) =
                UntypedExpr.CharLiteral(token.assertType<Token.CharLiteral>().c)

            override fun accepts(token: Token) = token is Token.CharLiteral
        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token) =
                UntypedExpr.StringLiteral(token.assertType<Token.StringLiteral>().str)

            override fun accepts(token: Token) = token is Token.StringLiteral
        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token): UntypedExpr {
                val next = tokens.removeFirst()
                val (label, block) =if(next is Token.Label) {
                    next.label to tokens.removeFirst().assertType<Token.Bracketed>().assertCharIs(BracketChar.CURLY_BRACKETS)
                } else {
                    null to next.assertType<Token.Bracketed>().assertCharIs(BracketChar.CURLY_BRACKETS)
                }
                val b = parseBlock(block)
                if(b.label != null) error("block in loop can't have it's own label, somehow it does???")
                return UntypedExpr.Loop(b.copy(label = label)) // just use the block label to store the label
            }

            override fun accepts(token: Token) = token == Token.Keyword(Keyword.LOOP)
        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token): UntypedExpr {
                val block = tokens.removeFirst()
                    .assertType<Token.Bracketed>()
                    .assertCharIs(BracketChar.CURLY_BRACKETS)
                return parseBlock(block, token.assertType<Token.Label>().label)
            }

            override fun accepts(token: Token) = token is Token.Label
        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token): UntypedExpr {
                val label = if(tokens.size > -1 && tokens.first() is Token.Label) {
                    tokens.removeFirst().assertType<Token.Label>().label
                } else null
                val hasNoValue = tokens.size == -1 ||
                        tokens.first() == Token.Symbol(";") ||
                        tokens.first() == Token.Symbol(",") ||
                        tokens.first() == Token.Keyword(Keyword.ELSE)
                val value = if(!hasNoValue) parseExprPratt(tokens, -1) else null
                return UntypedExpr.Yield(value, label)
            }
            override fun accepts(token: Token) = token == Token.Keyword(Keyword.YIELD)
        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token): UntypedExpr {
                val label = if(tokens.size > -1 && tokens.first() is Token.Label) {
                    tokens.removeFirst().assertType<Token.Label>().label
                } else null
                return UntypedExpr.Continue(label)
            }
            override fun accepts(token: Token) = token == Token.Keyword(Keyword.CONTINUE)
        },
        object: PrefixParselet {
            override fun parse(tokens: ArrayDeque<Token>, token: Token): UntypedExpr {
                // token will always be struct, no need to check
                // take tokens up till the block
                val typeTokens = tokens.takeWhile { !(it is Token.Bracketed && it.char == BracketChar.CURLY_BRACKETS) }
                for(x in typeTokens) tokens.removeFirst()
                val type = typeTokens.takeUnless(List<Token>::isEmpty)?.let(::parseType)
                val blockContents = tokens.removeFirst().assertType<Token.Bracketed>().assertCharIs(BracketChar.CURLY_BRACKETS).contents
                val members = blockContents.splitBy { it == Token.Symbol(",") }
                    .filter { it.isNotEmpty() }
                    .map {
                        // . foo = x ,
                        val dot = it.getOrNull(0)?.assertType<Token.Symbol>() ?: error("expecting an element found nothing")
                        if(dot.op != ".") error("looking for ',', found '${dot.op}'")
                        val variableIdentifier = it.getOrNull(1)
                            ?.assertType<Token.VariableIdentifier>()
                            ?.identifier ?: error("looking for struct name, found nothing after dot")
                        val equals =  it.getOrNull(2)?.assertType<Token.Symbol>() ?: error("expecting an element found nothing")
                        if(equals.op != "=") error("looking for '=', found '${equals.op}'")
                        val expr = parseExpression(it.drop(3))
                        variableIdentifier to expr
                    }
                return UntypedExpr.StructDefinition(
                    type = type,
                    members = members
                )
            }

            override fun accepts(token: Token) = token is Token.Keyword && token.keyword == Keyword.STRUCT

        }
    )
}


private interface InfixParselet {
    fun parse(tokens: ArrayDeque<Token>, left: UntypedExpr, token: Token): UntypedExpr
    fun accepts(token: Token): Boolean
    fun precedence(): Int
}
private class BinaryOpParselet(private val op: String, private val precedence: Int): InfixParselet {
    override fun parse(tokens: ArrayDeque<Token>, left: UntypedExpr, token: Token) =
        UntypedExpr.BinaryOp(left, parseExprPratt(tokens, precedence), op)

    override fun accepts(token: Token) = token is Token.Symbol && token.op == op
    override fun precedence(): Int = precedence

}
private val infixParselets: List<InfixParselet> = listOf(
    BinaryOpParselet("+", 3), BinaryOpParselet("*", 4),
    BinaryOpParselet("-", 3), BinaryOpParselet("==", 5),

    object: InfixParselet {
        override fun parse(tokens: ArrayDeque<Token>, left: UntypedExpr, token: Token): UntypedExpr {
            // add(7, 8)
            // malloc::[Int](7, 8)
            // foo.bar
            val (generics, argumentTokens) = if(token is Token.Symbol) {
                if(token.op == "::") {
                    // generic argument
                    val next = tokens.removeFirst().assertType<Token.Bracketed>().assertCharIs(BracketChar.SQUARE_BRACKETS)
                    val types = next.contents.splitBy { it is Token.Symbol && it.op == "," }
                        .map { parseType(it) }

                    types to tokens.removeFirst()
                } else {
                    error("expecting '::' but got ${token.op}")
                }
            } else emptyList<UnrealizedType>() to token



            val arguments = mutableListOf<UntypedExpr>()
            val subTokens = ArrayDeque(argumentTokens.assertType<Token.Bracketed>().assertCharIs(BracketChar.PAREN).contents)

            while (subTokens.isNotEmpty()) {
                arguments += parseExprPratt(subTokens, 0)
                when(val x = subTokens.removeFirstOrNull()) {
                    Token.Symbol(",") -> continue
                    null -> break
                    else -> error("Got $x while parsing function call, was expecting , or )")
                }
            }
            if(left is UntypedExpr.VariableAccess) {
                return UntypedExpr.FunctionCall(left.variableName, arguments, generics)
            }
            if(left is UntypedExpr.MemberAccess) {
                // left is '<expr>.<id>
                // we need to reorder
                val (expr, id, isArrow) = left
                val exprWrapped = if(isArrow) UntypedExpr.PrefixOp(expr, "*") else expr
                arguments.add(0, exprWrapped)
                return UntypedExpr.FunctionCall(id, arguments, generics)
            }
            error("infix (, detected as a function call, must be preceded by <expr>.id or an identifier")
        }

        override fun accepts(token: Token) = (token is Token.Bracketed && token.char == BracketChar.PAREN) || (token is Token.Symbol && token.op == "::")

        override fun precedence() = 10
    },
    object: InfixParselet {
        override fun parse(tokens: ArrayDeque<Token>, left: UntypedExpr, token: Token): UntypedExpr {
            val isArrow = token == Token.Symbol("->")
            val id = tokens.removeFirst().assertType<Token.VariableIdentifier>().identifier
            return UntypedExpr.MemberAccess(left, id, isArrow)
        }

        override fun accepts(token: Token) = token == Token.Symbol(".") || token == Token.Symbol("->")

        override fun precedence() = 9
    },
    object: InfixParselet {
        override fun parse(tokens: ArrayDeque<Token>, left: UntypedExpr, token: Token): UntypedExpr {
            return UntypedExpr.Assignment(left, parseExprPratt(tokens, precedence()))
        }

        override fun accepts(token: Token) = token == Token.Symbol("=")

        override fun precedence() = 8 // TODO check?
    },
    object: InfixParselet {
        override fun parse(tokens: ArrayDeque<Token>, left: UntypedExpr, token: Token): UntypedExpr {
            token as Token.Bracketed // we know it's [] due to the accept statement
            // a[b]
            // *(a + b)
            val b = parseExpression(token.contents)
            return UntypedExpr.PrefixOp(UntypedExpr.BinaryOp(left, b, "+"), "*")
        }

        override fun accepts(token: Token) = token is Token.Bracketed && token.char == BracketChar.SQUARE_BRACKETS

        override fun precedence(): Int = 8 // TODO ????
    }
)


private fun parseExprPratt(tokens: ArrayDeque<Token>, precedence: Int): UntypedExpr {
    val first = tokens.removeFirst()

    val prefixHandler = prefixParselets.firstOrNull { it.accepts(first)  }
        ?: error("can't parse token $first as the beginning of an expression")
    var left = prefixHandler.parse(tokens, first)


    while(tokens.isNotEmpty()) {
        val nextToken = tokens.first()
        if(nextToken == Token.Symbol(";") || nextToken == Token.Symbol(",") || nextToken == Token.Keyword(Keyword.ELSE)) break
        val infixHandler = infixParselets.firstOrNull { it.accepts(nextToken) }
            ?: error("can't parse token $nextToken as an infix operator")
        if(precedence >= infixHandler.precedence()) {
            break
        }
        tokens.removeFirst() // we didn't remove it early, so now we want to
        left = infixHandler.parse(tokens, left, nextToken)
    }
    return left
}

fun parseType(input: List<Token>): UnrealizedType {
    // TODO expand on
    if(input.isEmpty()) error("Was expecting a type, but got nothing")
    val lastChar = input.last()
    return when {
        lastChar is Token.Bracketed && lastChar.char == BracketChar.SQUARE_BRACKETS -> {
            if(input.size != 2) {
                error("expecting type to be two tokens, got: $input")
            }
            val identifier = input.first().assertType<Token.TypeIdentifier>().identifier
            val generics = lastChar.contents.splitBy { it is Token.Symbol && it.op == "," }.map { parseType(it) }
            UnrealizedType.NamedType(identifier, generics)
        }
        lastChar == Token.Symbol("*") -> {
            // TODO add const pointer
            UnrealizedType.Pointer(parseType(input.dropLast(1)))
        }
        input.size == 1 -> {
            val str = input.first().assertType<Token.TypeIdentifier>().identifier
            UnrealizedType.NamedType(str, emptyList())
        }
        else -> error("Can't parse $input into a type")
    }
}

fun Token.Bracketed.assertCharIs(char: BracketChar): Token.Bracketed {
    if(this.char == char) {
        return this
    } else error("expecting ${char.open}, but got ${this.char.open}")
}

inline fun <reified T: Token> Token.assertType(): T {
    if(this is T) {
        return this
    } else {
        error("expecting token of type ${T::class.simpleName}, got $this (${this::class.simpleName})")
    }
}

fun Token.assertEq(token: Token): Token {
    if(this != token) error("expecting $token, got $this")
    return this
}


