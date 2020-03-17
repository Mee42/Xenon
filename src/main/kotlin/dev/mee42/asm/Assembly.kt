package dev.mee42.asm

import dev.mee42.asm.RegisterSize.*


enum class RegisterSize(val bytes: Int) {
    BIT64(8),
    BIT32(4),
    BIT16(2),
    BIT8(1),
}

enum class Register(val bit64: String, val bit32: String, val bit16: String, val bit8: String) {
    A("rax","eax","ax","al"),
    B("rbx","ebx","bx","bl"),
    C("rcx","ecx","cx","cl"),
    D("rdx","edx","dx","dl"),
    SI("rsi","esi","si","sil"),
    DI("rdi","edi","di","dil"),
    BP("rbp","ebp","bp","bpl"),
    SP("rsp","esp","sp","spl"),
    R8(8),
    R9(9),
    R10(10),
    R11(11),
    R12(12),
    R13(13),
    R14(14),
    R15(15);

    constructor(i: Int): this("r$i","r${i}d","r${i}w","r${i}b")
    companion object {
        val argumentRegisters = listOf(B, C, D, R8, R9)
    }
}

class SizedRegister(val size: RegisterSize, private val register: Register) {
    override fun toString() = when(size) {
        BIT64 -> register.bit64
        BIT32 -> register.bit32
        BIT16 -> register.bit16
        BIT8 -> register.bit8
    }

}

class AdvancedRegister(private val register: SizedRegister, private val isMemory: Boolean) {
    override fun toString(): String {
        return if(isMemory) "[$register]" else "$register"
    }
    init {
        if(isMemory && register.size != RegisterSize.BIT64) error("all memory access must be 64-bit")
    }
}

sealed class AssemblyInstruction(private val str: String) {
    class Call(labelName: String): AssemblyInstruction("    call $labelName")
    object Ret: AssemblyInstruction("    ret")
    class Mov(reg1: AdvancedRegister, reg2: AdvancedRegister): AssemblyInstruction("    mov $reg1, $reg2")
    class Add(reg1: AdvancedRegister, reg2: AdvancedRegister): AssemblyInstruction("    add $reg1, $reg2")
    class Sub(reg1: AdvancedRegister, reg2: AdvancedRegister): AssemblyInstruction("    sub $reg1, $reg2")

    class Label(name: String): AssemblyInstruction("$name:")
    class Jump(to: String): AssemblyInstruction("    jmp $to")
    class Comment(content: String): AssemblyInstruction("    ; $content")
    class CommentedLine(line: AssemblyInstruction, comment: String): AssemblyInstruction("$line ; $comment")

    override fun toString() =  str
}
