package dev.mee42

import dev.mee42.asm.AssemblyInstruction
import dev.mee42.asm.assemble
import dev.mee42.nasm.run
import dev.mee42.parser.TypeEnum
import dev.mee42.parser.parsePass1
import dev.mee42.parser.parsePass2
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.*
import kotlin.math.sqrt
import kotlin.random.Random

private interface Generator<A> {
    fun static(): List<A>
    fun dynamic(): Sequence<A>
}
private fun <A> Generator<A>.filter(conditional: (A) -> Boolean) :Generator<A> {
    class FilteredGenerator(val gen: Generator<A>) : Generator<A> {
        override fun static() = gen.static().filter(conditional)
        override fun dynamic(): Sequence<A> = gen.dynamic().filter(conditional)
    }
    return FilteredGenerator(this)
}
private fun <A,B> Generator<A>.map(map: (A) -> B): Generator<B> {
    class MapGenerator(val a: Generator<A>) : Generator<B> {
        override fun static(): List<B> = a.static().map(map).distinct()
        override fun dynamic(): Sequence<B> = a.dynamic().map(map)
    }
    return MapGenerator(this)
}
private class ZippedGenerator<A,B>(val a: Generator<A>, val b: Generator<B>): Generator<Pair<A,B>> {
    override fun static(): List<Pair<A, B>> {
        return a.static().flatMap { a -> b.static().map { b -> a to b } }
    }
    override fun dynamic(): Sequence<Pair<A, B>> {
        return a.dynamic().zip(b.dynamic())
    }
}
private fun <A,B> Generator<A>.zip(other: Generator<B>): ZippedGenerator<A,B> {
    return ZippedGenerator(this, other)
}
private fun <A,B,C> ZippedGenerator<A,B>.zip3(other: Generator<C>): Generator<Triple<A,B,C>> {
    class ZippedGenerator3(val a: Generator<A>, val b: Generator<B>, val c: Generator<C>): Generator<Triple<A,B,C>> {
        override fun static(): List<Triple<A, B, C>> {
            return a.static().flatMap { a ->
                b.static().flatMap { b ->
                    c.static().map { c ->
                        Triple(a, b, c)
                    }
                }
            }
        }
        override fun dynamic(): Sequence<Triple<A, B, C>> {
            return a.dynamic().zip(b.dynamic()).zip(c.dynamic()) { (a, b), c -> Triple(a,b,c) }
        }
    }
    return ZippedGenerator3(a, b, other)
}

private object IntGenerator: Generator<Int> {
    override fun static(): List<Int> {
        return listOf(-1, 0, 1, -100, 100)
    }

    override fun dynamic(): Sequence<Int> {
        return generateSequence { Random.nextInt() }
    }
}
private val smallInts = IntGenerator.map { it / 10 }.filter { it != 0 }
private val positiveInts = IntGenerator.filter { it >= 0 }
private val smallPositiveInts = positiveInts.map { it / 10 }
private fun <A> forAll(iterations: Int, generator: Generator<A>, block: (A) -> Unit) {
    if(iterations < generator.static().size) error("to little")
    generator.static().forEach(block)
    generator.dynamic().take( iterations - generator.static().size).forEach(block)
}

/*
class MathTests: StringSpec({
    "int addition" {
        val add = parse("""
            function foo(int a, int b) int {
                return a + b;
            }
        """.trimIndent())
        forAll(10, smallInts.zip(smallInts)) { (a: Int, b: Int) ->
            print("$a + $b: ")
            run(TypeEnum.of("int"), add, a, b).toInt() shouldBe a + b
        }
    }
    "int subtraction" {
        val sub = parse("""
            function foo(int a, int b) int {
                return a - b;
            }
        """.trimIndent())
        forAll(10, smallInts.zip(smallInts)) { (a: Int, b: Int) ->
            print("$a - $b: ")
            run(TypeEnum.of("int"),sub, a, b).toInt() shouldBe a - b
        }
    }
    "int multiplication" {
        val mult = parse("""
            function foo(int a, int b) int {
                return a * b;
            }
        """.trimIndent())
        forAll(10, smallInts.zip(smallInts)) { (a: Int, b: Int) ->
            print("$a * $b: ")
            run(TypeEnum.of("int"), mult, a, b).toInt() shouldBe a * b
        }
    }
    "unsigned division" {
        val div = parse("""
            function foo(uint a, uint b) uint {
                return a / b;
            }
        """.trimIndent())
        forAll(10, smallPositiveInts.zip(smallPositiveInts.filter { it != 0 })) { (a: Int, b: Int) ->
            print("$a / $b: ")
            run(TypeEnum.of("uint"), div, a, b).toInt() shouldBe a / b
        } // unsigned division
    }
    "signed division" {
        val div = parse("""
            function foo(long a, long b) long {
                return a / b;
            }
        """.trimIndent())
        forAll(10, smallInts.zip(smallInts.filter { it != 0 })) { (a,b) ->
            print("$a / $b: ")
            run(TypeEnum.of("long"), div, a, b).toInt() shouldBe a / b
        }
    }
    "adding 3 elements" {
        val add = parse("""
            function foo(int a, int b, int c) int {
                return a + b + c;
            }
        """.trimIndent())
        forAll(10, smallInts.zip(smallInts).zip3(smallInts)) { (a, b, c) ->
            print("$a + $b + $c: ")
            run(TypeEnum.of("int"), add, a, b, c).toInt() shouldBe a + b + c
        }
    }
    "add and multiply" {

        val addAndMult = parse("""
            function foo(int a, int b, int c) int {
                return (a * b) + c;
            }
        """.trimIndent())
        forAll(10,  smallInts.zip(smallInts).zip3(smallInts)) { (a, b, c) ->
            print("$a * $b + $c: ")
            run(TypeEnum.of("int"), addAndMult, a, b, c).toInt() shouldBe a * b + c
        }
    }

})
*/
class MainTests: StringSpec({
    "basic program compiles" {
        run(TypeEnum.of("int"), parse("""
            function foo() int {
                return 7;
            }
        """.trimIndent())).toInt() shouldBe 7
    }
    "id function works" {
        val id = parse("""
            function foo(int a) int {
                return a;
            }
        """.trimIndent())
        forAll(10, IntGenerator) { a ->
            run(TypeEnum.of("int"), id, a).toInt() shouldBe a
        }
    }
    "id functions with a static variable" {
        val id = parse("""
            function foo(int a) int {
                val b = a;
                return b;
            }
        """.trimIndent())
        forAll(10, IntGenerator) { a ->
            run(TypeEnum.of("int"), id, a).toInt() shouldBe a
        }
    }
})

class TestUByte: StringSpec({testsForType("ubyte")})
class TestByte: StringSpec({testsForType("byte")})
class TestUShort: StringSpec({testsForType("ushort")})
class TestShort: StringSpec({testsForType("short")})
class TestUInts: StringSpec({testsForType("uint")})
class TestInts: StringSpec({testsForType("int")})
class TestULong: StringSpec({testsForType("ulong")})
class TestLong: StringSpec({testsForType("long")})

private fun StringSpec.testsForType(type: String) {
    val typef = TypeEnum.of(type)
    "using variable of type $type for id function" {
        run(typef,parse("function foo($type a) $type { val b = a; return b; }"), 7).toInt() shouldBe 7
    }
    "id function with int type $type" {
        run(typef,parse("function foo($type a) $type { return a }"), 7).toInt() shouldBe 7
    }
    for((name, operator, op) in listOf<Triple<String, String, (Long, Long) -> Long>>(
        "adding" to "+" to3 Long::plus,
        "subtracting" to "-" to3 Long::minus,
        "multiplying" to "*" to3 Long::times,
        "dividing" to "/" to3 Long::div
    )) {
        "$name integers of type $type" {
            val program = parse("function foo($type a, $type b) $type { return a $operator b }")
            for ((a, b) in mapOf(
                0L to 0L,
                7L to 0L,
                0L to 7L,
                7L to 7L
            )) {
                if(name == "dividing" && b == 0L) continue
                if(typef.isUnsigned && op(a,b) < 0) continue
                // these will fit into every data type
                run(typef, program, a, b).toLong() shouldBe op(a,b)
            }

            doTimes(5, 100) {
                // pick two random numbers, make sure the addition fits into the result, then do the operaton
                val a = if(name == "multiplying") typef.numberRange.smallRange.random() else typef.numberRange.random()
                val b = if(name == "multiplying") typef.numberRange.smallRange.random() else typef.numberRange.random()
                if(name == "dividing" && b == 0L) return@doTimes false

                val result = op(a,b)
                if(result !in typef.numberRange) return@doTimes false
                val bigArgs = type == "long" || type == "ulong"
                run(typef,program, a, b, argumentsAreBig = bigArgs).toLong() shouldBe result
                return@doTimes true
            }
        }
    }
}

private val LongRange.smallRange
    get() = sqrt(start.toDouble()).toLong()..sqrt(last.toDouble()).toLong()

private inline fun doTimes(i: Int,max: Int, block: () -> Boolean) {
    var index = 0
    var count = 0
    while(index < i) {
        if(block()) index++
        count++
        if(count > max) error("reached max")
    }
}

private infix fun <A,B,C> Pair<A,B>.to3(other: C): Triple<A,B,C> = Triple(first, second, other)

private fun parse(string: String): List<AssemblyInstruction> {
//    val preprocessed = dev.mee42.xpp.preprocess(string)
//    val tokens = dev.mee42.lexer.lex(preprocessed)
//    val initialAST = parsePass1(tokens).withOther(stdlib)
//    val ast = parsePass2(initialAST)
//    val optimized = dev.mee42.opt.optimize(ast)
//    return assemble(optimized)
    TODO()
}