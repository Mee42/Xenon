package dev.mee42

import dev.mee42.parser.*


private val println = lib {
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
        name = "println"
        id = "_uint"
        arguments = listOf("i".arg("uint"))
        assembly = """
            println_uint:
                mov rdi, printf_format_uint_newline
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
        id = "_long"
        arguments = listOf("l".arg("long"))
        assembly = """
            println_long:
                mov rdi, printf_format_int_newline
                jmp printf_main
        """.trimIndent()
    }
    function {
        name = "println"
        id = "_short"
        arguments = listOf("b".arg("short"))
        assembly = """
            println_short:
                mov rdi, printf_format_int_newline
                jmp printf_main
        """.trimIndent()
    }
    function {
        name = "println"
        id = "_byte"
        arguments = listOf("b".arg("byte"))
        assembly = """
            println_byte:
                mov rdi, printf_format_int_newline
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
    function {
        name = "print"
        id = "_string"
        arguments = listOf("str".arg("char*"))
        assembly = """
            print_string: ; takes in a single string argument
                push rbp
                mov rbp, rsp
                mov rdi, [rbp + 16]
                xor rax, rax
                call printf
                xor rax, rax
                pop rbp
                ret
        """.trimIndent()
    }
    function {
        name = "println"
        id = "_bool"
        arguments = listOf("b".arg("bool"))
        assembly = """
            println_bool:
                push rbp
                mov rbp, rsp
                mov rax, [rbp + 16]
                cmp al, 0
                je println_bool_L1
                mov rdi, printf_format_true
                jmp println_bool_L2
                println_bool_L1:
                mov rdi, printf_format_false
                println_bool_L2:
                xor rax, rax
                call printf
                xor rax, rax
                pop rbp
                ret
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
            printf_format_uint_newline: db "%u", 10,  0
            printf_format_int: db "%i", 0
            printf_format_newline: db 10, 0
            printf_format_char_newline: db "%c", 10, 0
            printf_format_char: db "%c", 0
            printf_format_true: db "true", 10, 0
            printf_format_false: db "false", 10, 0
        """.trimIndent()
    )
}

    //; argument 0 (a) in register DWORD [rbp + 24]
    //; argument 1 (b) in register DWORD [rbp + 16]
private val printf = lib {
    function {
        name = "printf"
        arguments = listOf("format".arg("char*"))
        id = "_no_args"
        assembly = """
            printf_no_args: ; takes in a single string argument
                push rbp
                mov rbp, rsp
                mov rdi, [rbp + 16]
                xor rax, rax
                call printf
                xor rax, rax
                pop rbp
                ret
        """.trimIndent()
    }
    extraText("""
        printf_one_arg:
            push rbp
            mov rbp, rsp
            mov rdi, [rbp + 24]
            mov rsi, [rbp + 16]
            xor rax, rax
            call printf
            pop rbp
    """.trimIndent())
    function {
        name = "printf"
        id = "_one_arg_int"
        arguments = listOf("str".arg("char*"), "i".arg("int"))
        assembly = "printf_one_arg_int: \njmp printf_one_arg"
    }
    function {
        name = "printf"
        id = "_one_arg_string"
        arguments = listOf("str".arg("char*"), "i".arg("char*"))
        assembly = "printf_one_arg_string: \n    jmp printf_one_arg"
    }
}

private val casts = lib {
    function {
        name = "cast_char"
        id = "_int"
        arguments = listOf("i".arg("int"))
        returnType = type("char")
        attrib("@pure")
        assembly = """
        cast_char_int:
            mov rax, [rsp + 8]
            ret
        """.trimIndent()
    }
    function {
        name = "cast_short"
        id = "_int"
        attrib("@pure")
        arguments = listOf("i".arg("int"))
        returnType = type("short")
        assembly  = """
            cast_short_int:
                mov rax, [rsp + 8]
                ret
        """.trimIndent()
    }
    function {
        name = "cast_long"
        attrib("@pure")
        id = "_int"
        arguments = listOf("i".arg("int"))
        returnType = type("long")
        assembly = """
            cast_long_int:
                mov rax, [rsp + 8]
                ret
        """.trimIndent()
    }

    // 0x06 F <- string
    // 0x07 0 <- i
    function {
        name = "cast_int"
        id = "_int_ptr"
        attrib("@pure")
        arguments = listOf("i".arg("ubyte*"))
        returnType = type("int")
        assembly = """
            cast_int_int_ptr:
                mov rax, [rsp + 8]
                ret
        """.trimIndent()
    }


    // casts to int
    function {
        name = "cast_uint"
        attrib("@pure")
        id = "_ubyte"
        arguments = listOf("x".arg("ubyte"))
        returnType = type("uint")
        assembly = """
            cast_uint_ubyte:
                xor rax, rax
                mov al, BYTE [rsp + 8]
                ret
        """.trimIndent()
    }
    function {
        name = "cast_uint"
        attrib("@pure")
        id = "_ushort"
        arguments = listOf("x".arg("ushort"))
        returnType = type("uint")
        assembly = """
            cast_uint_ushort:
                xor rax, rax
                mov ax, WORD [rsp + 8]
                ret
        """.trimIndent()
    }

    function {
        name = "cast_uint"
        attrib("@pure")
        id = "_ulong"
        arguments = listOf("x".arg("ulong"))
        returnType = type("uint")
        assembly = """
            cast_uint_ulong:
                mov rax, QWORD [rsp + 8]
                ret
        """.trimIndent()
    }

}

private val bools = lib {
    function {
        name = "true"
        id = "_bool"
        attrib("@pure")
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
        attrib("@pure")
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
        attrib("@pure")
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
        attrib("@pure")
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
        attrib("@pure")
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

private val malloc = lib {
    function {
        name = "malloc_int"
        id = ""
        arguments = listOf("count".arg("int"))
        returnType = type("int*")
        assembly = """
        malloc_int:    
            push rbp
            mov rbp, rsp
            mov rdi, [rbp + 16]
            add rdi, rdi
            add rdi, rdi
            xor rax, rax
            call malloc
            pop rbp
            ret
        """.trimIndent()
    }
    function {
        name = "malloc_byte"
        id = ""
        arguments = listOf("count".arg("int"))
        returnType = type("byte*")
        assembly = """
        malloc_byte:    
            push rbp
            mov rbp, rsp
            mov rdi, [rbp + 16]
            xor rax, rax
            call malloc
            pop rbp
            ret
        """.trimIndent()
    }
    function {
        name = "malloc_char"
        id = ""
        arguments = listOf("count".arg("int"))
        returnType = type("char*")
        assembly = """
        malloc_char:
            jmp malloc_byte
        """.trimIndent()
    }
    function {
        name = "free"
        id = "_char"
        arguments = listOf("ptr".arg("char*"))
        returnType = type("void")
        assembly = """
        free_char:
            push rbp
            mov rbp, rsp
            mov rdi, [rbp + 16]
            xor rax, rax
            call malloc
            pop rbp
            ret
        """.trimIndent()
    }
}

val stdlib = println + casts + bools + malloc + printf
val stdLibDef = InitialAST(stdlib.functions.map { it.toInitialFunction() }, emptyList())

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
    val assembly: String/* this includes the label */,
    val attributes: List<String>
) {
    fun toInitialFunction(): InitialFunction {
        return InitialFunction(
            name = name,
            id = id,
            arguments = arguments.map { it.asTyped<UntypedArgument, Argument>() },
            attributes = attributes,
            returnType = returnType.asTyped(),
            content = null)
    }
}

fun type(str: String): Type {
    return when {
        str.endsWith("*") -> PointerType(type(str.dropLast(1)), true)
        str.endsWith("+") -> PointerType(type(str.dropLast(1)), false)
        else -> BaseType(TypeEnum.of(str))
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
    private val attributes = mutableListOf<String>()
    fun attrib(str: String) { attributes.add(str) }
    fun build(): CompiledFunction {
        return CompiledFunction(name!!, id!!, arguments, returnType, assembly!!, attributes)
    }
}
@DslMarker
annotation class Builder