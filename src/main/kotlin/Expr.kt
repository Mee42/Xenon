package dev.mee42

sealed class Expr(val type: Type) {
    class Block(val label: LabelIdentifier, val contents: List<Expr>, t: Type): Expr(t)
    class Return(val expr: Expr): Expr(expr.type)
    class NumericalLiteral(val i: Int): Expr(Type.Builtin("Int")) // TODO add more
    class StringLiteral(val content: String): Expr(Type.Pointer(Type.Builtin("Char")))
    class BinaryOp(val left: Expr, val right: Expr, val op: Operator, t: Type): Expr(t)
    class FunctionCall(val header: FunctionHeader, val arguments: List<Expr>): Expr(header.returnType)
}
enum class Operator(val op: String){ ADD("+"), SUB("-") }


// passed around to every compileExpr call
private data class Env(
    // all functions, indexed by name
    val functions: Map<String, GenericFunction>,

    // all structs, indexed by name
    val structs: Map<String, GenericStruct>,

    // turn a unrealized type (for example, List[T]), into an actual Type, with generics replaced with their real values (List[Int])
    val typeMapper: (UnrealizedType) -> Type,

    // schedule the specialization of a function with a new header for a given function with some generic arguments, returns it.
    val specializeFunction: (GenericFunction, List<Type>) -> FunctionHeader
)

fun compileFunctionBlock(block: UntypedExpr.Block,
                         functions: List<GenericFunction>,
                         structs: List<GenericStruct>,
                         untypedStructs: List<UntypedStruct>, // for typing purposes
                         genericNames: Set<TypeIdentifier>, // in 'func[A, B, C] foo() {}' it's { A, B, C }
                         removeGenerics: (Type) -> Type,
                         specializeFunction: (GenericFunction, List<Type>) -> FunctionHeader): Expr.Block {

    fun typeMapper(type: UnrealizedType): Type {
        val pass1 = initialTypePass(type, untypedStructs, genericNames)
        // pass two removes all references to generic types
        val pass2 = removeGenerics(pass1)
        pass2.assertNoneGeneric()
        return pass2
    }
    val ast = Env(
        functions = functions.map { it.name to it }.toMap(),
        structs = structs.map { it.name to it }.toMap(),
        typeMapper = ::typeMapper,
        specializeFunction = specializeFunction
    )

    return ast.compileFunctionBlock(block)
}

private fun Env.compileFunctionBlock(block: UntypedExpr.Block): Expr.Block {
    // TODO label and shit support
    //     at the moment we'll just just... idk
    // we'll make the return type of block the same as all the top-level return statements
    // TODO make this support yield/return/shit

    val statements = block.sub.map { compileExpr(it) }

    val retTypes = statements.filterIsInstance<Expr.Return>().map { it.type }
    val retType = if (retTypes.isEmpty()) {
        Type.Unit
    } else {
        for(i in retTypes) if(i != retTypes[0]) error("conflicting return types, got both $i and ${retTypes[0]}")
        retTypes[0]
    }

    return Expr.Block(block.label ?: error("no block identifier?"), statements, retType)
}




private fun Env.compileExpr(expr: UntypedExpr): Expr = when(expr){
    is UntypedExpr.Assignment -> TODO()
    is UntypedExpr.BinaryOp -> {
        val left = compileExpr(expr.left)
        val right = compileExpr(expr.right)
        when (expr.op) {
            // this is going to need some work
            "+" -> {
                if (left.type != right.type) error("Can't add values of types ${left.type} and ${right.type} together")
                if (left.type != Type.Builtin("Int")) error("Attempting to add type ${left.type}, only supported type is Int")
                Expr.BinaryOp(left, right, Operator.ADD, Type.Builtin("Int"))
            }
            "-" -> {
                if (left.type != right.type) error("Can't subtract values of typees ${left.type} and ${right.type} together")
                if (left.type != Type.Builtin("Int")) error("Attempting to subtract type ${left.type}, only supported type is Int")
                Expr.BinaryOp(left, right, Operator.SUB, Type.Builtin("Int"))
            }
            else -> error("unknown binary operator " + expr.op)
        }
    }
    is UntypedExpr.Block -> TODO()
    is UntypedExpr.CharLiteral -> TODO()
    is UntypedExpr.FunctionCall -> {
        // TODO add bidirectional type assumption so generics don't always need to be specified


        // INTRINSIC:
        if(expr.functionName == "sizeof") {
            if(expr.generics.size != 1) error("sizeof function needs to take in one generic argument, not " + expr.generics.size)
            val type = typeMapper(expr.generics[0])
            val size = getSizeForType(type)
            Expr.NumericalLiteral(size)
        } else {


            // grab the function we need
            val genericFunction =
                functions[expr.functionName] ?: error("Can't find function called ${expr.functionName}")
            val generics = expr.generics.map { typeMapper(it) }
            val specializedFunction = specializeFunction(genericFunction, generics)

            val arguments = expr.arguments.map { compileExpr(it) }
            for ((real, expected) in arguments.map { it.type }.zip(specializedFunction.arguments.map { it.type })) {
                if (real != expected) error("in function arguments, expected expression to be of type $expected, but got type of $real")
            }
            Expr.FunctionCall(specializedFunction, arguments)
        }
    }
    is UntypedExpr.If -> TODO()
    is UntypedExpr.MemberAccess -> TODO()
    is UntypedExpr.NumericalLiteral -> Expr.NumericalLiteral(expr.number.toInt())
    is UntypedExpr.PrefixOp -> TODO()
    is UntypedExpr.Return -> Expr.Return(compileExpr(expr.expr))
    is UntypedExpr.StringLiteral -> Expr.StringLiteral(expr.content)
    is UntypedExpr.StructDefinition -> TODO()
    is UntypedExpr.VariableAccess -> TODO()
    is UntypedExpr.VariableDefinition -> TODO()

    is UntypedExpr.Loop -> TODO("not supported yet")
    is UntypedExpr.Continue -> TODO("not supported yet")
    is UntypedExpr.Yield -> TODO("not supported yet")
}


private fun Env.getSizeForType(type: Type): Int = when(type) {
    is Type.Builtin -> when(type.name) {
        "Int" -> 4
        "Char" -> 1
        else -> error("unknown size for type builtin " + type.name)
    }
    is Type.Generic -> error("can't get type for generic, this is probably an ICE")
    Type.Nothing -> 0
    is Type.Pointer -> 8
    is Type.Struct -> structs[type.name]!!.fields.sumOf { (t, _) -> getSizeForType(t) }
    Type.Unit -> 0
}