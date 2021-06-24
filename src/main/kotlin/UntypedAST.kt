package dev.mee42

import java.lang.IllegalStateException


typealias VariableIdentifier = String
typealias TypeIdentifier = String
typealias LabelIdentifier = String

data class UntypedAST(val functions: List<UntypedFunction>, val structs: List<UntypedStruct>)

data class UntypedStruct(val name: VariableIdentifier,
                         val generics: List<String>,
                         val fields: List<Pair<UnrealizedType, VariableIdentifier>>)

data class UntypedFunction(val name: VariableIdentifier,
                            val retType: UnrealizedType,
                            val arguments: List<UntypedArgument>,
                            val body: UntypedExpr.Block,
                            val generics: List<String>,
                            val isExtension: Boolean)

data class UntypedArgument(val name: VariableIdentifier, val type: UnrealizedType)

sealed interface UntypedExpr: TWAble {
    data class Block(val sub: List<UntypedExpr>, val label: LabelIdentifier?): UntypedExpr
    data class VariableDefinition(val variableName: VariableIdentifier, val value: UntypedExpr?, val type: UnrealizedType?, val isConst: Boolean): UntypedExpr
    data class Assignment(val left: UntypedExpr, val right: UntypedExpr): UntypedExpr
    data class FunctionCall(val functionName: VariableIdentifier, val arguments: List<UntypedExpr>, val generics: List<UnrealizedType>): UntypedExpr
    data class PrefixOp(val right: UntypedExpr, val op: String): UntypedExpr
    data class BinaryOp(val left: UntypedExpr, val right: UntypedExpr, val op: String): UntypedExpr
    data class VariableAccess(val variableName: VariableIdentifier): UntypedExpr
    data class Return(val expr: UntypedExpr?): UntypedExpr
    data class NumericalLiteral(val number: String): UntypedExpr
    data class StringLiteral(val content: String): UntypedExpr
    data class CharLiteral(val char: Char): UntypedExpr
    data class If(val cond: UntypedExpr, val ifBlock: UntypedExpr, val elseBlock: UntypedExpr?): UntypedExpr
    data class Loop(val block: Block): UntypedExpr
    data class Break(val value: UntypedExpr?, val label: LabelIdentifier?): UntypedExpr
    data class Continue(val label: LabelIdentifier?): UntypedExpr
    data class MemberAccess(val expr: UntypedExpr, val memberName: VariableIdentifier, val isArrow: Boolean): UntypedExpr
    data class StructDefinition(val type: UnrealizedType?, val members: List<Pair<VariableIdentifier, UntypedExpr>>): UntypedExpr
}

sealed interface UnrealizedType {
    data class Pointer(val subType: UnrealizedType): UnrealizedType // <whatever>*
    object Nothing: UnrealizedType // Nothing
    object Unit: UnrealizedType    // Unit
    data class NamedType(val name: TypeIdentifier, val genericTypes: List<UnrealizedType>): UnrealizedType // List[Tuple[Array[Int], Ptr[Char]]]
}
