package dev.mee42.tvm

import dev.mee42.*
import dev.mee42.Function

class TVMBackend: Backend {
    override val name: String = "TVM"

    override fun process(ast: AST): List<Instr> = list {
        // return String, probably
        this += Instr.Mov(Source.LabelValue("start_"), Register.RF)
        for(function in ast.functions.sortedBy { if(it.header.name == "main") 1 else 0 }) {
            this += compileFunction(function, ast)
        }
        this += Instr.Label("start_")
        this += Instr.Call(Source.LabelValue("main"))
        this += Instr.Label("halt_")
        this += Instr.Mov(Source.LabelValue("halt_"), Register.RF)
    }

}
sealed interface Instr {
    sealed class InstrS(private val name: String, private vararg val args: Any): Instr {
        override fun toString() = "    $name " + args.joinToString(", ")
    }
    class Label(val name: String): Instr {
        override fun toString() = "$name:"
    }
    object Nop: Instr {
        override fun toString() = "    nop"
    }
    class Text(val s: String): Instr {
        override fun toString(): String {
            return s
        }
    }
    class Mov(val source: Source, val register: Register): InstrS("mov", source, register)
    class Out(val source: Source): InstrS("out", source)
    class Call(val source: Source): InstrS("call", source)
    class Add(val a: Source, val b: Source, val c: Register): InstrS("add", a, b, c)

    class PopW(val register: Register): InstrS("popw", register)
    class PushW(val source: Source): InstrS("pushw", source)
    class PopB(val register: Register): InstrS("popb", register)
    class PushB(val source: Source): InstrS("pushb", source)


    class OStoreB(val value: Source, val addr: Source, val offset: Source): InstrS("ostoreb", value, addr, offset)
    class OStoreW(val value: Source, val addr: Source, val offset: Source): InstrS("ostorew", value, addr, offset)
    class OLoadB(val addr: Source, val i: Source, val reg: Register): InstrS("oloadb", addr, i, reg)
    class OLoadW(val addr: Source, val i: Source, val reg: Register): InstrS("oloadw", addr, i, reg)




    class StoreB(val value: Source, val addr: Source): InstrS("storeb", value, addr)
    class StoreW(val value: Source, val addr: Source): InstrS("storew", value, addr)

    class LoadB(val addr: Source, val reg: Register): InstrS("loadb", addr, addr)
    class LoadW(val addr: Source, val reg: Register): InstrS("loadw", addr, addr)

    class Mul(val a: Source, val b: Source, val high: Register, val low: Register): InstrS("mul", a, b, high, low)




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
    R0('0'), R1('1'), R2('2'), R3('3'), R4('4'), R5('5'), R6('6'), R7('7'), R8('8'), R9('9'),
    RA('a'), RB('b'), RC('c'), RD('d'), RE('e'), RF('f');

    override fun toString() = "r$id"
}


val outFunction = list<Instr> {
    this += Instr.Label("out")
    this += Instr.PushW(Register.RD)
    this += Instr.Mov(Register.RE, Register.RD)
    // we'll use r0 for scrap
    this += Instr.OLoadW(addr = Register.RD, i = Source.Immediate(4), reg = Register.R0)
    this += Instr.Out(Register.R0)
    this += Instr.PopW(Register.RD)
    this += Instr.PopW(Register.RF)
}

// TODO define abi
// at the moment, functions take in 1 value and it must be 16 bits
private fun compileFunction(function: Function, ast: AST): List<Instr> {
    if(function.header.name == "out") return outFunction
    println("compiling ${function.header.name}...")


    val variables = mutableMapOf<VariableIdentifier, ValueLocation>()
    var location = 4 // start at location +4
    for(arg in function.header.arguments.reversed()) {
        // the first argument is pushed first, then the last argument is given a location first
        location += sizeOfType(arg.type, ast) - 1
        variables[arg.name] = ValueLocation.MemoryOffset(location, Register.RD)
        location++
    }

    val scope = Scope(variables = variables, ast, function.header.name)
    return list {
        this += Instr.Label(function.header.name)
        this += Instr.PushW(Register.RD)
        this += Instr.Mov(Register.RE, Register.RD)
        this += scope.compileExpr(function.body)
        this += Instr.PopW(Register.RD)
        this += Instr.PopW(Register.RF)
    }
}
private fun Scope.sizeOfType(type: Type) = sizeOfType(type, this.ast)
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

/*
r0, r1 - accumulator register. Used for return value
r2, r3, r4, r5 - first 4 arguments, can be trashed. Additional arguments spilled onto stack
r6, r7, r8, r9 - registers for temporary data manipulation
rA, rB, rC - reserved for future use
rD - stack frame pointer
rE - stack pointer
rF - instruction pointer

 */

// represents all places that values can exist in
// TODO support values that are not 16 bits
sealed interface ValueLocation {
    class Register(val reg: dev.mee42.tvm.Register): ValueLocation // TODO16 bits
    class MemoryOffset(val offset: Int,val reg: dev.mee42.tvm.Register): ValueLocation // this is a 16 bit value that's at offset from a register
}

private data class Scope(
    val variables: Map<VariableIdentifier, ValueLocation>,
    val ast: AST,
    val currentFunctionName: VariableIdentifier
    )
/*
func bar() {
    @foo { break@foo 3 }
}
->
bar:
  bar_foo_start_:
    mov 3, r0
    mov bar_foo_end_, rf
  bar_foo_end_:
    popw rf

 */

private fun Scope.compileLValueExpr(expr: Expr): List<Instr> = when(expr) {
    else -> error("$expr is not an lvalue")
}
private fun Scope.compileExpr(expr: Expr): List<Instr> = when(expr) {
    is Expr.Block -> list {
        this += expr.contents.map { compileExpr(it) }.flatten()
        this += Instr.Label(this@compileExpr.currentFunctionName + "_" + expr.label + "_end_")
    }
    is Expr.Loop -> list {
        this += Instr.Label(this@compileExpr.currentFunctionName + "_" + expr.block.label + "_start_")
        this += compileExpr(expr.block)
        this += Instr.Mov(Source.LabelValue(this@compileExpr.currentFunctionName + "_" + expr.block.label + "_start_"), Register.RF)
    }
    is Expr.CharLiteral -> listOf(Instr.Mov(Source.Immediate(expr.char.code), Register.R0))
    is Expr.FunctionCall -> {
        if(expr.header.name == "cast") {
            // then it's a noop
            // TODO add checking to make sure this isn't invalid
            compileExpr(expr.arguments[0])
        } else list {
            for ((argExpr, arg) in expr.arguments.zip(expr.header.arguments)) {
                this += compileExpr(argExpr)// puts result into r0
                when {
                    sizeOfType(arg.type) == 1 -> this += Instr.PushB(Register.R0)
                    sizeOfType(arg.type) == 2 -> this += Instr.PushW(Register.R0)
                    else -> error("Don't support >2 byte types yet")
                }
            }

            this += Instr.Call(Source.LabelValue(expr.header.name))
            // puts the returned value in r0 so we're all good
            this += Instr.Add(
                Source.Immediate(expr.header.arguments.map { sizeOfType(it.type) }.sum()),
                Register.RE,
                c = Register.RE
            )
        }
    }

    is Expr.Break -> list {
        // evaluate and put result in r0, then jump to the end of the block we're in
        this += compileExpr(expr.value)
        this += Instr.Mov(Source.LabelValue(this@compileExpr.currentFunctionName + "_" + expr.label + "_end_"), Register.RF)
    }
    is Expr.BinaryOp -> list {
        if(expr.op != Operator.ADD) TODO("only addition supported rn")
        if((expr.left.type !is Type.Builtin.INT && expr.left.type !is Type.Pointer) || (expr.right.type !is Type.Builtin.INT && expr.right.type !is Type.Pointer)) {
            TODO("only Int arithmetic supported rn")
        }

        this += compileExpr(expr.left)
        if(expr.right.type is Type.Pointer) {
            // if this is true, then the next one is NOT true
            this += Instr.Mul(Register.R0, Source.Immediate(sizeOfType(expr.right.type)), Register.R1, Register.R0) // store the top bits into r1, which is trashed
        }
        this += Instr.PushW(Register.R0)
        this += compileExpr(expr.right)
        if(expr.left.type is Type.Pointer) {
            // if this is true, then the above one is NOT true
            this += Instr.Mul(Register.R0, Source.Immediate(sizeOfType(expr.left.type)), Register.R1, Register.R0) // store the top bits into r1, which is trashed
        }
        this += Instr.PopW(Register.R1)
        this += Instr.Add(Register.R0, Register.R1, Register.R0)
    }
    is Expr.VariableAccess -> list {
        val pos = this@compileExpr.variables[expr.variableName] ?: error("ICE: Variable not in scope in backend ${expr.variableName}")
        if(pos is ValueLocation.MemoryOffset) {
            this += Instr.OLoadW(pos.reg, Source.Immediate(pos.offset), reg = Register.R0)
        } else if(pos is ValueLocation.Register){
            this += Instr.Mov(pos.reg, Register.R0)
        }
    }
    is Expr.NumericalLiteral -> {
        if(expr.t != Type.Builtin.INT) TODO("only supporting ints right now lol")
        listOf(Instr.Mov(Source.Immediate(expr.i), Register.R0))
    }
    is Expr.Deref -> list {
        when (val size = sizeOfType(expr.type)) {
            1 -> {
                this += Instr.LoadB(Register.R0, Register.R0)
            }
            2 -> {
                this += Instr.LoadW(Register.R0, Register.R0)
            }
            else -> error("Can't load type of $size bytes")
        }
    }

    is Expr.Assignment -> list {
        if(expr.left is Expr.VariableAccess) {
            // if it's in a register, we need to explicitly handle this case
            val location = this@compileExpr.variables[expr.left.variableName] ?: error("ICE: variable not defined in scope, but got to tvm backend")
            when (location) {
                is ValueLocation.Register -> {
                    // then it's literally just the register
                    // if it's not, then we need to evalLValue it
                    this += compileExpr(expr.right)
                    when(sizeOfType(expr.left.type)) {
                        1 -> this += Instr.StoreB(Register.R0, location.reg)
                        2 -> this += Instr.StoreW(Register.R0, location.reg)
                        else -> error("more than two bytes, am helpless")
                    }
                }
                is ValueLocation.MemoryOffset -> {
                    this += compileExpr(expr.right)
                    when(sizeOfType(expr.left.type)) {
                        1 -> this += Instr.OStoreB(value = Register.R0, addr = location.reg, offset = Source.Immediate(location.offset))
                        2 -> this += Instr.OStoreW(value = Register.R0, addr = location.reg, offset = Source.Immediate(location.offset))
                        else -> error("more than two bytes, am helpless")
                    }
                }
            }
        } else {
            this += compileLValueExpr(expr.left)
            this += Instr.PushW(Register.R0) // the value of the pointer
            this += compileExpr(expr.right)
            this += Instr.PopW(Register.R1) // the value of the left hand
            when (sizeOfType(expr.left.type)) {
                1 -> this += Instr.StoreB(Register.R0, Register.R1)
                2 -> this += Instr.StoreW(Register.R0, Register.R1)
                else -> error("more than 2 bytes, no!")
            }
        }

    }
    is Expr.StringLiteral -> list {
        // mark TODO This is terrible
        for((i, char) in expr.content.withIndex()) {
            this += Instr.StoreB(addr = Source.Immediate(i + 0x5000), value = Source.Immediate(char.code))
        }
        this += Instr.Mov(Source.Immediate(0x5000), Register.R0)
    }
    is Expr.If -> TODO()

    // we'll get to these
    is Expr.Continue -> TODO()
    is Expr.MemberAccess -> TODO()
    is Expr.Ref -> TODO()
    is Expr.StructDefinition -> TODO()
    is Expr.Unit -> TODO()
    is Expr.VariableDefinition -> TODO()
}




private fun <T> list(block: MutableList<T>.() -> Unit): List<T> {
    val l = mutableListOf<T>()
    block(l)
    return l
}
