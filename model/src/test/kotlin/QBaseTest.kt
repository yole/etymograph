package ru.yole.etymograph

open class QBaseTest {
    val q = Language("Quenya", "Q").also {
        it.phonemes = listOf(
            Phoneme(-1,listOf("e", "ë"), null, setOf("vowel"))
        ) + listOf("a", "o", "u", "i").map { p ->
            Phoneme(-1, listOf(p), null, setOf("vowel"))
        } + listOf("á", "ó", "ú", "í", "é").map { p ->
            Phoneme(-1, listOf(p), null, setOf("long", "vowel"))
        } + listOf("c", "f", "h", "j", "l", "m", "q", "r", "s", "v", "w", "x", "z").map { p ->
            Phoneme(-1, listOf(p), null, setOf("consonant"))
        } + phoneme("p", "voiceless bilabial stop consonant") +
            phoneme("t", "voiceless alveolar stop consonant") +
            phoneme("b", "voiced bilabial stop consonant") +
            phoneme("d", "voiced alveolar stop consonant") +
            phoneme("n", "nasal consonant") +
            phoneme("k", "voiceless velar stop consonant") +
            phoneme("g", "voiced velar stop consonant")

        it.diphthongs = listOf("ai", "oi", "ui", "au", "eu", "iu")
    }
    val v = q.phonemeClassByName("vowel")!!

    val ce = Language("Common Eldarin", "CE").also {
        it.phonemes = listOf(
            Phoneme(-1, listOf("kh"), null, setOf("voiceless", "consonant")),
            Phoneme(-1, listOf("th"), null, setOf("voiceless", "consonant"))
        )
    }
    val emptyRepo = InMemoryGraphRepository()

    fun GraphRepository.addWord(text: String, gloss: String? = text, pos: String? = null, language: Language = q) =
        findOrAddWord(text, language, gloss, pos = pos)

    fun repoWithQ() = InMemoryGraphRepository().apply {
        addLanguage(q)
    }

    fun phoneme(grapheme: String, classes: String): Phoneme {
        return Phoneme(-1, listOf(grapheme), null, classes.split(' ').toSet())
    }

    fun phoneme(graphemes: List<String>, sound: String?, classes: String): Phoneme {
        return Phoneme(-1, graphemes, sound, classes.split(' ').toSet())
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
