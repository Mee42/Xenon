package dev.mee42.asm

import dev.mee42.asm.RegisterSize.*


enum class RegisterSize(val bytes: Int) {
    BIT64(8),
    BIT32(4),
    BIT16(2),
    BIT8(1)
}

enum class Register(val size: RegisterSize) {
    RAX(BIT64), RBX(BIT64), RCX(BIT64), RDX(BIT64),
    EAX(BIT32), EBX(BIT32), ECX(BIT32), EDX(BIT32),
    AX(BIT16),  BX(BIT16),  CX(BIT16),  DX(BIT16),
    AL(BIT8),   BL(BIT8),   CL(BIT8),   DL(BIT8)
    ;

    override fun toString() = name
}

class AdvancedRegister(private val register: Register, private val isMemory: Boolean) {
    override fun toString(): String {
        return if(isMemory) "[$register]" else "$register"
    }
}

sealed class AssemblyInstruction(private val str: String) {
    class Call(labelName: String): AssemblyInstruction("    call $labelName")
    object Ret: AssemblyInstruction("    ret")
    class Mov(reg1: AdvancedRegister, reg2: AdvancedRegister): AssemblyInstruction("    mov $reg1, $reg2")
    class Add(reg1: AdvancedRegister, reg2: AdvancedRegister): AssemblyInstruction("    add $reg1, $reg2")
    class Label(name: String): AssemblyInstruction("$name:")
    class Jump(to: String): AssemblyInstruction("    jmp $to")
    class Comment(content: String): AssemblyInstruction("    ; $content")
    class CommentedLine(line: AssemblyInstruction, comment: String): AssemblyInstruction("$line ; $comment")

    override fun toString() =  str
}
