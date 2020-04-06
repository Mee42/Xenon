package dev.mee42.xpp

import java.io.File

data class LabeledLine(val content: String, val line: Int, val file: String)

fun preprocess(fileContents: String, filename: String): List<LabeledLine> {
    return fileContents.split("\n")
            .map { str -> str.substring(0, str.indexOf("//").takeUnless { it == -1 } ?: str.length) }
            .mapIndexed { index, s -> LabeledLine(s, index, filename) }
            .flatMap { x ->
                if(x.content.startsWith("#")) {
                    // macro time!
                    val (name, argument) = x.content.split(Regex("""\s+"""), limit = 2)
                    when(name) {
                        "#" -> error("macro on line ${x.line} name must have a definition")
                        "#lib" -> {
                            val file = File(argument)
                            preprocess(file.readText(Charsets.UTF_8), argument)
                        }
                        else -> error("macro $name not supported yet")
                    }
                } else listOf(x)
            }
}