package dev.mee42.tvm

import dev.mee42.*
import dev.mee42.Function

class TVMBackend: Backend {
    override val name: String = "TVM"

    override fun process(ast: AST): List<Instr> {
        // return String, probably
        val instrs = mutableListOf<Instr>(Instr.Mov(Source.LabelValue("main"), Register.RF))
        for(function in ast.functions.sortedBy { if(it.header.name == "main") 1 else 0 }) {
            instrs += compileFunction(function, ast)
        }
        instrs += listOf(Instr.Label("halt_"), Instr.Mov(Source.LabelValue("halt_"), Register.RF))
        return instrs
    }

}
sealed interface Instr {
    class Label(val name: String): Instr {
        override fun toString() = "$name:"
    }
    object Nop: Instr {
        override fun toString() = "    nop"
    }
    class Mov(val source: Source, val register: Register): Instr {
        override fun toString() = "    mov $source, $register"
    }
    class Out(val source: Source): Instr {
        override fun toString() = "    out $source"
    }
    class Call(val source: Source): Instr {
        override fun toString() = "    call $source"
    }

    // TODO impl the rest. These two are for returning, kinda important
    class PopW(val register: Register): Instr {
        override fun toString() = "    popw $register"
    }
}
sealed interface Source {
    class Immediate(val value: Int): Source {
        override fun toString() = value.toString()
    }
    class LabelValue(val name: String): Source{
        override fun toString() = name
    }
}


enum class Register(val id: Char): Source {
    R0('0'), R1('1'), R2('2'), R3('3'), /*TODO*/ RF('f');

    override fun toString() = "r$id"
}


// TODO define abi
// at the moment, functions take in 1 value and it must be 16 bits
private fun compileFunction(function: Function, ast: AST): List<Instr> {
    if(function.header.name == "out") return listOf(Instr.Label("out"), Instr.Out(Register.R1), Instr.PopW(Register.RF)) // instrinsic

    println("compiling ${function.header.name}...")
    if(function.header.arguments.size > 1) error("no")
    val firstArg = function.header.arguments[0]
    if(sizeOfType(firstArg.type, ast) > 2) error("no (see comment)")
    val scope = Scope(variables = mapOf(firstArg.name to Register.R1))
    return listOf(Instr.Label(function.header.name)) + scope.compileExpr(function.body) + listOf(Instr.PopW(Register.RF))
}

// returns the number of bytes
private fun sizeOfType(type: Type, ast: AST): Int = when(type){
    is Type.Builtin -> type.size
    is Type.Generic -> error("generic types in backend???")
    Type.Nothing -> 0
    is Type.Pointer -> 2
    is Type.Struct -> {
        val structFields = fieldsFor(ast.structs.first { it.name == type.name }, type)
        structFields.map { (_, it) -> sizeOfType(it, ast) }.fold(0) { a, b -> a + b }
    }
    Type.Unit -> 0
}
// leaves the result in r0
private class Scope(val variables: Map<VariableIdentifier, Register>) // all variables go in registers at the moment
private fun Scope.compileExpr(expr: Expr): List<Instr> = when(expr) {
    is Expr.Block -> {
        // TODO break statements and such
        expr.contents.map { compileExpr(it) }.flatten()
    }
    is Expr.CharLiteral -> listOf(Instr.Mov(Source.Immediate(expr.char.code), Register.R0))
    is Expr.FunctionCall -> {
        // we're assuming there's 1 single argument that goes in r1
        compileExpr(expr.arguments[0]) +

        listOf(
            Instr.Mov(Register.R0, Register.R1), // expr puts result in r0, but argument should be passed in r1
            Instr.Call(Source.LabelValue(expr.header.name))
        ) // puts the returned value in r0 so we're all good
    }


    // we'll get to these
    is Expr.Assignment -> TODO()
    is Expr.BinaryOp -> TODO()
    is Expr.Break -> TODO()
    is Expr.Continue -> TODO()
    is Expr.Deref -> TODO()
    is Expr.If -> TODO()
    is Expr.Loop -> TODO()
    is Expr.MemberAccess -> TODO()
    is Expr.NumericalLiteral -> TODO()
    is Expr.Ref -> TODO()
    is Expr.StringLiteral -> TODO()
    is Expr.StructDefinition -> TODO()
    is Expr.Unit -> TODO()
    is Expr.VariableAccess -> TODO()
    is Expr.VariableDefinition -> TODO()
}
