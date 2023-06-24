package ru.yole.etymograph

typealias WordAlternatives = List<Word>

class ParadigmCell(val ruleAlternatives: List<Rule?>) {
    fun generate(word: Word, graph: GraphRepository): WordAlternatives {
        return ruleAlternatives.map { r ->
            val link = graph.getLinksTo(word).find { it.rules == listOf(r) }
            link?.fromEntity as? Word ?: r?.apply(word, graph) ?: word
        }.ifEmpty { listOf(word) }
    }
}

data class ParadigmColumn(val title: String) {
    val cells = mutableListOf<ParadigmCell?>()

    fun setRule(row: Int, rules: List<Rule?>) {
        while (cells.size <= row) {
            cells.add(null)
        }
        cells[row] = ParadigmCell(rules)
    }

    fun generate(word: Word, graph: GraphRepository): List<WordAlternatives?> {
        return cells.map {
            it?.generate(word, graph)
        }
    }

    fun removeRule(rule: Rule) {
        for (index in cells.indices) {
            val cell = cells[index] ?: continue
            if (cell.ruleAlternatives.size == 1 && cell.ruleAlternatives[0] == rule) {
                cells[index] = null
            }
            else {
                cells[index] = ParadigmCell(cell.ruleAlternatives - rule)
            }
        }
    }
}

data class Paradigm(
    val id: Int,
    val name: String,
    val language: Language,
    var pos: String
) {
    val rowTitles = mutableListOf<String>()
    val columns = mutableListOf<ParadigmColumn>()

    fun addRow(title: String) {
        rowTitles.add(title)
    }

    fun addColumn(title: String) {
        columns.add(ParadigmColumn(title))
    }

    fun setRule(row: Int, column: Int, rules: List<Rule?>) {
        if (row >= rowTitles.size || column >= columns.size) {
            throw IllegalArgumentException("Invalid row or column index")
        }

        columns[column].setRule(row, rules)
    }

    fun removeRule(rule: Rule) {
        for (column in columns) {
            column.removeRule(rule)
        }
    }

    fun collectAllRules(): Set<Rule> {
        return columns
            .flatMap { col -> col.cells.flatMap {
                cell -> cell?.ruleAlternatives ?: emptyList() }
            }
            .filterNotNull()
            .toSet()
    }

    fun generate(word: Word, graph: GraphRepository): List<List<WordAlternatives?>> {
        return columns.map {
            it.generate(word, graph)
        }
    }

    fun parse(text: String, ruleLookup: (String) -> Rule?) {
        rowTitles.clear()
        columns.clear()

        val lines = text.split('\n').filter { it.isNotEmpty() }
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
                val alternatives = w.split('|')
                val rules = alternatives.map { alt ->
                    if (alt == ".") {
                        null
                    }
                    else {
                        ruleLookup(alt) ?: throw ParadigmParseException("Can't find rule $alt")
                    }
                }
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
                    val alternatives = col.cells.getOrNull(index)?.ruleAlternatives
                    alternatives?.joinToString("|") { it?.name ?: "." }
                        ?.ifEmpty { "." } ?: "-"
                })
            }
        }.trimEnd('\n')
    }
}

class ParadigmParseException(message: String) : Exception(message)