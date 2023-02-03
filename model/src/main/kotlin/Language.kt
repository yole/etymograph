package ru.yole.etymograph

class Language(val name: String, val shortName: String) {
    var digraphs: List<String> = emptyList()
    var characterClasses = mutableListOf<CharacterClass>()

    fun characterClassByName(name: String) = characterClasses.find { it.name == name }
}

val UnknownLanguage = Language("?", "?")
