package dev.mee42.asm

import dev.mee42.asm.RegisterSize.*



sealed class AssemblyInstruction(strIn: String) {
    open val str = "    $strIn"
    data class Call(val labelName: String): AssemblyInstruction("call $labelName")
    object Ret: AssemblyInstruction("ret")
    object Nop: AssemblyInstruction("nop")

    data class Mov(val reg1: AdvancedRegister, val reg2: AdvancedRegister): AssemblyInstruction("mov $reg1, $reg2") {
        constructor(reg1: Register, reg2: Register, size: RegisterSize) : this(
            reg1 = SizedRegister(size, reg1).advanced(),
            reg2 = SizedRegister(size, reg2).advanced()
        )
        override fun toString() = str
    }
    data class MovToLabel(val reg: Register,val labelName: String): AssemblyInstruction("mov ${SizedRegister(BIT64,reg)}, $labelName")
    data class MovSX(val reg1: SizedRegister,val reg2: AdvancedRegister): AssemblyInstruction("movsx $reg1, $reg2")
    data class MovZX(val reg1: SizedRegister, val reg2: AdvancedRegister): AssemblyInstruction("movzx $reg1, $reg2")

    data class Shl(val reg1: SizedRegister, val i: Int): AssemblyInstruction("shl $reg1, $i")

    data class Add(val reg1: AdvancedRegister, val reg2: AdvancedRegister): AssemblyInstruction("add $reg1, $reg2")
    data class Sub(val reg1: AdvancedRegister, val reg2: AdvancedRegister): AssemblyInstruction("sub $reg1, $reg2")

    //class IMul(reg1: AdvancedRegister): AssemblyInstruction("    imul $reg1")
    data class Mul(val reg1: AdvancedRegister): AssemblyInstruction("mul $reg1")
    data class Div(val reg1: AdvancedRegister): AssemblyInstruction("div $reg1")
    data class IDiv(val reg1: AdvancedRegister): AssemblyInstruction("idiv $reg1")

    object ConvertToOctoword: AssemblyInstruction("cqo")
    object ConvertToQuadword: AssemblyInstruction("cdq")
    object ConvertToDoublword: AssemblyInstruction("cwd")

    data class Push(val register: AdvancedRegister): AssemblyInstruction("push $register") {
        constructor(register: Register): this(SizedRegister(BIT64, register).advanced())

        override fun toString() = str
    }
    data class Pop(val register: Register): AssemblyInstruction("pop ${SizedRegister(BIT64, register)}")

    class Custom(str: String): AssemblyInstruction(str)

    data class Xor(val reg1: SizedRegister, val reg2: AdvancedRegister): AssemblyInstruction("xor $reg1, $reg2") {
        companion object {
            fun useToZero(register: SizedRegister): Xor {
                return Xor(
                    reg1 = register,
                    reg2 = register.advanced()
                )
            }
        }
    }
    data class Label(val name: String): AssemblyInstruction("$name:") {
        companion object {
            private var used = 0
            fun next():Label {
                return Label("L" + ++used)
            }
        }

        override fun toString(): String {
            return "$name:"
        }
        override val str: String = "$name:"
    }
    data class Jump(val to: String): AssemblyInstruction("jmp $to")
    data class ConditionalJump(val conditional: ComparisonOperator, val label: Label): AssemblyInstruction("${conditional.jump} ${label.name}")
    data class Comment(val content: String): AssemblyInstruction("; $content")
    class CommentedLine(val line: AssemblyInstruction, comment: String): AssemblyInstruction("OHNO") {
        override val str: String = "$line ; $comment"
        override fun equals(other: Any?): Boolean {
            return other is CommentedLine && line == other.line
        }

        override fun hashCode(): Int {
            return line.hashCode()
        }
    }

    class SetCC(comparison: ComparisonOperator, register: Register):
        AssemblyInstruction("${comparison.setcc} ${SizedRegister(BIT8,register)}")

    class Compare(reg1: SizedRegister, reg2: AdvancedRegister):AssemblyInstruction("cmp $reg1, $reg2"){
        init {
            if(reg1.size != reg2.size) error("uh, no")
        }
    }

    override fun toString() =  str


    companion object {
        // note: will trash rdx
        // note: the A register better be prepared better have the right
        fun divOf(size: RegisterSize, divisor: SizedRegister, signed: Boolean) : List<AssemblyInstruction> {
            return when(size){
                BIT64 -> buildList {
                    if(signed){
                        this += ConvertToOctoword
                        this += IDiv(divisor.advanced())
                    } else {
                        this += Xor.useToZero(SizedRegister(BIT64, Register.D))
                        this += Div(divisor.advanced())
                    }
                }
                BIT32 -> buildList {
                    if(signed) {
                        this += ConvertToQuadword
                        this += IDiv(divisor.advanced())
                    } else {
                        this += Xor.useToZero(SizedRegister(BIT32, Register.D))
                        this += Div(divisor.advanced())
                    }
                }
                BIT16 -> buildList {
                    if(signed) {
                        this += ConvertToDoublword
                        this += IDiv(divisor.advanced())
                    } else {
                        this += Xor.useToZero(SizedRegister(size, Register.D))
                        this += Div(divisor.advanced())
                    }
                }
                BIT8 -> buildList {
                    if(signed) {
                        this += MovSX(
                            reg1 = SizedRegister(BIT16, Register.A),
                            reg2 = SizedRegister(BIT8, Register.A).advanced()
                        )
                        this += IDiv(divisor.advanced())
                    } else {
                        this += Custom("xor ah, ah")
                        this += Div(divisor.advanced())
                    }
                }
            }
        }
    }
}


enum class ComparisonOperator(val setcc: String, val jump: String) {
    EQUALS("sete", "je"),
    NOT_EQUALS("setne","jne")
}