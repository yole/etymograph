package ru.yole.etymograph

open class QBaseTest {
    val q = Language("Quenya", "Q")
    val ce = Language("Common Eldarin", "CE").also {
        it.digraphs = listOf("kh", "th")
    }
    val v = PhonemeClass("vowel", listOf("a", "o", "u", "i", "e")).also { q.phonemeClasses.add(it) }
}
