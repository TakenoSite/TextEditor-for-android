package com.example.helloworld

internal object LogicalLines {
    fun starts(text: CharSequence): IntArray {
        val result = ArrayList<Int>()
        result.add(0)
        text.forEachIndexed { index, char -> if (char == '\n') result.add(index + 1) }
        return result.toIntArray()
    }
}
