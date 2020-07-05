package ru.yole.etymograph

class Link(
    val fromWord: Word,
    val toWord: Word,
    val type: String,
    source: String?,
    notes: String?
) : LangEntity(source, notes) {
}
