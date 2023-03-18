package ru.yole.etymograph

open class QBaseTest {
    val q = Language("Quenya", "Q").also {
        it.diphthongs = listOf("ai", "oi", "ui", "au", "eu", "iu")
    }

    val ce = Language("Common Eldarin", "CE").also {
        it.digraphs = listOf("kh", "th")
    }
    val v = PhonemeClass("vowel", listOf("a", "o", "u", "i", "e", "á", "ó", "ú", "í", "é")).also { q.phonemeClasses.add(it) }
    val lv = PhonemeClass("long vowel", listOf("á", "ó", "ú", "í", "é")).also { q.phonemeClasses.add(it) }

    val emptyRepo = InMemoryGraphRepository()
}
