package ru.yole.etymograph

class PhonemeClass(val name: String, val matchingPhonemes: List<String>)

class Language(val name: String, val shortName: String) {
    var digraphs: List<String> = emptyList()
    var phonemeClasses = mutableListOf<PhonemeClass>()

    fun characterClassByName(name: String) = phonemeClasses.find { it.name == name }
}
