package dev.mee42

sealed class Expr(val type: Type) {
    sealed interface LValue
    class Block(val label: LabelIdentifier, val contents: List<Expr>, t: Type): Expr(t)
    class Return(val expr: Expr): Expr(expr.type)
    class NumericalLiteral(val i: Int): Expr(Type.Builtin("Int")) // TODO add more
    class StringLiteral(val content: String): Expr(Type.Pointer(Type.Builtin("Char")))
    class CharLiteral(val char: Char): Expr(Type.Builtin("Char"))
    class BinaryOp(val left: Expr, val right: Expr, val op: Operator, t: Type): Expr(t)
    class FunctionCall(val header: FunctionHeader, val arguments: List<Expr>): Expr(header.returnType)
    class VariableDefinition(val variableName: VariableIdentifier, val value: Expr, val isConst: Boolean, t: Type): Expr(t)
    class VariableAccess(val variableName: VariableIdentifier, val isConst: Boolean, t: Type): Expr(t), LValue
    class StructDefinition(val t: Type.Struct, val members: List<Pair<VariableIdentifier, Expr>>): Expr(t)
    class If(val cond: Expr, val ifBlock: Expr, val elseBlock: Expr?, t: Type): Expr(t)
}
enum class Operator(val op: String){ ADD("+"), SUB("-"), TIMES("*"), DIVIDE("/") }




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
                         specializeFunction: (GenericFunction, List<Type>) -> FunctionHeader,
                         arguments: List<Argument>): Expr.Block {

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

    return ast.compileFunctionBlock(block, arguments)
}

private fun Env.compileFunctionBlock(block: UntypedExpr.Block, arguments: List<Argument>): Expr.Block {
    // TODO label and shit support
    //     at the moment we'll just just... idk
    // we'll make the return type of block the same as all the top-level return statements
    // TODO make this support yield/return/shit
    val scope = Scope(
        variables = arguments.map { (name, type) -> Variable(name, type, true) }.toMutableList(),
        null
    )
    return compileBlock(block, scope)
}
private fun Env.compileBlock(block: UntypedExpr.Block, scope: Scope): Expr.Block {
    // TODO same as above - label support
    val newScope = Scope(mutableListOf(), scope)
    val statements = block.sub.map { compileExpr(it, newScope) }

    val retTypes = statements.filterIsInstance<Expr.Return>().map { it.type }
    val retType = if (retTypes.isEmpty()) {
        Type.Unit// TODO we know this is going to be like this, just ignore for now, do later
    } else {
        for(i in retTypes) if(i != retTypes[0]) error("conflicting return types, got both $i and ${retTypes[0]}")
        retTypes[0]
    }

    return Expr.Block(block.label ?: error("no block identifier?"), statements, retType)
}


private data class Scope(val variables: MutableList<Variable>, val parent: Scope?) {
    fun lookupVariable(name: VariableIdentifier): Variable? = variables.firstOrNull { it.name == name } ?: parent?.lookupVariable(name)
}
data class Variable(val name: VariableIdentifier, val type: Type, val isConst: Boolean)

// will mutate scope if a variable is defined
private fun Env.compileExpr(expr: UntypedExpr, scope: Scope): Expr = when(expr){
    is UntypedExpr.BinaryOp -> {
        val left = compileExpr(expr.left, scope)
        val right = compileExpr(expr.right, scope)
        when (expr.op) {
            // this is going to need some work
            "+", "-", "*", "/" -> {
                val verb = when(expr.op) { "+" -> "add"; "-" -> "subtract"; "*" -> "multiply"; "/" -> "divide"; else -> error("ICE")}
                if (left.type != right.type) error("Can't $verb values of types ${left.type} and ${right.type} together")
                if (left.type != Type.Builtin("Int")) error("Attempting to $verb type ${left.type}, only supported type is Int")
                val operator = Operator.values().first { it.op == expr.op }
                Expr.BinaryOp(left, right, operator, Type.Builtin("Int"))
            }
            else -> error("unknown binary operator " + expr.op)
        }
    }
    is UntypedExpr.CharLiteral -> Expr.CharLiteral(expr.char)
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

            val arguments = expr.arguments.map { compileExpr(it, scope) }
            for ((real, expected) in arguments.map { it.type }.zip(specializedFunction.arguments.map { it.type })) {
                if (real != expected) error("in function arguments, expected expression to be of type $expected, but got type of $real")
            }
            Expr.FunctionCall(specializedFunction, arguments)
        }
    }
    is UntypedExpr.NumericalLiteral -> Expr.NumericalLiteral(expr.number.toInt())
    is UntypedExpr.Return -> Expr.Return(compileExpr(expr.expr, scope))
    is UntypedExpr.StringLiteral -> Expr.StringLiteral(expr.content)
    is UntypedExpr.StructDefinition -> {
        val structType = this.typeMapper(expr.type ?: error("type assumption for struct definitions not supported yet"))
        if(structType !is Type.Struct) {
            error("Can't define struct with non-struct type" + structType.str())
        }
        val structName = structType.name
        val structDef = structs[structName] ?: error("no struct named $structName found")
        val expectedFields = fieldsFor(structDef, structType)
        val parsedFields = expr.members.map { (identifier, value) ->
            val subExpr = compileExpr(value, scope)
            val (_, realType) = expectedFields.firstOrNull { (name, _) -> name == identifier }
                ?: error("Struct $structName does not have a member $identifier")
            if(realType != subExpr.type) {
                error("Expecting type ${realType.str()} but got expr of type ${subExpr.type.str()} ")
            }
            identifier to subExpr
        }
        // TODO think about default initialization, zero initialization, etc
        if(parsedFields.size != expectedFields.size) {
            val missing = expectedFields
                .filter { (n, _) -> parsedFields.none { (nn, _) -> nn == n }  }
                .map{ (_, t) -> t }
            error("In struct definition ${structType.str()}, missing fields: $missing")
        }
        // make sure we covered all of them
        Expr.StructDefinition(structType, parsedFields)
    }
    is UntypedExpr.VariableDefinition -> {
        val variableName = expr.variableName
        val isConst = expr.isConst
        val writtenType = expr.type
        val value = compileExpr(expr.value ?: error("default initialization not supported yet"), scope)
        if(writtenType != null) {
            if(writtenType != value.type) {
                error("defined variable $variableName as type ${writtenType.str()} but got ${value.type.str()}")
            }
        }
        if(scope.lookupVariable(variableName) != null) {
            error("trying to define variable named $variableName when a variable with the same name is already defined")
        }
        scope.variables += Variable(variableName, value.type, isConst)
        Expr.VariableDefinition(variableName, isConst = isConst, t = value.type, value = value)
    }
    is UntypedExpr.VariableAccess -> {
        val variable = scope.lookupVariable(expr.variableName) ?: error("no variable in scope named ${expr.variableName}")
        // TODO check for constness when used as lvalue?
        Expr.VariableAccess(expr.variableName, variable.isConst, variable.type)
    }
    is UntypedExpr.Block -> compileBlock(expr, scope)
    // NEED TO DO
    is UntypedExpr.If -> {
        // TODO make Bool type
        val cond = compileExpr(expr.cond, scope)
        val ifBlock = compileExpr(expr.ifBlock, scope)
        val elseBlock = if(expr.elseBlock != null) compileExpr(expr.elseBlock, scope) else null
        if(cond.type != Type.Builtin("Int")) error("Condition must return value of type Int, not ${cond.type.str()}")
        val type = if(elseBlock == null) {
            Type.Builtin("Unit")
        } else if(elseBlock.type != ifBlock.type) {
            Type.Builtin("Unit") // TODO figure out what to do here
        } else ifBlock.type
        Expr.If(cond, ifBlock, elseBlock, type)
    }
    is UntypedExpr.MemberAccess -> TODO()
    is UntypedExpr.PrefixOp -> TODO()
    is UntypedExpr.Assignment -> TODO()


    // put off for later probably
    is UntypedExpr.Loop -> TODO("not supported yet")
    is UntypedExpr.Continue -> TODO("not supported yet")
    is UntypedExpr.Yield -> TODO("not supported yet")
}

// figures out all the fields in a struct
// based on the template specification
fun fieldsFor(struct: GenericStruct, type: Type.Struct): List<Pair<VariableIdentifier, Type>> {
    val info = struct.genericTypes.mapIndexed { index, s -> s to type.genericTypes[index] }.toMap()
    return struct.fields.map { (type, name) -> name to type.replaceGenerics(info) }
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