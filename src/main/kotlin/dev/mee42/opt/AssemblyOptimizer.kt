package dev.mee42.opt

import dev.mee42.Builder
import dev.mee42.arg.VerboseOption
import dev.mee42.asm.*
import dev.mee42.asm.AssemblyInstruction.*
import dev.mee42.asm.RegisterSize.*


private interface OptAssembler
private class PeepholeOptimizerBuilder: OptAssembler {
    var size: Int? = null
    var runner: PeepholeRunner? = null
    var enabled = true
    var name: String = "unnamed"
    fun build() = PeepholeOptimizer(size!!, runner!!, enabled, name)
    @Builder
    fun runner(args: PeepholeRunner){
        runner = args
    }
}
typealias PeepholeRunner =  (List<AssemblyInstruction>) -> List<AssemblyInstruction>?
private class PeepholeOptimizer(val size: Int, val runner: PeepholeRunner, val enabled: Boolean, val name: String)
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
        name = "merge mov and push"
        runner { (a, b) ->
            if(a !is Mov) return@runner null
            if(b !is Push) return@runner null
//            println("testing $a and $b")
            if(a.reg1 != SizedRegister(BIT64, Register.A).advanced())  return@runner null
            if(b.register != SizedRegister(BIT64, Register.A).advanced()) return@runner null
            listOf(Push(register = a.reg2))
        }
    }
    peephole {
        size = 1
        enabled = true
        name = "remove 0 add/subs"
        runner { (a) ->
            if(a is Add && a.reg2 is StaticValueAdvancedRegister && a.reg2.value == 0L ||
                    a is Sub && a.reg2 is StaticValueAdvancedRegister && a.reg2.value == 0L) {
                emptyList()
            } else null
        }
    }
    peephole {
        size = 2
        enabled = false // TODO make this work with variable set propegation
        name = "merge double mov"
        runner { (a,b) ->
            if(a !is Mov) return@runner null
            if(b !is Mov) return@runner null
            if(a.reg1.isMemory) return@runner null
            if(a.reg1 != b.reg2) return@runner null
            if(a.reg2.isMemory && b.reg1.isMemory) return@runner null
            listOf(Mov(
                    reg1 = b.reg1,
                    reg2 = a.reg2
            ))
        }
    }
    peephole {
        size = 2
        enabled = true
        name = "merge mov static value and push"
        runner { (a,b) ->
            if(a !is Mov) return@runner null
            if(b !is Push) return@runner null
            if(a.reg1 != SizedRegister(BIT64, Register.A).advanced()) return@runner null
            if(b.register != SizedRegister(BIT64, Register.A).advanced()) return@runner null
            if(a.reg2 !is StaticValueAdvancedRegister) return@runner null
            if(a.reg2.size != BIT64) return@runner null
            listOf(
                    Push(StaticValueAdvancedRegister(a.reg2.value, BIT64))
            )
        }
    }
    peephole {
        size = 1
        enabled = true
        name = "remove nops"
        runner { (a) ->
            if(a is Nop) emptyList() else null
        }
    }
    peephole {
        size = 1
        enabled = true
        name = "mov 0 replaced with xor zero"
        runner { (a) ->
            when {
                a !is Mov -> null
                a.reg2 !is StaticValueAdvancedRegister -> null
                a.reg2.value != 0L -> null
                a.reg1.isMemory -> null
                else -> listOf(Xor.useToZero(a.reg1.register))
            }
        }
    }
}

fun optimize(asm: Assembly): Assembly {
    return Assembly(optimize(asm.asm), asm.data)
}
private fun optimize(assembly: List<AssemblyInstruction>): List<AssemblyInstruction> {
    for(i in assembly.indices) {
        for(optimization in optimizations.peepholes) {
            if(!optimization.enabled) continue
            if(i + optimization.size > assembly.size) continue
            val subset = assembly.subList(i, i + optimization.size)
                    .map {
                        var f = it
                        while(f is CommentedLine) {
                            f = f.line
                        }
                        f
                    }
//            println("testing \"${optimization.name}\" on $subset")
            val new = optimization.runner(subset) ?: continue
//            println("succeeded!")
            VerboseOption.OPTIMIZATIONS.println("applying optimization ${optimization.name}: $subset -> $new ")
            return optimize(assembly.subList(0,i) + new + assembly.subList(i + optimization.size, assembly.size))
        }
    }
    return assembly
}
