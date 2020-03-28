package dev.mee42

import dev.mee42.parser.*


private val printf = lib {
    function {
        name = "println"
        id = "_int"
        arguments = listOf("i".arg("int"))
        assembly = """
            println_int:
                mov rdi, printf_format_int_newline
                jmp printf_main
        """.trimIndent()
    }
    function {
        name = "print"
        id = "_int"
        arguments = listOf("i".arg("int"))
        assembly = """
            print_int:
                mov rdi, printf_format_int
                jmp printf_main
        """.trimIndent()
    }
    function {
        name = "println"
        id = "_char"
        arguments = listOf("c".arg("char"))
        assembly = """
            println_char:
                mov rdi, printf_format_char_newline
                jmp printf_main
        """.trimIndent()
    }
    function {
        name = "print"
        id = "_char"
        arguments = listOf("c".arg("char"))
        assembly = """
            print_char:
                mov rdi, printf_format_char
                jmp printf_main
        """.trimIndent()
    }
    function {
        name = "println"
        id = "_default"
        arguments = emptyList()
        assembly = """
            println_default:
                mov rdi, printf_format_newline
                jmp printf_main
        """.trimIndent()
    }

    extraText("""
        printf_main:
            push rbp
            mov rbp, rsp
            ;mov rdi, string
            mov rsi, [rbp + 16]
            xor rax, rax
            call printf
            xor rax, rax
            pop rbp
            ret
    """.trimIndent())
    //language=NASM
    extraData(
        """
            printf_format_int_newline: db "%i", 10,  0
            printf_format_int: db "%i", 0
            printf_format_newline: db 10, 0
            printf_format_char_newline: db "%c", 10, 0
            printf_format_char: db "%c", 0
        """.trimIndent()
    )
}

private val casts = lib {
    function {
        name = "cast_char"
        id = "_int"
        arguments = listOf("i".arg("int"))
        returnType = type("char")
        assembly = """
        cast_char_int:
            mov rax, [rsp + 8]
            ret
        """.trimIndent()
    }
    function {
        name = "cast_long"
        id = "_int"
        arguments = listOf("i".arg("int"))
        returnType = type("long")
        assembly = """
            cast_long_int:
                mov rax, [rsp + 8]
                ret
        """.trimIndent()
    }
}
private val bools = lib {
    function {
        name = "true"
        id = "_bool"
        arguments = emptyList()
        returnType = type("bool")
        assembly = """
            true_bool:
                mov al, 1
                ret
        """.trimIndent()
    }
    function {
        name = "false"
        id = "_bool"
        arguments = emptyList()
        returnType = type("bool")
        assembly = """
            false_bool:
                mov al, 0
                ret
        """.trimIndent()
    }
    function {
        name = "and"
        id = "_bool"
        arguments = listOf("a".arg("bool"),"b".arg("bool"))
        returnType = type("bool")
        assembly = """
            and_bool:
                push rbp
                mov rbp, rsp
                mov al, [rbp + 24]
                mov bl, [rbp + 16]
                and al, bl
                pop rbp
                ret
        """.trimIndent()
    }
    function {
        name = "or"
        id = "_bool"
        arguments = listOf("a".arg("bool"),"b".arg("bool"))
        returnType = type("bool")
        assembly = """
            or_bool:
                push rbp
                mov rbp, rsp
                mov al, [rbp + 24]
                mov bl, [rbp + 16]
                or al, bl
                pop rbp
                ret
        """.trimIndent()
    }
    function {
        name = "not"
        id = "_bool"
        arguments = listOf("b".arg("bool"))
        returnType = type("bool")
        assembly = """
            not_bool:
                mov rax, [rsp + 8]
                cmp al, 0
                je not_bool_L1
                mov al, 0
                ret
                not_bool_L1:
                mov al, 1
                ret
        """.trimIndent()
    }
}
val stdlib = printf + casts + bools

class XenonLibrary(val functions: List<CompiledFunction>,
                   val extraText: String,
                   val extraData: String) {
    operator fun plus(other:XenonLibrary): XenonLibrary {
        return XenonLibrary(
            functions + other.functions,
            extraText + "\n\n" + other.extraText,
            extraData + "\n\n" + other.extraData
        )
    }
}

data class CompiledFunction(
    val name: String,
    val id: String,
    val arguments: List<Argument>,
    val returnType: Type,
    val assembly: String /* this includes the label */
) {
    fun toInitialFunction(): InitialFunction {
        return InitialFunction(
            name = name,
            id = id,
            arguments = arguments,
            attributes = emptyList(),
            returnType = returnType,
            content = null)
    }
}

fun type(str: String): Type {
    return if(str.endsWith("*")){
        PointerType(type(str.substring(1)))
    } else {
        BaseType(TypeEnum.of(str))
    }
}
fun String.arg(type: String) = Argument(this, type(type))

private fun lib(block: LibBuilder.() -> Unit): XenonLibrary {
    return LibBuilder().apply(block).build()
}


@Builder
private class LibBuilder {
    private val functions = mutableListOf<CompiledFunction>()
    fun function(block: FunctionBuilder.() -> Unit) {
        functions.add(FunctionBuilder().apply(block).build())
    }
    private var text: String = ""
    fun extraText(text: String) {
        this.text += text
        if(!this.text.endsWith("\n")) this.text += "\n"
    }
    private var data: String = ""
    fun extraData(data: String) {
        this.data += data
        if(!this.data.endsWith("\n")) this.data += "\n"
    }
    fun build() = XenonLibrary(functions, text, data)
}
@Builder
private class FunctionBuilder {
    var name: String? = null
    var id: String? = null
    var arguments: List<Argument> = emptyList()
    var returnType: Type = type("void")
    var assembly: String? = null /* this includes the label */
    fun build(): CompiledFunction {
        return CompiledFunction(name!!, id!!, arguments, returnType, assembly!!)
    }
}
@DslMarker
annotation class Builder