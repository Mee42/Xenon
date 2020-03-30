package dev.mee42.opt

import dev.mee42.Builder
import dev.mee42.asm.*
import dev.mee42.asm.AssemblyInstruction.*
import dev.mee42.asm.RegisterSize.*


private interface OptAssembler
private class PeepholeOptimizerBuilder: OptAssembler {
    var size: Int? = null
    var runner: PeepholeRunner? = null
    var enabled = true
    fun build() = PeepholeOptimizer(size!!, runner!!, enabled)
    @Builder
    fun runner(args: PeepholeRunner){
        runner = args
    }
}
typealias PeepholeRunner =  (List<AssemblyInstruction>) -> List<AssemblyInstruction>?
private class PeepholeOptimizer(val size: Int, val runner: PeepholeRunner, val enabled: Boolean)
private class OptimizationSuite {
    val peepholes = mutableListOf<PeepholeOptimizer>()
    @Builder
    fun peephole(block: PeepholeOptimizerBuilder.() -> Unit) {
        peepholes += PeepholeOptimizerBuilder().apply(block).build()
    }
}

@Builder
private fun optimizationSuite(block: OptimizationSuite.() -> Unit): OptimizationSuite {
    return OptimizationSuite().apply(block)
}

// this works on the assumption that each register is only used once
private val optimizations = optimizationSuite {
    peephole { // push mov merge
        size = 2
        enabled = true
        runner { (a, b) ->
            if(a !is Mov) return@runner null
            if(b !is Push) return@runner null
            if(a.reg1 != AdvancedRegister(SizedRegister(BIT64, Register.A), isMemory = false, size = BIT64)) return@runner null
            if(b.register != AdvancedRegister(SizedRegister(BIT64, Register.A), isMemory = false, size = BIT64)) return@runner null
            listOf(Push(register = a.reg2))
        }
    }
    peephole {
        size = 1
        enabled = true
        runner { (a) ->
            if(a is Add && a.reg2 is StaticValueAdvancedRegister && a.reg2.value == 0L ||
                    a is Sub && a.reg2 is StaticValueAdvancedRegister && a.reg2.value == 0L) {
                emptyList()
            } else null
        }
    }
    peephole {
        size = 2
        enabled = true
        runner { (a,b) ->
            if(a !is Mov) return@runner null
            if(b !is Mov) return@runner null
            if(a.reg1.isMemory) return@runner null
            if(a.reg1 != b.reg2) return@runner null
            listOf(Mov(
                    reg1 = b.reg1,
                    reg2 = a.reg2
            ))
        }
    } // TODO: make sure the optimizations are actually applied (thinking - live edit an asm file?)
}

fun optimize(asm: Assembly): Assembly {
    return Assembly(optimize(asm.asm), asm.data)
}
private fun optimize(assembly: List<AssemblyInstruction>): List<AssemblyInstruction> {
    for(i in assembly.indices) {
        for(optimization in optimizations.peepholes) {
            if(!optimization.enabled) continue
            if(i + optimization.size >= assembly.size) continue
            val subset = assembly.subList(i, i + optimization.size)
            val new = optimization.runner(subset) ?: continue
            return assembly.subList(0,i) + new + assembly.subList(i + optimization.size, assembly.size)
        }
    }
    return assembly
}