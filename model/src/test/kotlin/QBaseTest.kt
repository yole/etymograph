package ru.yole.etymograph

open class QBaseTest {
    val q = Language("Quenya", "Q").also {
        it.phonemes = listOf(
            Phoneme(-1,listOf("e", "ë"), null, setOf("vowel"))
        ) + listOf("a", "o", "u", "i").map { p ->
            Phoneme(-1, listOf(p), null, setOf("short", "vowel"))
        } + listOf("á", "ó", "ú", "í", "é").map { p ->
            Phoneme(-1, listOf(p), null, setOf("long", "vowel"))
        } + listOf("c", "f", "h", "j", "l", "m", "q", "r", "s", "v", "w", "x", "z").map { p ->
            Phoneme(-1, listOf(p), null, setOf("consonant"))
        } + phoneme("p", "voiceless bilabial stop") +
            phoneme("t", "voiceless alveolar stop") +
            phoneme("b", "voiced bilabial stop") +
            phoneme("d", "voiced alveolar stop") +
            phoneme("n", "voiced alveolar nasal consonant") +
            phoneme("k", "voiceless velar stop") +
            phoneme("g", "voiced velar stop")

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

    fun GraphRepository.addWord(
        text: String,
        gloss: String? = text,
        pos: String? = null,
        classes: List<String> = emptyList(),
        language: Language = q
    ) = findOrAddWord(text, language, gloss, pos = pos, classes = classes)

    fun GraphRepository.with(language: Language) = apply { addLanguage(language) }

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
        name: String = "q", addedCategories: String? = null
    ): Rule {
        return addRule(name, q, q,
            Rule.parseBranches(text, createParseContext(q, q, this)),
            addedCategories = addedCategories
        )
    }

    fun applyRule(rule: Rule, word: Word): String {
        return rule.apply(word, emptyRepo).text
    }
}
