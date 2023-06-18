package ru.yole.etymograph

open class QBaseTest {
    val q = Language("Quenya", "Q").also {
        it.diphthongs = listOf("ai", "oi", "ui", "au", "eu", "iu")
        it.letterNormalization = mapOf("ë" to "e")
    }

    val ce = Language("Common Eldarin", "CE").also {
        it.digraphs = listOf("kh", "th")
    }
    val v = PhonemeClass("vowel", listOf("a", "o", "u", "i", "e", "á", "ó", "ú", "í", "é")).also { q.phonemeClasses.add(it) }
    val c = PhonemeClass("consonant", listOf("b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "q", "r", "s", "t", "v", "w", "x", "z"))
        .also { q.phonemeClasses.add(it) }
    val lv = PhonemeClass("long vowel", listOf("á", "ó", "ú", "í", "é")).also { q.phonemeClasses.add(it) }

    val emptyRepo = InMemoryGraphRepository()

    fun GraphRepository.addWord(text: String, gloss: String? = text) = findOrAddWord(text, q, gloss)
}
