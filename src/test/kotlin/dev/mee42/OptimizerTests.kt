package dev.mee42

import dev.mee42.asm.*
import dev.mee42.asm.RegisterSize.*
import dev.mee42.opt.optimize
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OptimizerTests: StringSpec({
    "empty asm -> empty asm" {
        optimize(Assembly(emptyList(), emptyList())).asm shouldBe emptyList()
    }
    "mov & push merge" {
        optimize(Assembly(listOf(
                AssemblyInstruction.Mov(SizedRegister(BIT64, Register.A).advanced(),StaticValueAdvancedRegister(5, BIT64)),
                AssemblyInstruction.Push(Register.A)
        ), emptyList())).asm shouldBe listOf(
                AssemblyInstruction.Push(StaticValueAdvancedRegister(5, BIT64))
        )
        optimize(Assembly(listOf(
                AssemblyInstruction.Mov(SizedRegister(BIT32, Register.A).advanced(),StaticValueAdvancedRegister(5, BIT32)),
                AssemblyInstruction.Push(Register.A)
        ), emptyList())).asm shouldBe listOf(
                AssemblyInstruction.Push(StaticValueAdvancedRegister(5, BIT64))
        )
    }
    "merge double mov" {
        optimize(Assembly(listOf(
                AssemblyInstruction.Mov(SizedRegister(BIT64, Register.A).advanced(), StaticValueAdvancedRegister(5, BIT64)),
                AssemblyInstruction.Mov(SizedRegister(BIT64, Register.B).advanced(), SizedRegister(BIT64, Register.A).advanced())
        ), emptyList())).asm shouldBe listOf(
                AssemblyInstruction.Mov(SizedRegister(BIT64, Register.B).advanced(),  StaticValueAdvancedRegister(5, BIT64))
        )
    }

})