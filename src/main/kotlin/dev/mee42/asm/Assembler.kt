package dev.mee42.asm

import dev.mee42.parser.*
import dev.mee42.parser.Function

fun assemble(ast: AST):List<AssemblyInstruction> {
    return ast.functions.map(::assemble).flatten()
}
private class Variable(val name: String, val type: Type, val register: Register, isFinal: Boolean)

private fun assemble(function: Function): List<AssemblyInstruction> {
    val list = mutableListOf<AssemblyInstruction>()

    // these are the registers needed to call this function:
    if(function.arguments.size > 2) error("can't support more then 2 arguments to a function as of right now, lol")
    val variableBindings = mutableListOf<Variable>()
    val returnRegister = Register.values().first { it.size == function.returnType.size }
    variableBindings.add(Variable("_ret", function.returnType, returnRegister, true))

    list.add(AssemblyInstruction.CommentedLine(
        line = AssemblyInstruction.Label(function.name),
        comment = "return value in register $returnRegister"))

    function.arguments.forEachIndexed { i, it ->
        val size = it.type.size
        val register = Register.values().firstOrNull { reg ->
            reg.size == size && variableBindings.none { v -> v.register == reg }
        } ?: error("out of registers")
        variableBindings.add(Variable(it.name, it.type, register, true))
        list.add(AssemblyInstruction.Comment("argument $i (${it.name}) in register $register"))
    }

    // alright, now let's look at our first instruction
    // only one statement is supported right now lol
    when(val statement = function.content.statements.first()) {
        is ReturnStatement -> {
            // guess we're returning this expression
            // so we store the value in $returnRegister
            // but how to calculate it :thinking:
            when(val expr = statement.expression) {
                is VariableAccessExpression -> {
                    list.add(AssemblyInstruction.Mov(
                        reg1 = AdvancedRegister(returnRegister, false),
                        reg2 = AdvancedRegister(
                            variableBindings.first { it.name == expr.variableName }.register, false
                        )
                    ))
                }
                is AddExpression -> {
                    if(expr.var1 is VariableAccessExpression) {//maybe have an InstantGet interface that's for numbers and registers?
                        // ok, so we just add the second expression to the first
                        if(expr.var2 is VariableAccessExpression) {
                            // easy
                            list.add(AssemblyInstruction.Add(
                                reg1 = AdvancedRegister(variableBindings.first { it.name == expr.var1.variableName }.register, false),
                                reg2 = AdvancedRegister(variableBindings.first { it.name == expr.var2.variableName }.register, false)
                            ))
                            list.add(AssemblyInstruction.Mov(
                                reg1 = AdvancedRegister(returnRegister, false),
                                reg2 = AdvancedRegister(
                                    variableBindings.first { it.name == expr.var1.variableName }.register, false
                                )
                            ))
                        } else TODO("can't")
                    } else TODO("can't do this")
                }
                else -> TODO("can't return anything else lol")
            }
            // then we return
            list.add(AssemblyInstruction.Ret)
        }
    }

    return list
}