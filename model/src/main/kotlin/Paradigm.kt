package ru.yole.etymograph

data class ParadigmCell(val rules: List<Rule>) {
    fun generate(word: Word): String {
        return rules.fold(word) { w, r -> Word(-1, r.apply(w), word.language) }.text
    }
}

data class ParadigmColumn(val title: String) {
    val cells = mutableListOf<ParadigmCell?>()

    fun setRule(row: Int, rules: List<Rule>) {
        while (cells.size <= row) {
            cells.add(null)
        }
        cells[row] = ParadigmCell(rules)
    }

    fun generate(word: Word): List<String> {
        return cells.map {
            it?.generate(word) ?: word.text
        }
    }
}

data class Paradigm(
    val id: Int,
    val name: String,
    val language: Language,
    val pos: String
) {
    val rowTitles = mutableListOf<String>()
    val columns = mutableListOf<ParadigmColumn>()

    fun addRow(title: String) {
        rowTitles.add(title)
    }

    fun addColumn(title: String) {
        columns.add(ParadigmColumn(title))
    }

    fun setRule(row: Int, column: Int, rules: List<Rule>) {
        if (row >= rowTitles.size || column >= columns.size) {
            throw IllegalArgumentException("Invalid row or column index")
        }

        columns[column].setRule(row, rules)
    }

    fun generate(word: Word): List<List<String>> {
        return columns.map {
            it.generate(word)
        }
    }

    fun parse(text: String, ruleLookup: (String) -> Rule?) {
        rowTitles.clear()
        columns.clear()

        val lines = text.split('\n')
        val columnHeaders = lines[0].trim().split(' ')
        for (columnHeader in columnHeaders) {
            addColumn(columnHeader)
        }

        for ((rowIndex, line) in lines.drop(1).withIndex()) {
            val words = line.trim().split(' ')
            addRow(words[0])
            for ((colIndex, w) in words.drop(1).withIndex()) {
                if (w == "-") {
                    continue
                }
                val ruleNames = w.split(',')
                val rules = ruleNames.map { ruleLookup(it) ?: throw ParadigmParseException("Can't find rule $it") }
                setRule(rowIndex, colIndex, rules)
            }
        }
    }

    fun toEditableText(): String {
        return buildString {
            appendLine(columns.joinToString(" ") { it.title })
            for ((index, rowTitle) in rowTitles.withIndex()) {
                append(rowTitle)
                append(" ")
                appendLine(columns.joinToString(" ") { col ->
                    col.cells.getOrNull(index)?.rules?.joinToString(",") { it.name } ?: "-"
                })
            }
        }.trimEnd('\n')
    }
}

class ParadigmParseException(message: String) : Exception(message)