package ru.yole.etymograph

import java.util.*

class PhonemeClass(val name: String, val matchingPhonemes: List<String>)

class Language(val name: String, val shortName: String) {
    var digraphs: List<String> = emptyList()
    var diphthongs: List<String> = emptyList()
    var phonemeClasses = mutableListOf<PhonemeClass>()
    var letterNormalization = mapOf<String, String>()

    fun phonemeClassByName(name: String) = phonemeClasses.find { it.name == name }

    fun normalizeWord(text: String): String {
        return letterNormalization.entries.fold(text.lowercase(Locale.FRANCE)) { s, entry ->
            s.replace(entry.key, entry.value)
        }
    }
}
