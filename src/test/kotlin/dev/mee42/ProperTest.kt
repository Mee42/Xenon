package dev.mee42

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestContext
import io.kotest.matchers.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.ArrayDeque


val testTestTest = """

> main function should return 0
--
int main() {
    return 0
}
--
Num> 0

> function is required to return a value
--
int main() {

}
--
Err> Function `main` does not return a value
""".trimIndent()

private class Test(
        val name: String,
        val code: String,
        val outputNum: Int,
        val compilerError: String?,
        val stdout: String?
)

private fun <T> ArrayDeque<T>.removeWhile(predicate: (T) -> Boolean): List<T> {
    val l = mutableListOf<T>()
    while(true){
        if(isEmpty()) error("reached end of content while removing")
        if(predicate(first)) l += removeFirst()
        else return l
    }
}

private fun <T> T.assert(equalTo: T? = null, notEqualTo: T? = null) {
    if(equalTo != null && equalTo != this) error("parse error. Expecting $equalTo, got $this")
    if(notEqualTo != null && notEqualTo == this) error("parse error. Expecting to not be equal to $notEqualTo, got $this")

}

private fun parseTestFile(lines: List<String>): List<Test> {
    val tests = mutableListOf<Test>()
    val left = ArrayDeque(lines.toMutableList())
    while(left.isNotEmpty()){
        val next = left.removeFirst()
        if(next.isBlank()) continue
        if(next.startsWith("//")) continue
        if(next.startsWith(">")) {
            val testName = next.substring(1).trim()
            left.removeWhile { it.isBlank() }
            left.remove().trimEnd().assert(equalTo = "--")
            // code
            val code = left.removeWhile { it.trimEnd() != "--" }
            left.remove().trimEnd().assert(equalTo = "--")
            val behavior = left.removeWhile { !it.startsWith(">") }
                    .filter { !it.startsWith("//") }
                    .filter { it.isNotBlank() }
            var outputNum: Int = 0
            var compilerError: String? = null
            var stdout: String? = null
            for(b in behavior) {
                val content = b.substring(4).split("//")[0]
                when(b.substring(0, 4).toLowerCase()) {
                    "num>" -> outputNum = content.toInt()
                    "err>" -> compilerError = content
                    "out>" -> stdout = content
                }
            }
            tests += Test(
                    name = testName,
                    code = code.joinToString("\n","\n","\n"),
                    outputNum = outputNum,
                    compilerError = compilerError,
                    stdout = stdout
            )
        } else {
            error("unknown line \"$next\"")
        }
    }
    return tests
}


abstract class ProperTest(f: String): StringSpec() {
    init {
        val file = File(f)
        "test file should exist" {
            println(file.absolutePath)
            file.exists() shouldBe true
        }
        val tests = parseTestFile(file.readLines(Charsets.UTF_8))
        for(test in tests) {
            test.name {
                // run
                val compiled =
            }
        }
    }
}

class SubTest: ProperTest("main.xenon.tests")


