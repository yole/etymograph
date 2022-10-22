package ru.yole.etymograph

open class Rule(
    val fromLanguage: Language,
    val toLanguage: Language,
    val fromPattern: String,
    val toPattern: String,
    val addedCategories: String?,
    source: String?,
    notes: String?
) : LangEntity(source, notes) {
    fun matches(word: Word): Boolean {
        return fromPattern.toRegexPattern().matches(word.text)
    }

    fun apply(word: String): String {
        return fromPattern.toRegexPattern().replace(word) { mr ->
            toPattern.replace("$1", mr.groupValues[1])
        }
    }

    private fun String.toRegexPattern(): Regex {
        return this
            .replace("*", ".+")
            .replace("V", "[aeiouáéíóúäëïöü]")
            .replace("C", "[bcdfghjklmnpqrstvwxz]").toRegex()
    }
}
