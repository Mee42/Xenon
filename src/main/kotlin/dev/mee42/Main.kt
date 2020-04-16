package dev.mee42


open class CompilerException(message: String = ""): RuntimeException(message)

class InternalCompilerException(message: String): CompilerException(message)
