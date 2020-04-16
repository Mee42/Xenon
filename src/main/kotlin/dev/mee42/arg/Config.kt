package dev.mee42.arg

import java.util.*
import kotlin.system.exitProcess


lateinit var globalConfig: Config

enum class OutputFormat {
    NASM, OBJECT, CODE, RUN
}

enum class Optimization(val str: String) {
    DEAD_CODE("dead-code-elem"),
    INLINE_MACROS("inline-macros"),
    RESHUFFLE("reshuffle"),
    VALUE_PROPAGATOR("value-propagator"),
    FLATTEN_TYPELESS_BLOCK("flatten-typeless-blocks")
}

enum class VerboseOption(val str: String) {
    END_TIME("end-timings"),
    COMPILE_TIME("compile-timings"),
    OPTIMIZATIONS("optimizations"),
    AST("ast"),
    DECOMPILE_AST("decompile-ast"),
    TOKENS("tokens"),
    PURITY("purity"),
    CONFIG("config"),
    COMMANDS("commands"),
    ;
    private fun println(str: String, err: Boolean) {
        if(isSelected()){
            (if(err) System.err else System.out).println(str)
        }
    }
    fun println(any: Any?, err: Boolean = false) = println(any.toString(), err)
    fun isSelected() = globalConfig.verboseEnabled.contains(this)
}



data class Config(
        val format: OutputFormat,
        val nasmCommand: String,
        val gccCommand: String,
        val buildDir: String,
        val optimizationsEnabled: List<Optimization>,
        val verboseEnabled: List<VerboseOption>,
        val optimizerIterations: Int,
        val target: String,
        val outputBinary: String
)




class ConsumableQueue<T>(creator: Collection<T>) {
    private val queue = ArrayDeque(creator)
    private fun isEmpty() = queue.isEmpty()
    fun peek(): T? = if(isEmpty()) null else queue.first
    fun isNotEmpty() = queue.isNotEmpty()
    fun remove(): T = if(isEmpty()) error("reached end of file while parsing") else queue.remove()
    fun removeWhile(condition: (T) -> Boolean): List<T> {
        val list = mutableListOf<T>()
        while(true){
            if(queue.isEmpty()) error("reached end of file while parsing")
            if(condition(queue.peek())){
                list.add(queue.remove())
            } else {
                return list
            }
        }
    }
}

fun parseConfig(args: List<String>): Config {
    var format: OutputFormat = OutputFormat.CODE
    var nasmCommand = "nasm -felf64 {i} -o {o}"
    var gccCommand  = "gcc -no-pie {i} -o {o}"
    var buildDir    = "build/"
    val optimizationsEnabled = mutableListOf<Optimization>()
    val verboseEnabled = mutableListOf<VerboseOption>()
    var optimisationIterations = 0
    var target: String? = null
    var outputBinary = "a.out"

    val queue = ConsumableQueue(args)

    fun sortaVerbose() {
        verboseEnabled += listOf(VerboseOption.COMPILE_TIME, VerboseOption.END_TIME)
    }

    while(queue.isNotEmpty()) {
        val token = queue.remove()
        if(token.isBlank()) continue
        when {
            token == "" -> {}
            token == "--help" -> {
                // execute man xenon, and return
                println("[help menu, todo]")
                exitProcess(0)
            }
            token == "--nasm" -> nasmCommand = queue.remove()
            token == "--gcc" -> gccCommand = queue.remove()
            token == "--build" -> buildDir = queue.remove()
            token == "--format" -> {
                val rem = queue.remove()
                val opt = OutputFormat.values().firstOrNull { it.name.toUpperCase() == rem.toUpperCase() }
                        ?: error("can't find output format \"$rem\"")
                format = opt
            }
            token == "--run" -> {
                format = OutputFormat.RUN
            }
            token == "--out" -> outputBinary = queue.remove()
            token == "-t" || token == "--target" -> target = queue.remove()
            token == "--verbose, -v" -> sortaVerbose()
            token == "-vv" || token == "-vall" -> verboseEnabled += VerboseOption.values()
            token.startsWith("-v") -> {
                val (str, remove) = if(token.startsWith("-vno")) {
                    token.substring(4) to true
                } else token.substring(2) to false
                val option = VerboseOption.values().firstOrNull { it.str.toUpperCase() == str.toUpperCase() }
                        ?: error("Can't find verbose option" + token.substring(2).toUpperCase())
                if(remove){
                    verboseEnabled.remove(option)
                } else {
                    verboseEnabled.add(option)
                }
            }
            token.startsWith("-O") -> {
                val i = token.substring(2).toIntOrNull() ?: error("can't convert \"${token.substring(2)}\" to an int")
                optimisationIterations = i
                if(i != 0){
                    optimizationsEnabled += Optimization.values()
                }
            }
            token.startsWith("-o") || token == "--optimize" -> {
                val (str, remove) = when {
                    token == "--optimize" -> {
                        val rem =  queue.remove()
                        if(rem.startsWith("no")) rem.substring(2) to true else rem to false
                    }
                    token.startsWith("-ono") -> token.substring(4) to true
                    else -> token.substring(2) to false
                }
                val option = Optimization.values().firstOrNull { it.str.toUpperCase() == str.toUpperCase() }
                        ?: error("Can't find verbose option" + token.substring(2).toUpperCase())
                if(remove){
                    optimizationsEnabled.remove(option)
                } else {
                    optimizationsEnabled.add(option)
                }
            }
            token.startsWith("-") -> {
                error("unknown option \"$token\"")
            }
            else -> {
                // considered the target
                if(target == null) target = token
                else error("can not specify multiple targets, at: \"$token\"")
            }
        }
    }

    return Config(format, nasmCommand, gccCommand, buildDir, optimizationsEnabled, verboseEnabled, optimisationIterations, target ?: error("target must be spetified"), outputBinary)
}