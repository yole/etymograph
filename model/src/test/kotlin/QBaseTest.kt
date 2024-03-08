package ru.yole.etymograph

open class QBaseTest {
    val q = Language("Quenya", "Q").also {
        it.phonemes = listOf(
            Phoneme(listOf("e", "ë"), setOf("vowel"))
        ) + listOf("a", "o", "u", "i").map { p ->
            Phoneme(listOf(p),setOf("vowel"))
        } + listOf("á", "ó", "ú", "í", "é").map { p ->
            Phoneme(listOf(p), setOf("long", "vowel"))
        } + listOf("c", "f", "g", "h", "j", "k", "l", "m", "q", "r", "s", "v", "w", "x", "z").map { p ->
            Phoneme(listOf(p), setOf("consonant"))
        } + phoneme("p", "voiceless bilabial stop consonant") +
            phoneme("t", "voiceless alveolar stop consonant") +
            phoneme("b", "voiced bilabial stop consonant") +
            phoneme("d", "voiced alveolar stop consonant") +
            phoneme("n", "nasal consonant")

        it.diphthongs = listOf("ai", "oi", "ui", "au", "eu", "iu")
    }
    val v = q.phonemeClassByName("vowel")!!

    val ce = Language("Common Eldarin", "CE").also {
        it.phonemes = listOf(
            Phoneme(listOf("kh"), setOf("voiceless", "consonant")),
            Phoneme(listOf("th"), setOf("voiceless", "consonant"))
        )
    }
    val emptyRepo = InMemoryGraphRepository()

    fun GraphRepository.addWord(text: String, gloss: String? = text, pos: String? = null, language: Language = q) =
        findOrAddWord(text, language, gloss, pos = pos)

    fun repoWithQ() = InMemoryGraphRepository().apply {
        addLanguage(q)
    }

    fun phoneme(sound: String, classes: String): Phoneme {
        return Phoneme(listOf(sound), classes.split(' ').toSet())
    }

    fun GraphRepository.rule(
        text: String,
        fromLanguage: Language = q,
        toLanguage: Language = q,
        name: String = "q", addedCategories: String? = null
    ): Rule {
        return addRule(name, fromLanguage, toLanguage,
            Rule.parseBranches(text, createParseContext(fromLanguage, toLanguage, this)),
            addedCategories = addedCategories
        )
    }
}
