package dev.mee42.parser

import dev.mee42.CompilerException
import dev.mee42.lexer.Token
import dev.mee42.lexer.TokenType
import java.util.*


class TokenQueue(private val queue: Deque<Token>) {
    fun peek(): Token {
        cleanNewlines()
        return queue.peekFirst() ?: throw CompilerException("reached end of file while parsing")
    }
    private fun cleanNewlines() {
        while(queue.isNotEmpty() && queue.first().type == TokenType.NEWLINE) {
            queue.removeFirst()
        }
    }
    fun peekWithNewline(): Token = queue.peek()
    fun remove(): Token {
        cleanNewlines()
        return queue.remove()
    }
    fun removeWhile( includeNewlines: Boolean = false, conditional: (Token) -> Boolean): List<Token> {
        val list = mutableListOf<Token>()
        val newlines = mutableListOf<Token>()
        while (true) {
            if(queue.peekFirst().type == TokenType.NEWLINE && !includeNewlines)  { newlines.add(queue.removeFirst()); continue }
            if (conditional(queue.peek()) || (queue.peek().type == TokenType.NEWLINE && includeNewlines)) {
                list.add(queue.remove())
                newlines.clear()
            } else {
                for(newline in newlines) queue.addFirst(newline)
                return list
            }
        }
    }
    fun isNotEmpty(): Boolean {
//        cleanNewlines()
        return queue.isNotEmpty() && queue.any { it.type != TokenType.NEWLINE }
    }
    fun shove(token: Token) {
        queue.addFirst(token)
    }
}
