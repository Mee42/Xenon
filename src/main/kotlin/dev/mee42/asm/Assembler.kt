package dev.mee42.asm

import dev.mee42.parser.*
import dev.mee42.parser.Function

fun assemble(ast: AST):List<AssemblyInstruction> {
    return ast.functions.map { assemble(it, ast) }.flatten()
}
private class Variable(val name: String, val type: Type, val register: SizedRegister, isFinal: Boolean)

private fun assemble(function: Function, ast: AST): List<AssemblyInstruction> {
    println("compiling function ${function.name}, ast: ${function.content}")
    return buildList {

        // these are the registers needed to call this function:
        if(function.arguments.size > 2) error("can't support more then 5 arguments to a function as of right now, lol")
        val variableBindings = mutableListOf<Variable>()
        val returnRegister = SizedRegister(function.returnType.size, Register.A)
        val accumulatorRegister = Register.B

        // so we don't use these variables on accident
        variableBindings.add(Variable("_ret", function.returnType, returnRegister, true))
        variableBindings.add(Variable("_acc",DynamicType, SizedRegister(RegisterSize.BIT64, accumulatorRegister), true))

        this += AssemblyInstruction.CommentedLine(
            line = AssemblyInstruction.Label(function.name),
            comment = "return value in register $returnRegister")
        // so the argument registers are
        function.arguments.forEachIndexed { i, it ->
            val size = it.type.size
            val register = SizedRegister(size, Register.argumentRegisters[i])
            variableBindings.add(Variable(it.name, it.type, register, true))
            this += AssemblyInstruction.Comment("argument $i (${it.name}) in register $register")
        }
        this += AssemblyInstruction.Push(accumulatorRegister) // we always push the accumulator
        val statement = function.content.statements.first()
        // alright, now let's look at our first instruction
        // only one statement is supported right now lol
        if(statement !is ReturnStatement){
            error("only return statements allowed right now")
        }
        // statement is ReturnStatement: everything after this line assumes that, everything before this line should act like it could be not
        val expression = statement.expression
        this += assembleExpression(variableBindings, ast, expression, accumulatorRegister)
        this += AssemblyInstruction.Mov(returnRegister.advanced(), SizedRegister(returnRegister.size, accumulatorRegister).advanced())
        this += AssemblyInstruction.Pop(accumulatorRegister)
        this += AssemblyInstruction.Ret
    }
}

// output assembly that, using the variables in the registers
// computes the value of the expression
// when this assembly completes, the following must be true
// - only the rbx register has changed values
// - the rbx register is has the evaluated value of this expression
// - the stack is in the same state it entered in
private fun assembleExpression(variableBindings: List<Variable>, ast: AST, expression: Expression, accumulatorRegister: Register): List<AssemblyInstruction> {
    return when(expression) {
        is AddExpression -> {
            // alright, here's the real test
            // reserve a free register
            buildList {
                val (newBindings, reservedRegister) = variableBindings.reserveOne(expression.type)
                this += AssemblyInstruction.Push(reservedRegister.register)

                this += assembleExpression(newBindings, ast, expression.var1, accumulatorRegister)
                this += AssemblyInstruction.Push(accumulatorRegister)

                this += assembleExpression(newBindings, ast, expression.var2, accumulatorRegister)
                this += AssemblyInstruction.Pop(reservedRegister.register)

                this += AssemblyInstruction.Add(
                    reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(),
                    reg2 = reservedRegister.advanced()
                )

                this += AssemblyInstruction.Pop(reservedRegister.register)
            }
        }
        is SubExpression -> TODO()
        is VariableAccessExpression -> {
            val variable = variableBindings.first { it.name == expression.variableName }
            listOf(AssemblyInstruction.Mov(
                reg1 = SizedRegister(variable.register.size, Register.B).advanced(),
                reg2 = variable.register.advanced()
            ))
        }
        is DereferencePointerExpression -> TODO("pointers not allowed yet")
    }

}

private fun List<Variable>.reserveOne(type: Type): Pair<List<Variable>, SizedRegister> {
    val register = Register.usable.firstOrNull { this.none { variable -> variable.register.register == it } }
        ?: error("ran out of registers oof")
    fun Int.toReservedRegisterName(): String = "_reg_reserved_$this"
    val name = (0..1000).first {
        this.none { variable -> variable.name == it.toReservedRegisterName() }
    }.toReservedRegisterName()
    val sized =  SizedRegister(type.size, register)
    return (this + Variable(name,type,sized, true)) to sized
}

private fun SizedRegister.advanced(isMemory: Boolean = false): AdvancedRegister {
    return AdvancedRegister(
        isMemory = isMemory,
        register = this)
}
private fun <T> buildList(block: MutableList<T>.() -> Unit): List<T> {
    val list = mutableListOf<T>()
    block(list)
    return list
}