package dev.mee42.arg

import dev.mee42.asm.Assembly
import dev.mee42.asm.assemble
import dev.mee42.decompile
import dev.mee42.lexer.lex
import dev.mee42.opt.optimize
import dev.mee42.parser.*
import dev.mee42.stdLibDef
import dev.mee42.stdlib
import dev.mee42.xpp.preprocess
import java.io.File
import java.time.Duration

fun main(args: Array<String>){
    val config = parseConfig(args.toList())
    globalConfig = config
    VerboseOption.CONFIG.println(config)
    fullRun()
}

fun compile(text: String, buildDir: File, fileName: String = "tmp"): Assembly {
    // TODO make this possible to be called from the propertest framework
    TODO()
}

fun fullRun() {
    // okay, now compile it
    val inputFile = File(globalConfig.target);
    if (!inputFile.exists()) error("target file \"$inputFile\" does not exist")
    val text = inputFile.readText(Charsets.UTF_8)

    // compile
    val buildDir = File(globalConfig.buildDir, globalConfig.target)
    buildDir.mkdirs()

    val preprocessed = time("preprocess") { preprocess(text, globalConfig.target) }
    val tokens = time("lexer") { lex(preprocessed) }
    VerboseOption.TOKENS.println(tokens.joinToString(" ") { it.content })

    val initialAST = time("pass1") { parsePass1(tokens).withOther(stdLibDef) }
    val ast = time("pass2") { markPurity(parsePass2(initialAST)) }
    VerboseOption.DECOMPILE_AST.println(ast.decompile())
    val optimizedAST = if (globalConfig.optimizerIterations != 0)
        time("optimize pass") { optimize(ast, globalConfig.optimizerIterations) }
    else ast
    VerboseOption.DECOMPILE_AST.println(ast.decompile("optimized ast"))
    if(VerboseOption.AST.isSelected()) {
        printAST(optimizedAST)
    }
    val asm = time("assemble") {  assemble(optimizedAST) }
    val optimizedASM = time("optimize asm") { optimize(asm) }
    val asmFile = writeToFile(optimizedASM, buildDir, inputFile.name)
    val objectFile = File(buildDir, "${inputFile.name}.o")
    if(globalConfig.format == OutputFormat.NASM) return
    val nasmCommand = globalConfig.nasmCommand
            .replace("{i}", asmFile.absolutePath).replace("{o}", objectFile.absolutePath)
    VerboseOption.COMMANDS.println("nasm command: \"$nasmCommand\"")
    val pb = ProcessBuilder("bash","-c", nasmCommand)
    pb.inheritIO()

    var result = pb.start().waitFor()
    if(result != 0) error("assembler exiting with code $result")
    if(globalConfig.format == OutputFormat.OBJECT) return
    val gccCommand = globalConfig.gccCommand
            .replace("{i}", objectFile.absolutePath).replace("{o}", globalConfig.outputBinary)
    VerboseOption.COMMANDS.println("gcc command: \"$gccCommand\"")
    val gcc = ProcessBuilder("bash","-c", gccCommand)
    gcc.inheritIO()
    result = gcc.start().waitFor()
    if(result != 0) error("native code compiler exiting with code $result")
    if(globalConfig.format == OutputFormat.CODE) return
    val time = run(File(globalConfig.outputBinary))
    VerboseOption.END_TIME.println("runtime: " + time.toMillis() + "ms", true)
}

private fun run(file: File): Duration {
    val runProcess = ProcessBuilder("bash", "-c", file.path)
    runProcess.inheritIO()
    val (process, time) = timeAndGet("run", print = false) {
        val process = runProcess.start()
        process.waitFor()
        process
    }
    val stderr = String(process.errorStream.readAllBytes()).trim()
    if(stderr.isNotBlank()) error("stderr: $stderr")
    val exitCode = process.exitValue()
    if(exitCode == 139) {
        error("SEGFAULT")
    }
    if(exitCode != 0) error("exited with code $exitCode\n stderr: $stderr\n")
    return time
}


fun <A> timeAndGet(name: String, print: Boolean = true , block: () -> A): Pair<A,Duration> {
    val start = System.nanoTime()
    val a = block()
    val end = System.nanoTime()
    val dur = Duration.ofNanos(end - start)
    if(print || VerboseOption.COMPILE_TIME.isSelected()) println("$name: " + dur.toMillis() + "ms")
    return a to dur
}

fun writeToFile(asm: Assembly, buildDir: File, filename: String): File {
    val stringContent =  """
extern printf
extern malloc

section .text
    global main

[[program_text]]

[[library_text]]


section .data
[[program_data]]

[[library_data]]

    """.trimIndent()
    val file = File(buildDir, "$filename.asm")
    val program = asm.asm.joinToString("\n","","") { it.str }
    val programData = asm.data.joinToString("\n","","") {
        "    " + it.name + ": db " + it.data.joinToString(",","","")
    }
    val libraryText = stdlib.functions.joinToString("\n\n") { it.assembly } + "\n\n" + stdlib.extraText
    file.writeText(stringContent.replace("[[program_text]]", program)
            .replace("[[program_data]]", programData)
            .replace("[[library_text]]",libraryText)
            .replace("[[library_data]]", stdlib.extraData))
    return file
}


fun <A> time(name: String,printIf: VerboseOption = VerboseOption.COMPILE_TIME,  block: () -> A): A {
    val start = System.nanoTime()
    val a = block()
    val end = System.nanoTime()
    printIf.println("$name: " + Duration.ofNanos(end - start).toMillis() + "ms")
    return a
}
