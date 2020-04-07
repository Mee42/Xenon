package dev.mee42.asm

import dev.mee42.parser.*

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

private fun assemble(function: XenonFunction, ast: AST): Assembly = buildAssembly {
    // these are the registers needed to call this function:
    val variableBindings = mutableListOf<Variable>()

    val returnRegister = SizedRegister(function.returnType.size, Register.A)
    val accumulatorRegister = Register.A
    // so we don't use these variables on accident
    this += AssemblyInstruction.CommentedLine(
        line = AssemblyInstruction.Label(function.identifier),
        comment = "return value in register $returnRegister")
    // so the argument registers are
    // for now, let's fit everything into 8 bytes?
    // yeah
    // sure
    fun Int.padUpTo16(): Int {
        return if(this % 16 == 0) this
               else this + 16 - (this % 16)
    }
    var position = function.arguments.sumBy { 8 /*arguments are 8 bytes because they're pushed to the stack*/ /*it.type.size.bytes*/ }.plus(8).padUpTo16()
    function.arguments.forEachIndexed { i, it ->
        val register = stackPointerRegister(position, it.type.size)
        position -= 8
        variableBindings.add(Variable(it.name, it.type, register))
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
    this += assembleBlock(variableBindings, ast, accumulatorRegister, function.content, 0, returnInstructions)
}


fun assembleBlock(variableBindings: List<Variable>,
                          ast: AST, accumulatorRegister: Register,
                          block: Block,
                          topLocal: Int,
                          returnInstructions: List<AssemblyInstruction>): Assembly  = buildAssembly {
    val localVariables = mutableListOf<Variable>()
    var localVariableLocation = topLocal
    for(statement in block.statements) {
        when (statement) {
            is ReturnStatement -> {
                val expression = statement.expression
                this += assembleExpression(variableBindings + localVariables, ast, expression, accumulatorRegister, localVariableLocation, returnInstructions)
                this += returnInstructions
            }
            is ExpressionStatement -> {
                this += assembleExpression(variableBindings + localVariables, ast, statement.expression, accumulatorRegister, localVariableLocation, returnInstructions)
            }
            is Block -> {
                this += assembleBlock(variableBindings + localVariables, ast, accumulatorRegister, statement, localVariableLocation, returnInstructions)
            }
            NoOpStatement -> this += AssemblyInstruction.Nop
            is DeclareVariableStatement -> {
                val expression = statement.expression
                val size = expression.type.size
                localVariableLocation -= size.bytes

                val variableRegister = AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.BP), true, size, localVariableLocation)
                this += AssemblyInstruction.Comment("declaring new variable  ${statement.variableName} at register $variableRegister")
                this += assembleExpression(variableBindings + localVariables, ast, expression, accumulatorRegister, localVariableLocation, returnInstructions)
                localVariables.add(Variable(
                    name = statement.variableName,
                    register = variableRegister,
                    type = expression.type))
                this += AssemblyInstruction.Mov(
                    reg1 = variableRegister,
                    reg2 = SizedRegister(size, accumulatorRegister).advanced()).zeroIfNeeded()
            }
            is IfStatement -> {
                // [conditional]
                // if result == 0, jmp to end
                //    block
                // end:

                this += assembleExpression(variableBindings + localVariables, ast, statement.conditional, accumulatorRegister, localVariableLocation, returnInstructions)
                this += AssemblyInstruction.Compare(
                    SizedRegister(RegisterSize.BIT8, accumulatorRegister),
                    StaticValueAdvancedRegister(0, RegisterSize.BIT8)
                )
                val endingLabel = AssemblyInstruction.Label.next()
                // jump to the end if the result is true (ie, if the condition results in
                this += AssemblyInstruction.ConditionalJump(ComparisonOperator.EQUALS, endingLabel)
                this += assembleBlock(variableBindings + localVariables, ast, accumulatorRegister, statement.block, localVariableLocation, returnInstructions)
                this += endingLabel
            }
            is WhileStatement -> {
                val lstart = AssemblyInstruction.Label.next()
                val lend = AssemblyInstruction.Label.next()
                this += lstart
                this += assembleExpression(variableBindings + localVariables, ast, statement.conditional, accumulatorRegister, localVariableLocation, returnInstructions)
                this += AssemblyInstruction.Compare(
                    SizedRegister(RegisterSize.BIT8, accumulatorRegister),
                    StaticValueAdvancedRegister(0, RegisterSize.BIT8)
                )
                this += AssemblyInstruction.ConditionalJump(ComparisonOperator.EQUALS, lend)
                this += assembleBlock(variableBindings + localVariables, ast, accumulatorRegister, statement.block, localVariableLocation, returnInstructions)
                this += AssemblyInstruction.Jump(lstart.name)
                this += lend
            }
            is AssignVariableStatement -> {
                val expression = statement.expression
                this += assembleExpression(variableBindings + localVariables, ast, expression, accumulatorRegister, localVariableLocation, returnInstructions)
                val variableRegister = (variableBindings + localVariables).firstOrNull { it.name == statement.variableName }?.register ?: error("can't find variable ${statement.variableName}")
                this += AssemblyInstruction.Mov(
                    reg1 = variableRegister,
                    reg2 = SizedRegister(statement.expression.type.size, accumulatorRegister).advanced()
                ).zeroIfNeeded()
            }
            is MemoryWriteStatement ->  {
                this += assembleExpression(variableBindings + localVariables, ast, statement.location, accumulatorRegister, localVariableLocation, returnInstructions)
                this += AssemblyInstruction.Push(accumulatorRegister)
                this += assembleExpression(variableBindings + localVariables, ast, statement.value, accumulatorRegister, localVariableLocation, returnInstructions)
                this += AssemblyInstruction.Pop(Register.B)
                this += AssemblyInstruction.Mov(
                        reg1 = AdvancedRegister(
                                register = SizedRegister(RegisterSize.BIT64, Register.B),
                                isMemory = true,
                                size = statement.value.type.size),
                        reg2 = SizedRegister(statement.value.type.size, accumulatorRegister).advanced()
                )
            }
        }
    }
}

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
