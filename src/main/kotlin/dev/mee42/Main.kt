package dev.mee42

import dev.mee42.asm.Assembly
import dev.mee42.asm.StringInterner
import dev.mee42.asm.assemble
import dev.mee42.nasm.randomID
import dev.mee42.opt.optimize
import dev.mee42.parser.*
import java.io.File
import java.lang.RuntimeException
import java.time.Duration

open class CompilerException(message: String = ""): RuntimeException(message)

class InternalCompilerException(message: String): CompilerException(message)

fun main(args: Array<String>) {
    val file = File(args.firstOrNull() ?: error("you need to specify a file"))
    var millis = 0L
    val program  = compile(file.readText(), stdlib)
    time("all") {
        for (i in 0..100) {
            val m = run(program).toMillis()
            if(i > 10) millis += m
        }
    }
    System.out.flush()
    System.err.flush()
    System.err.println("\nTOTAL: $millis ms")
}
fun resetCompiler(){
    StringInterner.reset()
}

private fun <A> timeAndGet(name: String, block: () -> A): Pair<A,Duration> {
    val start = System.nanoTime()
    val a = block()
    val end = System.nanoTime()
    val dur = Duration.ofNanos(end - start)
    println("$name: " + dur.toMillis() + "ms")
    return a to dur
}
private fun <A> time(name: String, block: () -> A): A {
    val start = System.nanoTime()
    val a = block()
    val end = System.nanoTime()
    println("$name: " + Duration.ofNanos(end - start).toMillis() + "ms")
    return a
}

private fun compile(xenon: String, stdlib: XenonLibrary): File {
    val id = randomID()
    System.err.println("running, id: $id")
    val compiled = time("compile") { compile(xenon) }
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

    File("gen/tests/$id").mkdirs()
    val filePath = "gen/tests/$id/$id"
    val file = File("$filePath.asm")
    val program = compiled.asm.joinToString("\n","","") { it.str }
    val programData = compiled.data.joinToString("\n","","") {
        "    " + it.name + ": db " + it.data.joinToString(",","","")
    }
    val libraryText = stdlib.functions.joinToString("\n\n") { it.assembly } + "\n\n" + stdlib.extraText
    file.writeText(stringContent.replace("[[program_text]]", program)
            .replace("[[program_data]]", programData)
            .replace("[[library_text]]",libraryText)
            .replace("[[library_data]]", stdlib.extraData))

    val pb = ProcessBuilder("bash","-c", "nasm -felf64 $filePath.asm -o $filePath.o && gcc $filePath.o -o $filePath -no-pie")
    pb.inheritIO()
    val result = pb.start().waitFor()
    if(result != 0) error("exiting with code $result")
    return File(filePath)
}

private fun run(file: File): Duration {
    val runProcess = ProcessBuilder("bash", "-c", file.path)
    runProcess.inheritIO()
    val (process, time) = timeAndGet("run") {
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

private fun compile(string: String): Assembly {
    val preprocessed = time("preprocess") { dev.mee42.xpp.preprocess(string) }

    val tokens = time("lex") { dev.mee42.lexer.lex(preprocessed) }
//    println("-- tokens --")
//    tokens.map(Token::content).map { "$it "}.forEach(::print)
//    println("\n")

    val initialAST = time("pass1") {
        parsePass1(tokens)
                .withOther(InitialAST(stdlib.functions.map { it.toInitialFunction() }, emptyList()))
    }

    val ast = time("pass2") { parsePass2(initialAST) }

    val optimized = time("optimize") { dev.mee42.opt.optimize(ast) }

    val asm = time("assemble") { assemble(optimized) }

    return time("optimized2 ") { optimize(asm) }
}