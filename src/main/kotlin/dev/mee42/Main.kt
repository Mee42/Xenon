package dev.mee42

import dev.mee42.asm.AssemblyInstruction
import dev.mee42.asm.assemble
import dev.mee42.nasm.randomID
import dev.mee42.parser.*
import java.io.File
import java.lang.RuntimeException

open class CompilerException(message: String = ""): RuntimeException(message)

class InternalCompilerException(message: String): CompilerException(message)

fun main(args: Array<String>) {
    val file = File(args.firstOrNull() ?: error("you need to specify a file"))
    run(file.readText(Charsets.UTF_8), stdlib)
}
/*
val standardLibrary = listOf(
    InitialFunction(
        name = "println",
        id = "",
        arguments = listOf(Argument(
            name = "i",
            type = BaseType(TypeEnum.INT32)
        )),
        returnType = BaseType(TypeEnum.VOID),
        content = null, // TODO build a dsl for this
        attributes = emptyList()
    ),
    InitialFunction(
        name = "println_ptr",
        id = "",
        arguments = listOf(Argument(
            name = "ptr",
            type = PointerType(BaseType(TypeEnum.INT32))
        )),
        returnType = BaseType(TypeEnum.VOID),
        content = null,
        attributes = emptyList()
    ),
    InitialFunction(
        name = "print",
        id = "",
        arguments = listOf(Argument(
            name = "c",
            type = BaseType(TypeEnum.INT32)
        )),
        returnType = BaseType(TypeEnum.VOID),
        content = null,
        attributes = emptyList()
    ),
    InitialFunction(
        name = "print_newline",
        id = "",
        arguments = emptyList(),
        returnType = BaseType(TypeEnum.VOID),
        content = null,
        attributes = emptyList()
    ),
    InitialFunction(
        name = "malloc_int",
        id = "",
        arguments = listOf(Argument(
            name = "bytes",
            type = BaseType(TypeEnum.INT32)
        )),
        returnType = PointerType(BaseType(TypeEnum.INT32)),
        content = null,
        attributes = emptyList()
    )
)
*/


private fun run(xenon: String, stdlib: XenonLibrary) {
    val id = randomID()
    System.err.println("running, id: $id")
    val compiled = compile(xenon)
    val stringContent =  """
extern printf
extern malloc

section .text
    global main

[[copy]]
 


[[library_text]]


section .data
[[library_data]]

    """.trimIndent()

    File("gen/tests/$id").mkdirs()
    val filePath = "gen/tests/$id/$id"
    val file = File("$filePath.asm")
    val program = compiled.fold("") {a,b  -> "$a\n$b" }.trim()
    val libraryText = stdlib.functions.joinToString("\n\n") { it.assembly } + "\n\n" + stdlib.extraText
    file.writeText(stringContent.replace("[[copy]]", program)
                                .replace("[[library_text]]",libraryText)
                                .replace("[[library_data]]", stdlib.extraData))

    val pb = ProcessBuilder("bash","-c", "nasm -felf64 $filePath.asm -o $filePath.o && gcc $filePath.o -o $filePath -no-pie")
    pb.inheritIO()
    val result = pb.start().waitFor()
    if(result != 0) error("exiting with code $result")
    val runProcess = ProcessBuilder("bash", "-c", filePath)
    runProcess.inheritIO()
    val process = runProcess.start()
    process.waitFor()
    val stderr = String(process.errorStream.readAllBytes()).trim()
    if(stderr.isNotBlank()) error("stderr: $stderr")
    val exitCode = process.exitValue()
    if(exitCode == 139) {
        error("SEGFAULT")
    }
    if(exitCode != 0) error("exited with code $exitCode\n stderr: $stderr\n")
}

private fun compile(string: String): List<AssemblyInstruction> {
    val preprocessed = dev.mee42.xpp.preprocess(string)

    val tokens = dev.mee42.lexer.lex(preprocessed)
//    println("-- tokens --")
//    tokens.map(Token::content).map { "$it "}.forEach(::print)
//    println("\n")

    val initialAST = parsePass1(tokens).withOther(InitialAST(stdlib.functions.map { it.toInitialFunction() }))

    val ast = parsePass2(initialAST)

    val optimized = dev.mee42.opt.optimize(ast)

    return assemble(optimized)
}