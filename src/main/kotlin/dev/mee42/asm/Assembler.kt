package dev.mee42.asm

import dev.mee42.parser.*
import dev.mee42.parser.Function

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

    var position = function.arguments.sumBy { it.type.size.bytes }.plus(8)
    function.arguments.forEachIndexed { i, it ->
        val register = stackPointerRegister(position, it.type.size.fitToRegister())
        variableBindings.add(Variable(it.name, it.type, register))
        position -= it.type.size.bytes
        this += AssemblyInstruction.Comment("argument $i (${it.name}) in register ${register.size.asmName} $register")
    }
    val max = function.content.localVariableMaxBytes
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
    val returnInstructions = buildList<AssemblyInstruction> {
        this += AssemblyInstruction.Add(
            reg1 = SizedRegister(RegisterSize.BIT64, Register.SP).advanced(),
            reg2 = StaticValueAdvancedRegister(localVariableSize, RegisterSize.BIT64)
        )
        this += AssemblyInstruction.Pop(Register.BP)
        this += AssemblyInstruction.Ret
    }
    this += assembleBlock(variableBindings, ast, function.content, 0, returnInstructions)
}



fun assembleBlock(variableBindings: List<Variable>,
                          ast: AST,
                          block: Block,
                          topLocal: Int,
                          returnInstructions: List<AssemblyInstruction>): Assembly  = buildAssembly {
    val localVariables = mutableListOf<Variable>()
    var localVariableLocation = topLocal
    for(statement in block.statements) {
        this += AssemblyInstruction.Comment("statement $statement")
        when (statement) {
            is ReturnStatement -> {
                val expression = statement.expression
                this += assembleExpression(variableBindings + localVariables, ast, expression, localVariableLocation, returnInstructions)
                this += returnInstructions
            }
            is ExpressionStatement -> {
                this += assembleExpression(variableBindings + localVariables, ast, statement.expression, localVariableLocation, returnInstructions)
            }
            is Block -> {
                this += assembleBlock(variableBindings + localVariables, ast, statement, localVariableLocation, returnInstructions)
            }
            NoOpStatement -> this += AssemblyInstruction.Nop
            is DeclareVariableStatement -> {
                val expression = statement.expression
                val size = expression.type.size
                localVariableLocation -= size.bytes

                val variableRegister = AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.BP), true, size.fitToRegister(), localVariableLocation)
                this += AssemblyInstruction.Comment("declaring new variable  ${statement.variableName} at register $variableRegister")
                this += assembleExpression(variableBindings + localVariables, ast, expression, localVariableLocation, returnInstructions)
                localVariables.add(Variable(
                    name = statement.variableName,
                    register = variableRegister,
                    type = expression.type))
                this += AssemblyInstruction.Mov(
                    reg1 = variableRegister,
                    reg2 = SizedRegister(size.fitToRegister(), Register.A).advanced()).zeroIfNeeded()
            }
            is IfStatement -> {
                // [conditional]
                // if result == 0, jmp to end
                //    block
                // end:

                this += assembleExpression(variableBindings + localVariables, ast, statement.conditional, localVariableLocation, returnInstructions)
                this += AssemblyInstruction.Compare(
                    SizedRegister(RegisterSize.BIT8, Register.A),
                    StaticValueAdvancedRegister(0, RegisterSize.BIT8)
                )
                val endingLabel = AssemblyInstruction.Label.next()
                // jump to the end if the result is true (ie, if the condition results in
                this += AssemblyInstruction.ConditionalJump(ComparisonOperator.EQUALS, endingLabel)
                this += assembleBlock(variableBindings + localVariables, ast, statement.block, localVariableLocation, returnInstructions)
                this += endingLabel
            }
            is WhileStatement -> {
                val lstart = AssemblyInstruction.Label.next()
                val lend = AssemblyInstruction.Label.next()
                this += lstart
                this += assembleExpression(variableBindings + localVariables, ast, statement.conditional, localVariableLocation, returnInstructions)
                this += AssemblyInstruction.Compare(
                    SizedRegister(RegisterSize.BIT8, Register.A),
                    StaticValueAdvancedRegister(0, RegisterSize.BIT8)
                )
                this += AssemblyInstruction.ConditionalJump(ComparisonOperator.EQUALS, lend)
                this += assembleBlock(variableBindings + localVariables, ast, statement.block, localVariableLocation, returnInstructions)
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
    a: [rbp + 8 + 8]     [rbp + 16]
    b: [rbp + 8 + 8 - 4] [rbp + 12]


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
