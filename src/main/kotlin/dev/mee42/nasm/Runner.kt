package dev.mee42.nasm

import dev.mee42.asm.AssemblyInstruction
import dev.mee42.asm.Register
import dev.mee42.asm.RegisterSize
import dev.mee42.asm.SizedRegister
import dev.mee42.parser.TypeEnum
import java.io.File
import java.util.concurrent.TimeUnit

fun run(returnType: TypeEnum, asm: List<AssemblyInstruction>, vararg arguments: Int, argumentsAreBig: Boolean = false) =
    run(returnType, asm, arguments.map { it.toLong() }, argumentsAreBig)

fun run(returnType: TypeEnum, asm: List<AssemblyInstruction>, vararg arguments: Long, argumentsAreBig: Boolean = false) =
    run(returnType, asm, arguments.toList(), argumentsAreBig)

fun run(returnType: TypeEnum, asm: List<AssemblyInstruction>, argumentsAreBig: Boolean = false) =
    run(returnType, asm, emptyList(), argumentsAreBig)

// TODO fix this up to do type checks
/** writes the assembly to a file, calls the function, tells you what the result is. Throws an exception if compilation fails*/
private fun run(returnType: TypeEnum, asm: List<AssemblyInstruction>, arguments: List<Long>, argumentsAreBig: Boolean): String {
    val id = randomID()
    println(id)
    // write it to a file
    File("gen/tests/$id").mkdirs()
    val filePath = "gen/tests/$id/$id"
    val file = File("$filePath.asm")
    val stringContent = StringBuilder()
    stringContent.append("""
extern printf

section .text
    global main

main:

    """.trimIndent())

    for (argument in arguments) {
        if(argumentsAreBig) {
            stringContent.append("    mov rax, $argument\n    push rax\n")
        } else {
            stringContent.append("    push $argument\n")
        }
    }

        stringContent.append("""
    call foo
    add rsp, ${arguments.size * 8}
    
    """.trimIndent())


    val inputRegister = SizedRegister(returnType.registerSize, Register.A)
    val movLine =
        if(returnType.isUnsigned && returnType.registerSize in listOf(RegisterSize.BIT8, RegisterSize.BIT16)) {
            "movzx rbx, $inputRegister"
        } else if(returnType.isUnsigned) {
            "mov ${SizedRegister(returnType.registerSize, Register.B)}, $inputRegister" // zero-extended mov is implicit
        } else if(returnType.registerSize == RegisterSize.BIT64)/*returnType is signed*/ {
            "mov rbx, $inputRegister" // it's the same size, no movxs needed
        } else {
            "movsx rbx, $inputRegister" // inputRegister is smaller then rbx, needs
        }

    stringContent.append("""
    $movLine
    call println
    mov rax, 0
    ret

    """.trimIndent())
    stringContent.append(asm.fold("") {a,b  -> "$a\n$b" })
    stringContent.append("\n\n")
    stringContent.append("""

println: ; function println(int64 i)
          ; returns the output of printf
          ; i is stored in rbx
          ; this overwrites rdi and rsi
    mov rdi, string
    mov rsi, rbx
    mov rax, 0
    push rcx
    push rdx
    call printf
    pop rdx
    pop rcx
    mov rax, rbx
    ret


    """.trimIndent())
    val printfFormatter = when(returnType.isUnsigned) {
        true -> "%lu"
        false -> "%li"
    }
    stringContent.append("""
        section .data
            string: db "$printfFormatter", 10, 0
    """.trimIndent())

    file.writeText(stringContent.toString())

    val pb = ProcessBuilder("bash","-c", "nasm -felf64 $filePath.asm -o $filePath.o && gcc $filePath.o -o $filePath -no-pie")
    pb.inheritIO()
    val result = pb.start().waitFor()
    if(result != 0) error("exiting with code $result")
    val runProcess = ProcessBuilder("bash", "-c", filePath)
    val process = runProcess.start()
    process.waitFor()
    val stdout = String(process.inputStream.readAllBytes()).trim()
    val stderr = String(process.errorStream.readAllBytes()).trim()
    if(stderr.isNotBlank()) error("stdout: $stdout\n stderr: $stderr")

    val exitCode = process.exitValue()
    if(exitCode != 0) error("exited with code $exitCode\nstdout: $stdout\n stderr: $stderr\n")
    return stdout
}

fun randomID(): String {
    val s = StringBuilder()
    for(i in 0 until 10)
        s.append(('a'..'z').random())
    return s.toString()
}