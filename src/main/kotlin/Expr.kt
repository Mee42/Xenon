package dev.mee42

sealed class Expr(val type: Type) {
    class Block(val label: LabelIdentifier, val contents: List<Expr>, t: Type): Expr(t)
    class Return(val expr: Expr): Expr(expr.type)
    class NumericalLiteral(val i: Int, val t: Type.BuiltinInteger): Expr(t) // TODO add more
    class StringLiteral(val content: String): Expr(Type.Pointer(Type.Builtin.CHAR))
    class CharLiteral(val char: Char): Expr(Type.Builtin.CHAR)
    class BinaryOp(val left: Expr, val right: Expr, val op: Operator, t: Type): Expr(t)
    class FunctionCall(val header: FunctionHeader, val arguments: List<Expr>): Expr(header.returnType)
    class VariableDefinition(val variableName: VariableIdentifier, val value: Expr, val isConst: Boolean, t: Type): Expr(t)
    class VariableAccess(val variableName: VariableIdentifier, val isConst: Boolean, t: Type): Expr(t)
    class StructDefinition(val t: Type.Struct, val members: List<Pair<VariableIdentifier, Expr>>): Expr(t)
    class If(val cond: Expr, val ifBlock: Expr, val elseBlock: Expr?, t: Type): Expr(t)
    class MemberAccess(val expr: Expr, val memberName: VariableIdentifier, val isArrow: Boolean, t: Type): Expr(t)
    class Ref(val expr: Expr): Expr(Type.Pointer(expr.type))
    class Deref(val expr: Expr, t: Type): Expr(t) {
        init { if(Type.Pointer(t) != expr.type) error("ICE") }
    }
    class Assignment(val left: Expr, val right: Expr): Expr(left.type) {
        init { if(left.type != right.type) error("ICE") }
    }
}

fun Expr.isLValue(): Boolean = when(this) {
    is Expr.Deref -> true
    is Expr.VariableAccess -> true
    is Expr.MemberAccess -> isArrow || expr.isLValue()
    else -> false
}

enum class Operator(val op: String){
    ADD("+"), SUB("-"), TIMES("*"), DIVIDE("/"),
    EQUALS("=="),NOT_EQUALS("!=")
}




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

fun integralArithmetic(verb: String, leftType: Type.BuiltinInteger, rightType: Type.BuiltinInteger): Type.BuiltinInteger {
    // helper function for returnType

    if(leftType.signed != rightType.signed) error("Can't $verb types $leftType and $rightType as one is unsigned")
    // okay then we'll just cast it up to the highest type
    return if(leftType.size > rightType.size) leftType else rightType // re
}
// throws an error on
fun returnType(op: Operator, leftType: Type, rightType: Type): Type = when(op){
    Operator.ADD ->  {
        if(leftType is Type.BuiltinInteger && rightType is Type.BuiltinInteger) {
            // okay, both integers
            integralArithmetic("add", leftType, rightType)
        } else if(leftType is Type.Pointer && rightType is Type.Pointer) {
            error("Can't add two pointer $leftType and $rightType")
        } else if(leftType is Type.Pointer || rightType is Type.Pointer) {
            val ptrType = if(leftType is Type.Pointer) leftType else (rightType as Type.Pointer)
            val intType = if(leftType is Type.Pointer) rightType else leftType
            if(intType !is Type.BuiltinInteger) error("Can't support adding a pointer $ptrType to a non-integer type $intType")
            if(intType.size > 2) error("Can't add a pointer to type $intType as $intType is bigger than a pointer") // todo make better
            ptrType
        } else {
            error("no code has been written that handles the $leftType + $rightType case")
        }
    }
    Operator.SUB -> {
        if(leftType is Type.BuiltinInteger && rightType is Type.BuiltinInteger) {
            integralArithmetic("subtract", leftType, rightType)
        } else if(leftType is Type.Pointer && rightType is Type.Pointer) {
            // only valid if both inner types are valid, and the return type is Int
            if(leftType.inner != rightType.inner) error("Can't subtract pointer types $leftType and $rightType")
            Type.Builtin.INT
        } else if(leftType is Type.Pointer || rightType is Type.Pointer) {
            val ptrType = if(leftType is Type.Pointer) leftType else (rightType as Type.Pointer)
            val intType = if(leftType is Type.Pointer) rightType else leftType
            if(intType !is Type.BuiltinInteger) error("Can't support subtracting a pointer $ptrType to a non-integer type $intType")
            if(intType.size > 2) error("Can't subtract a pointer to type $intType as $intType is bigger than a pointer") // todo make better
            ptrType
        } else {
            error("no code has been written that handles the $leftType - $rightType case")
        }
    }
    Operator.TIMES -> {
        if(leftType is Type.BuiltinInteger && rightType is Type.BuiltinInteger) {
            integralArithmetic("multiply", leftType, rightType)
        } else {
            error("no code has been written that handles the $leftType * $rightType case")
        }
    }
    Operator.DIVIDE -> {
        if(leftType is Type.BuiltinInteger && rightType is Type.BuiltinInteger) {
            integralArithmetic("divide", leftType, rightType)
        } else {
            error("no code has been written that handles the $leftType * $rightType case")
        }
    }
    Operator.EQUALS, Operator.NOT_EQUALS -> {
        if(leftType != rightType) {
            error("Can't compare types $leftType and $rightType for equality")
        } else {
            Type.Builtin.BOOL
        }
    }
}

// will mutate scope if a variable is defined
private fun Env.compileExpr(expr: UntypedExpr, scope: Scope): Expr = when(expr){
    is UntypedExpr.BinaryOp -> {
        val left = compileExpr(expr.left, scope)
        val right = compileExpr(expr.right, scope)
        val op = Operator.values().firstOrNull { it.op == expr.op } ?: error("unknown operator ${expr.op}")
        val retType = returnType(op, left.type, right.type)
        Expr.BinaryOp(left, right, op, retType)
    }
    is UntypedExpr.CharLiteral -> Expr.CharLiteral(expr.char)
    is UntypedExpr.FunctionCall -> {

        // INTRINSIC:
        if(expr.functionName == "sizeof") {
            if(expr.generics.size != 1) error("sizeof function needs to take in one generic argument, not " + expr.generics.size)
            val type = typeMapper(expr.generics[0])
            val size = getSizeForType(type)
            Expr.NumericalLiteral(size, Type.Builtin.UINT) // MARK consider: the size of objects is always UInt?
        } else {

            // grab the function we need
            val genericFunction =
                functions[expr.functionName] ?: error("Can't find function called ${expr.functionName}")
            val generics = expr.generics.map { typeMapper(it) }
            if(genericFunction.generics.size != generics.size) {
                error("calling function ${genericFunction.name}, expecting generic arguments ${genericFunction.generics} but found $generics, sizes did not match")
            }
            val specializedFunction = specializeFunction(genericFunction, generics)

            val arguments = expr.arguments.map { compileExpr(it, scope) }
            for ((real, expected) in arguments.map { it.type }.zip(specializedFunction.arguments.map { it.type })) {
                if (real != expected) error("in function arguments, expected expression to be of type $expected, but got type of $real")
            }
            Expr.FunctionCall(specializedFunction, arguments)
        }
    }
    is UntypedExpr.NumericalLiteral -> {
        // postfix
        val n = expr.number
        val (dropCount, type) = when { // only extract type info
            n.endsWith("ui") -> 2 to Type.Builtin.UINT
            n.endsWith("ub") -> 2 to Type.Builtin.UBYTE
            n.endsWith("ul") -> 2 to Type.Builtin.ULONG
            n.endsWith("u") -> 1 to Type.Builtin.UINT
            n.endsWith("i") -> 1 to Type.Builtin.INT
            n.endsWith("l") -> 1 to Type.Builtin.LONG
            n.endsWith("b") -> 1 to Type.Builtin.BYTE
            else -> 0 to Type.Builtin.INT
        }
        Expr.NumericalLiteral(n.dropLast(dropCount).toInt(), type)
    }
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
        val writtenType = expr.type?.let(typeMapper)
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
    is UntypedExpr.If -> {
        // TODO make Bool type
        val cond = compileExpr(expr.cond, scope)
        val ifBlock = compileExpr(expr.ifBlock, scope)
        val elseBlock = if(expr.elseBlock != null) compileExpr(expr.elseBlock, scope) else null
        if(cond.type != Type.Builtin.BOOL) error("Condition must return value of type Bool, not ${cond.type.str()}")
        val type = when {
            elseBlock == null -> Type.Unit
            elseBlock.type != ifBlock.type -> Type.Unit// TODO figure out what to do here
            else -> ifBlock.type
        }
        Expr.If(cond, ifBlock, elseBlock, type)
    }
    // NEED TO DO
    is UntypedExpr.MemberAccess -> {
        val left = compileExpr(expr.expr, scope)

        val structType = if(expr.isArrow) {
            if(left.type !is Type.Pointer){
                error("Can't use -> when type is not a pointer. Got type ${left.type.str()} instead")
            }
            left.type.inner
        } else {
            left.type
        }
        if(structType !is Type.Struct) {
            error("can't do ${if(expr.isArrow) "->" else "."}${expr.memberName} on non-struct expression. Got value of type " + left.type.str())
        }
        val fields = fieldsFor(this.structs[structType.name] ?: error("ICE, can't find struct with name"), structType)
        val (_, fieldType) = fields
            .firstOrNull { (name, _) -> name == expr.memberName }
            ?: error("Can't find member ${expr.memberName} on struct of type ${structType.str()}")
        // TODO make sure that the struct is writable, or smth

        Expr.MemberAccess(left, expr.memberName, expr.isArrow, fieldType)
    }
    is UntypedExpr.Assignment -> {
        val left = compileExpr(expr.left, scope)
        val right = compileExpr(expr.right, scope)
        if(!left.isLValue()) {
            error("Can't assign value to rvalue")
        }
        if(left.type != right.type) {
            error("Can't assign value of type ${right.type.str()} to lvalue of type ${left.type.str()}")
        }
        Expr.Assignment(left, right)
    }
    is UntypedExpr.PrefixOp -> {
        val right = compileExpr(expr.right, scope)
        when (expr.op) {
            "*" -> {
                if(right.type !is Type.Pointer) {
                    error("Can't dereferencee expression of type ${right.type.str()}, must be pointer type")
                }
                Expr.Deref(right, right.type.inner)
            }
            "&" -> {
                if(!right.isLValue()) {
                    error("Can't dereference rvalue expression $right")
                }
                Expr.Ref(right)
            }
            else -> TODO("not sure what that prefix operator is")
        }
    }


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