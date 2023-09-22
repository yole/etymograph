package ru.yole.etymograph

class SourceRef(val pubId: Int?, val refText: String)

open class LangEntity(val id: Int, var source: List<SourceRef>, var notes: String?) {
}
