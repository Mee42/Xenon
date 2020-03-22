package dev.mee42.nasm

import dev.mee42.asm.AssemblyInstruction
import java.io.File
import java.util.concurrent.TimeUnit

// TODO fix this up to do type checks
/** writes the assembly to a file, calls the function, tells you what the result is. Throws an exception if compilation fails*/
fun run(asm: List<AssemblyInstruction>, vararg arguments: Int): String {
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
    for(argument in arguments) {
        stringContent.append("    push $argument\n")
    }
    stringContent.append("    call foo\n    add rsp, ")
    stringContent.append(arguments.size * 8)
    stringContent.append("\n    mov rbx, rax\n    call println\n    mov rax, 0\n    ret\n\n\n")
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


section .data
    string: db "%i", 10, 0

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

private fun randomID(): String {
    val s = StringBuilder()
    for(i in 0 until 10)
        s.append(('a'..'z').random())
    return s.toString()
}