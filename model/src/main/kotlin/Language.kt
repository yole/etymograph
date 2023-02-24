package ru.yole.etymograph

import java.util.*

class PhonemeClass(val name: String, val matchingPhonemes: List<String>)

class Language(val name: String, val shortName: String) {
    var digraphs: List<String> = emptyList()
    var phonemeClasses = mutableListOf<PhonemeClass>()
    var letterNormalization = mapOf<String, String>()

    fun phonemeClassByName(name: String) = phonemeClasses.find { it.name == name }

    fun normalizeWord(text: String): String {
        return text.lowercase(Locale.FRANCE)
            .replace('ä', 'a')
            .replace('ö', 'o')
            .replace('ü', 'u')
            .replace('ï', 'i')
            .replace('ë', 'e')
    }
}
