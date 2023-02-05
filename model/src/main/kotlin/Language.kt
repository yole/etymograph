package ru.yole.etymograph

class Language(val name: String, val shortName: String) {
    var digraphs: List<String> = emptyList()
    var phonemeClasses = mutableListOf<PhonemeClass>()

    fun characterClassByName(name: String) = phonemeClasses.find { it.name == name }
}
