package ru.yole.etymograph

class Language(val name: String, val shortName: String) {
    var digraphs: List<String> = emptyList()
}

val UnknownLanguage = Language("?", "?")
