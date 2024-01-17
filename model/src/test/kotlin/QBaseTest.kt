package ru.yole.etymograph

open class QBaseTest {
    val q = Language("Quenya", "Q").also {
        it.phonemes = listOf(
            Phoneme(listOf("e", "ë"), listOf("vowel"))
        ) + listOf("a", "o", "u", "i").map { p ->
            Phoneme(listOf(p), listOf("vowel"))
        } + listOf("á", "ó", "ú", "í", "é").map { p ->
            Phoneme(listOf(p), listOf("long", "vowel"))
        } + listOf("b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "q", "r", "s", "t", "v", "w", "x", "z").map { p ->
            Phoneme(listOf(p), listOf("consonant"))
        }

        it.diphthongs = listOf("ai", "oi", "ui", "au", "eu", "iu")
    }
    val v = q.phonemeClassByName("vowel")!!

    val ce = Language("Common Eldarin", "CE").also {
        it.phonemes = listOf(
            Phoneme(listOf("kh"), listOf("voiceless", "consonant")),
            Phoneme(listOf("th"), listOf("voiceless", "consonant"))
        )
    }
    val emptyRepo = InMemoryGraphRepository()

    fun GraphRepository.addWord(text: String, gloss: String? = text, pos: String? = null) = findOrAddWord(text, q, gloss, pos = pos)

    fun repoWithQ() = InMemoryGraphRepository().apply {
        addLanguage(q)
    }
}
