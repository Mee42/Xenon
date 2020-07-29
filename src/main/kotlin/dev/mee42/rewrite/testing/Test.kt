package dev.mee42.rewrite.testing

import java.util.*
import kotlin.system.exitProcess

typealias TypeName     = String
typealias Identifier   = String
typealias UnparsedType = String

class UntypedFunction(val content: String,
                      val identifier: Identifier,
                      val returnType: UnparsedType,
                      val arguments: List<Pair<UnparsedType, Identifier>>,
                      val templates: List<TypeName>
)
class UntypedStruct(val identifier: Identifier,
                    val fields: List<Pair<UnparsedType, Identifier>>,
                    val templates: List<TypeName>
)
class TypedFunction(val identifier: Identifier,
                    val returnType: Type,
                    val arguments: List<Pair<Type, Identifier>>,
                    val templates: List<Pair<TypeName, Type?>>
) // partially typed function? is this possible?

data class TypedStruct(val identifier: Identifier,
                       val fields: List<Pair<Type, Identifier>>,
                       val templates: List<Pair<TypeName, Type>>
)

sealed class Type
data class StructType(val identifier: Identifier, val templates: List<Type>): Type() {
    override fun toString(): String {
        return identifier + templates.joinToString(", ", "<", ">")
    }
}
data class TemplateType(val templateIdentifier: Identifier): Type()
object IntType: Type() {
    override fun toString(): String {
       return "IntType"
    }
}
object BoolType: Type(){
    override fun toString(): String {
        return "BoolType"
    }
}

// TODO refactor out
fun type(type: UnparsedType, structArity: Map<Identifier, Int>, templateValues: Map<Identifier, Type?>): Pair<Type, List<Pair<Identifier, List<Type>>>> {
    return when {
        type.startsWith(" ") || type.endsWith(" ") -> type(type.trim(), structArity, templateValues)
        type == "Int" -> IntType to emptyList()
        type == "Bool" -> BoolType to emptyList()
        type in structArity.keys -> StructType(type, emptyList()) to listOf(type to emptyList())
        type in templateValues.keys -> {
            templateValues[type]?.to(emptyList()) ?: (TemplateType(type) to emptyList())
        }
        type.contains("<") && type.last() == '>' -> {
            val (structName, inside_) = type.split('<', limit = 2)
            val inside = inside_.dropLast(1) // the >
            // ok now we parse the top as a struct type
            // ok split inside by `,`, except if the , is inside other <>s
            val insideSplit = mutableListOf<String>()
            var last = 0
            var bracketDepth = 0
            for ((i, char) in inside.withIndex()) {
                if (bracketDepth == 0 && char == ',') {
                    insideSplit += inside.substring(last, i)
                    last = i + 1
                } else if (char == '>') {
                    bracketDepth--
                } else if (char == '<') {
                    bracketDepth++
                }
            }
            insideSplit += inside.substring(last)
            val arity = structArity[structName] ?: error("no struct named $structName")
            if (insideSplit.size != arity) error("arity not matched. Found $insideSplit, which has ${insideSplit.size} elements, but expecting arity of $arity")
            // okay perfect
            val needed = mutableListOf<Pair<Identifier, List<Type>>>()
            val s = StructType(
                    identifier = structName,
                    templates = insideSplit.map {
                        val (t, n) = type(it, structArity, templateValues)
                        needed.addAll(n)
                        t
                    }
            )
            needed.add(structName to s.templates)
            return s to needed
        }
        else -> error("too complex $type")

    }
}


class StructQueue(val queue: ArrayDeque<Pair<Identifier, List<Type>>>)

fun type(untypedStructs: List<UntypedStruct>): List<TypedStruct> {
    // lets just try to parse out a struct called main, no functions right now
    val needed = StructQueue(ArrayDeque())
    val done = mutableMapOf<Pair<Identifier, List<Type>>, TypedStruct>()
    needed.queue.add("Main" to emptyList())
    val structArity = untypedStructs.map { it.identifier to it.templates.size }.toMap()
    while(needed.queue.isNotEmpty()) {
        val (nextID, type) = needed.queue.removeFirst()
        val next = untypedStructs.firstOrNull { it.identifier == nextID } ?: error("can't find struct $nextID")
        if(next.templates.size != type.size) error("template arity mismatched: caller has ${type.size}, callee only has ${next.templates.size}")
        // ok so now lets handle the 0 templates case. the easy one lol
        val new = TypedStruct(
                identifier = next.identifier,
                templates = next.templates.zip(type), // in the 0 case, this is just an empty list
                fields = next.fields.map { (unparsedType, fieldName) ->
                    val (newType, required) = type(unparsedType, structArity, next.templates.zip(type).toMap())
//                    println("parsing field $fieldName, type: $newType, needs: $required")
                    needed.queue.addAll(required)
                    newType to fieldName
                }
        )
        done[nextID to type] = new
    }
    return done.values.toList().map { removeTemplatedTypes(it) }
}

fun removeTemplatedTypes(typedStruct: TypedStruct): TypedStruct {
    fun removeTemplatedTypes(type: Type): Type {
        return when(type) {
            IntType -> IntType
            BoolType -> BoolType
            is StructType -> type.copy(templates = type.templates.map(::removeTemplatedTypes))
            is TemplateType -> typedStruct.templates.firstOrNull { it.first == type.templateIdentifier }?.second ?: error("can't find template type $type")
        }
    }
    return TypedStruct(
            identifier = typedStruct.identifier,
            templates = typedStruct.templates,
            fields = typedStruct.fields.map { (a, b) ->
                removeTemplatedTypes(a) to b
            }
    )
}


fun main() {
    val untyped = listOf(
            UntypedStruct("Main",  listOf("Foo<Bool, Bool>" to "x", "Foo<Int, Foo<Bool, Bool>>" to "z"), emptyList()),
            UntypedStruct("Foo",   listOf(), listOf("A","B"))
    )
    println("==== untyped")
    untyped.forEach(::print)
    println("==== run")
    val type = type(untyped)
    println("==== typed")
    type.forEach(::print)
}
fun print(struct: UntypedStruct) {
    println("struct ${struct.identifier}" + struct.templates.joinToString(", ","<",">") + " {")
    for((type, id) in struct.fields) {
        println("    $type $id")
    }
    println("}")
}

fun print(struct: TypedStruct) {
    println("struct ${struct.identifier}" + struct.templates.joinToString(", ", "<", ">") { (id, type) -> "$type($id)" } + " {")
    for((type, id) in struct.fields) {
        println("    $type $id")
    }
    println("}")
}





