import dev.mee42.*
import io.kotest.core.spec.style.StringSpec
import junit.framework.Assert.assertEquals
import java.io.File


fun testInOut(input: String, output: String) {
    fun String.fix() = this.trimIndent().split("\n").joinToString("\n") { it.trimEnd() }
    assertEquals(output.fix(), paintAllYields(parse(lex(input))).str().fix())
}

class ParserTests: StringSpec({
    val tests = File("res/parserTests.dip.xn").readText().split(Regex("(:?=){10,}"))
    for(test in tests) {
        parseTest(test)
    }
})

private fun StringSpec.parseTest(test: String) {
    if(test.isBlank()) return
    val (name, content) = test.trim().split("\n", limit = 2)
    val (i, o) = content.split("<=>")
    name {
        testInOut(i, o)
    }
}