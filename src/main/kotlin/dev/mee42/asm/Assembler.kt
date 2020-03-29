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
    var position = function.arguments.size * 8 + 8
    function.arguments.forEachIndexed { i, it ->
        val register = stackPointerRegister(position, it.type.size)
        position -= 8
        variableBindings.add(Variable(it.name, it.type, register))
        this += AssemblyInstruction.Comment("argument $i (${it.name}) in register ${register.size.asmName} $register")
    }
    val localVariableSize = function.content.localVariableMaxBytes.let { if(it % 16 == 0) it else it + 16 - it % 16 }
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
    this += assembleBlock(variableBindings, ast, accumulatorRegister, function.content, -8, returnInstructions)
}


private fun assembleBlock(variableBindings: List<Variable>,
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
                this += assembleExpression(variableBindings + localVariables, ast, expression, accumulatorRegister)
                this += returnInstructions
            }
            is ExpressionStatement -> {
                this += assembleExpression(variableBindings + localVariables, ast, statement.expression, accumulatorRegister)
            }
            is Block -> {
                this += assembleBlock(variableBindings + localVariables, ast, accumulatorRegister, statement, localVariableLocation, returnInstructions)
            }
            NoOpStatement -> this += AssemblyInstruction.Nop
            is DeclareVariableStatement -> {
                val expression = statement.expression
                val size = expression.type.size
                val variableRegister = AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.BP), true, size, localVariableLocation)
                localVariableLocation -= size.bytes
                this += AssemblyInstruction.Comment("declaring new variable  ${statement.variableName} at register $variableRegister")
                this += assembleExpression(variableBindings + localVariables, ast, expression, accumulatorRegister)
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

                this += assembleExpression(variableBindings + localVariables, ast, statement.conditional, accumulatorRegister)
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
                this += assembleExpression(variableBindings + localVariables, ast, statement.conditional, accumulatorRegister)
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
                this += assembleExpression(variableBindings + localVariables, ast, expression, accumulatorRegister)
                val variableRegister = (variableBindings + localVariables).first { it.name == statement.variableName }.register
                this += AssemblyInstruction.Mov(
                    reg1 = variableRegister,
                    reg2 = SizedRegister(statement.expression.type.size, accumulatorRegister).advanced()
                ).zeroIfNeeded()
            }
            is MemoryWriteStatement ->  {
                this += assembleExpression(variableBindings + localVariables, ast, statement.location, accumulatorRegister)
                this += AssemblyInstruction.Push(accumulatorRegister)
                this += assembleExpression(variableBindings + localVariables, ast, statement.value, accumulatorRegister)
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
inline fun <reified T> buildList(crossinline block: MutableList<T>.() -> Unit): List<T> {
    val list = mutableListOf<T>()
    block(list)
    return list
}