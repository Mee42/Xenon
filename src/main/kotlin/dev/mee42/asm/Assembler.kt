package dev.mee42.asm

import dev.mee42.parser.*
import dev.mee42.parser.Function
import dev.mee42.type

fun assemble(ast: AST): Assembly {
    return ast.functions
        .filterIsInstance<XenonFunction>()
        .map { assemble(it, ast) }.fold(Assembly(emptyList(), emptyList())) { a, b -> a + b }
}
class Variable(val name: String, val type: Type, val register: AdvancedRegister)

private fun stackPointerRegister(offset: Int, size: RegisterSize): AdvancedRegister {
    return AdvancedRegister(
        register = SizedRegister(RegisterSize.BIT64, Register.BP),
        isMemory = true,
        offset = offset,
        size = size
    )
}


fun ValueSize.fitToRegister(): RegisterSize {
    if(!canFitInRegister) {
        error("can't fit in a registers, structs don't work yet")
    }
    return when(this.bytes) {
        1 -> RegisterSize.BIT8
        2 -> RegisterSize.BIT16
        4 -> RegisterSize.BIT32
        8 -> RegisterSize.BIT64
        else -> error("illegal")
    }
}

private fun assemble(function: XenonFunction, ast: AST): Assembly = buildAssembly {
    // these are the registers needed to call this function:
    val variableBindings = mutableListOf<Variable>()

//    val returnRegister = SizedRegister(function.returnType.size, Register.A)
    // so we don't use these variables on accident
//    this += AssemblyInstruction.CommentedLine(
       this += AssemblyInstruction.Label(function.identifier)
//        comment = "return value in register $returnRegister")
    // so the argument registers are
    // for now, let's fit everything into 8 bytes?
    // yeah
    // sure
    fun Int.padUpTo16(): Int {
        return if(this % 16 == 0) this
               else this + 16 - (this % 16)
    }


    val isStruct = function.returnType is StructType

    val arguments = (function.arguments +  if(isStruct) listOf(Argument("_ret_ptr", PointerType(function.returnType, true))) else listOf()).asReversed()

    var position = 16
    val comments = mutableListOf<String>()
// this actually sucks, I hate it
    arguments.forEachIndexed { i, it ->
        val register = stackPointerRegister(position, it.type.size.toSomeSize(it.type is StructType))
        variableBindings.add(Variable(it.name, it.type, register))
        position += it.type.size.bytes
        comments += "(${it.name}) in register ${register.size.asmName} $register"
    }
    comments.reversed().forEachIndexed { i, s ->
        this += AssemblyInstruction.Comment("argument $i $s")
    }

    val max = function.content.localVariableMaxBytes + function.content.maxStructSize
    val localVariableSize = max.padUpTo16()
    this += AssemblyInstruction.Push(Register.BP) // push rbp so we don't lose it when we return
    this += AssemblyInstruction.Mov(
        reg1 = SizedRegister(RegisterSize.BIT64, Register.BP).advanced(),
        reg2 = SizedRegister(RegisterSize.BIT64, Register.SP).advanced()
    )
    this += AssemblyInstruction.Sub(
        reg1 = SizedRegister(RegisterSize.BIT64, Register.SP).advanced(),
        reg2 = StaticValueAdvancedRegister(localVariableSize, RegisterSize.BIT64)
    )
    val returnInstructions = ReturnBuilder(isStruct, localVariableSize,function.returnType)
    this += assembleBlock(variableBindings, ast, function.content, -function.content.maxStructSize,-function.content.maxStructSize, returnInstructions)
}


class ReturnBuilder(private val isStruct: Boolean, private val localVariableSize: Int, private val retType: Type) {
    fun assembleReturn(expr: Expression, s: ExpressionState): Assembly = buildAssembly {
        if(isStruct) {
            if(retType !is StructType) error("expecting struct type")
            // we want to copy the bytes at rax to the pointer that rbx points to
            val xpr = AssigmentExpression(
                    setLocation = DereferencePointerExpression(VariableAccessExpression("_ret_ptr", PointerType(retType, true), false)),
                    value = expr
            )
            this += assembleExpression(xpr, s) // just copy the bytes like a normal transfer
            this += AssemblyInstruction.Custom("")
        } else {
            this += assembleExpression(expr, s) // put the result in the rax register, then return
        }
        this += AssemblyInstruction.Add(
                reg1 = SizedRegister(RegisterSize.BIT64, Register.SP).advanced(),
                reg2 = StaticValueAdvancedRegister(localVariableSize, RegisterSize.BIT64)
        )
        this += AssemblyInstruction.Pop(Register.BP)
        this += AssemblyInstruction.Ret
    }
}


fun assembleBlock(variableBindings: List<Variable>,
                          ast: AST,
                          block: Block,
                          topLocal: Int,
                          structLocal: Int,
                          returnInstructions: ReturnBuilder): Assembly  = buildAssembly {
    val localVariables = mutableListOf<Variable>()
    var localVariableLocation = topLocal
    for(statement in block.statements) {
        this += AssemblyInstruction.Comment("statement $statement")
        when (statement) {
            is ReturnStatement -> {
//                val expression = statement.expression
//                this += assembleExpression(variableBindings + localVariables, ast, expression, localVariableLocation, structLocal, returnInstructions)
                this += returnInstructions.assembleReturn(statement.expression, ExpressionState(variableBindings + localVariables, ast, localVariableLocation, structLocal, returnInstructions))
            }
            is ExpressionStatement -> {
                this += assembleExpression(variableBindings + localVariables, ast, statement.expression, localVariableLocation, structLocal, returnInstructions)
            }
            is Block -> {
                this += assembleBlock(variableBindings + localVariables, ast, statement, localVariableLocation, structLocal, returnInstructions)
            }
            NoOpStatement -> this += AssemblyInstruction.Nop
            is DeclareVariableStatement -> {
                val expression = statement.expression
                val size = expression.type.size
                localVariableLocation -= size.bytes

                val variableRegister = AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.BP), true, if(size.canFitInRegister) size.fitToRegister() else RegisterSize.BIT64, localVariableLocation)
                this += AssemblyInstruction.Comment("declaring new variable  ${statement.variableName} at register $variableRegister. size: " + size.bytes)
                this += assembleExpression(variableBindings + localVariables, ast, expression, localVariableLocation, structLocal, returnInstructions)
                localVariables.add(Variable(
                    name = statement.variableName,
                    register = variableRegister,
                    type = expression.type))
                if(expression.type is StructType) {
                    this += AssemblyInstruction.Custom("mov rcx, " + size.bytes) // this many bytes
                    this += AssemblyInstruction.Custom("mov rsi, rax")
//                    val a = AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.BP),true,  RegisterSize.BIT64, structLocal)
                    this += AssemblyInstruction.Custom("lea rdi, " + variableRegister.toStringNoSize())
                    this += AssemblyInstruction.Custom("rep movsb")
                } else {
                    this += AssemblyInstruction.Mov(
                            reg1 = variableRegister,
                            reg2 = SizedRegister(size.fitToRegister(), Register.A).advanced()).zeroIfNeeded()
                }
            }
            is IfStatement -> {
                // [conditional]
                // if result == 0, jmp to end
                //    block
                // end:

                this += assembleExpression(variableBindings + localVariables, ast, statement.conditional, localVariableLocation,structLocal, returnInstructions)
                this += AssemblyInstruction.Compare(
                    SizedRegister(RegisterSize.BIT8, Register.A),
                    StaticValueAdvancedRegister(0, RegisterSize.BIT8)
                )
                val endingLabel = AssemblyInstruction.Label.next()
                // jump to the end if the result is true (ie, if the condition results in
                this += AssemblyInstruction.ConditionalJump(ComparisonOperator.EQUALS, endingLabel)
                this += assembleBlock(variableBindings + localVariables, ast, statement.block, localVariableLocation,structLocal, returnInstructions)
                this += endingLabel
            }
            is WhileStatement -> {
                val lstart = AssemblyInstruction.Label.next()
                val lend = AssemblyInstruction.Label.next()
                this += lstart
                this += assembleExpression(variableBindings + localVariables, ast, statement.conditional, localVariableLocation,structLocal, returnInstructions)
                this += AssemblyInstruction.Compare(
                    SizedRegister(RegisterSize.BIT8, Register.A),
                    StaticValueAdvancedRegister(0, RegisterSize.BIT8)
                )
                this += AssemblyInstruction.ConditionalJump(ComparisonOperator.EQUALS, lend)
                this += assembleBlock(variableBindings + localVariables, ast, statement.block, localVariableLocation,structLocal, returnInstructions)
                this += AssemblyInstruction.Jump(lstart.name)
                this += lend
            }
        }
    }
}
/*
 so what we're gonna do
 is
 uh
 oh that's right - sized function arguments
 to reiterate
 B = bytes of all the arguments combined

 first argument is at [rbp + B + 8]
 second is at [rbp + B + 8 - (first argument size)]
 and so on
 B is trivial to find
 at [rbp] is the old rbp value, ofc
 so if you have
    foo(long a, long b)
 it should be
    B: 16
    a: [rbp + 24]
    b: [rbp + 16]

 if you have
    foo(int a, int b)
 it should be
    B: 8
    a: [rbp + 16 + B - 4]  [rbp + 20]
    b: [rbp + B + 8]       [rbp + 16]



*/



fun AssemblyInstruction.Mov.zeroIfNeeded(): List<AssemblyInstruction> {
    return when {
        reg1.isMemory -> listOf(this)
        reg1.size == RegisterSize.BIT16 || reg1.size == RegisterSize.BIT8 -> listOf(
            AssemblyInstruction.Xor(reg1 = SizedRegister(RegisterSize.BIT64, reg1.register.register),
                                    reg2 = SizedRegister(RegisterSize.BIT64, reg1.register.register).advanced()),
            this)
        else -> listOf(this)
    }
}

fun AssemblyInstruction.comment(str: String): AssemblyInstruction {
    return AssemblyInstruction.CommentedLine(this, str)
}

fun SizedRegister.advanced(): AdvancedRegister {
    return AdvancedRegister(
        isMemory = false,
        register = this,
        size = this.size)
}

@FunctionalInterface
interface ListBuilder<T> { operator fun plusAssign(t: T); operator fun plusAssign(t: List<T>) }
inline fun <reified T> buildList(crossinline builder: ListBuilder<T>.() -> Unit): List<T>{
    val list = mutableListOf<T>()
    builder.invoke(object : ListBuilder<T> {
        override fun plusAssign(t: T) {
            list.add(t)
        }

        override fun plusAssign(t: List<T>) {
            list.addAll(t)
        }
    })
    return list
}
