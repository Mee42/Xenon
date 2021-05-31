package dev.mee42

import java.lang.IllegalStateException


// unspecialized
data class GenericStruct(
    val name: TypeIdentifier,
    val genericTypes: List<TypeIdentifier>,
    val fields: List<Pair<Type, VariableIdentifier>>, // can include generic types
)
data class Argument(val name: VariableIdentifier, val type: Type)
data class GenericFunction(
   val name: VariableIdentifier,
   val arguments: List<Argument>,
   val returnType: Type,
   val isExtension: Boolean,
   val body: UntypedExpr.Block,
   val generics: List<TypeIdentifier>
)

data class FunctionHeader(
    val name: VariableIdentifier,
    val arguments: List<Argument>,
    val returnType: Type,
    val isExtension: Boolean,
    val genericNames: List<TypeIdentifier>,
    val genericsInfo: Map<TypeIdentifier, Type>
) {
    init {
        returnType.assertNoneGeneric()
        arguments.forEach { (_, t) -> t.assertNoneGeneric() }
    }
}

data class Function(val header: FunctionHeader, val body: Expr.Block)

data class AST(val functions: List<Function>, val structs: List<GenericStruct>)

fun type(untypedAST: UntypedAST): AST {
    // we start by turning every struct into a generic struct
    val structs = generateGenericStructs(untypedAST.structs)


    // we then compile all the functions into genericFunctions.
    // generic functions don't contain an analysed body
    // every function is turned into a GenericFunction
    val functions = untypedAST.functions.map { generateGenericFunction(it, untypedAST.structs) }


    // then we loop over each non-generic GenericFunction and call "specalize" on it, which adds it to the compile queue.
    val compileQueue = ArrayDeque<FunctionHeader>() // things we need to compile
    // for memoization
    val compiledFunctions = mutableMapOf<FunctionHeader, Function>()

    fun specialized(genericFunction: GenericFunction, genericTypes: List<Type>): FunctionHeader {
        // generate the header
        if (genericFunction.generics.size != genericTypes.size) {
            error(
                "ICE: got $genericTypes but was trying to fill generic type list ${genericFunction.generics} and the sizes did not match, " +
                        "while compiling specalization for function named ${genericFunction.name}"
            )
        }
        val genericMap = genericFunction.generics.zip(genericTypes).toMap()
        // generate function header for the type
        val header = FunctionHeader(
            name = genericFunction.name,
            arguments = genericFunction.arguments.map { (name, type) ->
                Argument(
                    name,
                    type.replaceGenerics(genericMap)
                )
            },
            returnType = genericFunction.returnType.replaceGenerics(genericMap),
            isExtension = genericFunction.isExtension,
            genericNames = genericFunction.generics,
            genericsInfo = genericMap
        )
        if (!compiledFunctions.containsKey(header)) {
            compileQueue.addLast(header) // if we haven't compiling it yet.
            // note: this isn't a catch-all, some duplicates will still be pushed to the list, so it's important to check for every single function
        }
        return header
    }
    for (func in functions) {
        if (func.generics.isEmpty()) {
            specialized(func, emptyList()) // just fill the compile queue with these functions
        }
    }


    // now, we flush out the compile queue
    while (compileQueue.isNotEmpty()) {
        val function = compileQueue.removeFirst()
        if (compiledFunctions.containsKey(function)) continue // skip, already done!
        if (function.name == "cast") continue // INTRINSIC: idk how else to implement this
        val body = try {
            compileFunctionBlock(
                block = functions.first { it.name == function.name }.body, // just look up the body
                functions = functions,
                structs = structs,
                untypedStructs = untypedAST.structs,
                genericNames = function.genericNames.toSet(),
                removeGenerics = { it.replaceGenerics(function.genericsInfo) },
                specializeFunction = ::specialized,
                arguments = function.arguments,
                functionIdentifier = function.name
            )
        } catch (e: IllegalStateException) {
            throw IllegalStateException("while compiling function ${function.name}", e)
        }
        val compiledFunction = Function(
            header = function,
            body = body
        )
        if (compiledFunction.body.type != function.returnType) {
            error("Function ${function.name} returns ${function.returnType} but the body return type is ${compiledFunction.body.type}")
        }
        compiledFunctions[function] = compiledFunction
    }

    compiledFunctions.map { (_, function) -> loopValidate(function.body, emptyMap()) }
    return AST(compiledFunctions.values.toList(), structs)
}


fun generateGenericFunction(function: UntypedFunction, structs: List<UntypedStruct>): GenericFunction {
    val genericsNames = function.generics.toSet()
    return GenericFunction(
        name = function.name,
        arguments = function.arguments.map { (name, type) ->
            Argument(name, initialTypePass(type, structs, genericsNames))
        },
        returnType = initialTypePass(function.retType, structs, genericsNames),
        body = function.body,
        generics = function.generics,
        isExtension = function.isExtension
    )
}


// for the most part, constructs and validates the Types
fun generateGenericStructs(structs: List<UntypedStruct>): List<GenericStruct> {

    return structs.map { struct ->
        GenericStruct(
            name = struct.name,
            genericTypes = struct.generics,
            fields = struct.fields.map { (type, name) ->
                initialTypePass(type, structs, struct.generics.toSet()) to name
            }
        )
    }
}


// this goes over and types all the types
// this does NOT replace any generics or anything
// just sorts it into the right categories and checks the arities on struct calls
// checks to make sure the types actually exist, etc
fun initialTypePass(type: UnrealizedType, structs: List<UntypedStruct>, genericNames: Set<TypeIdentifier>): Type = when(type){
    is UnrealizedType.NamedType -> {
        val builtinLookup = Type.Builtin.allMap[type.name]
        when {
            builtinLookup != null -> builtinLookup
            genericNames.contains(type.name) -> {
                if (type.genericTypes.isNotEmpty()) error("Not expecting generic types on generic type ${type.name}")
                Type.Generic(type.name)
            }
            else -> {
                val struct = structs.firstOrNull { it.name == type.name } ?: error("Can't resolve type ${type.name}.")
                // check to make sure we have the right arity of generics
                if(struct.generics.size != type.genericTypes.size) {
                    error("was expecting ${struct.generics.size} generic arguments, but found ${type.genericTypes.size}")
                }
                Type.Struct(struct.name, type.genericTypes.map { initialTypePass(it, structs, genericNames) })
            }
        }
    }
    is UnrealizedType.Pointer -> Type.Pointer(initialTypePass(type.subType, structs, genericNames))
    UnrealizedType.Nothing -> Type.Nothing
    UnrealizedType.Unit -> Type.Unit
}




fun Type.replaceGenerics(info: Map<TypeIdentifier, Type>): Type = when(this){
    is Type.Generic -> info[this.identifier] ?: error("tried to replace generic type $this but no information was found to do so")
    is Type.Pointer -> Type.Pointer(inner.replaceGenerics(info))
    is Type.Struct -> Type.Struct(name, genericTypes.map { it.replaceGenerics(info) })
    else -> this
}
