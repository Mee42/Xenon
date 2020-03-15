package dev.mee42.xpp

data class LabeledLine(val content: String, val index: Int)

fun preprocess(fileContents: String): List<LabeledLine> {
    return fileContents.split("\n")
        .map { str -> str.substring(0, str.indexOf("//").takeUnless { it == -1 } ?: str.length) }
        .mapIndexed { index, s -> LabeledLine(s, index) }
}