package ru.yole.etymograph

data class WordAlternative(val word: Word, val rule: Rule?)

typealias WordAlternatives = List<WordAlternative>

class ParadigmCell(val ruleAlternatives: List<Rule?>) {
    fun generate(word: Word, graph: GraphRepository): WordAlternatives {
        return ruleAlternatives.mapTo(mutableSetOf()) { r ->
            val link = graph.getLinksTo(word).find { it.rules == listOf(r) }
            val resultWord = link?.fromEntity as? Word ?: r?.apply(word, graph) ?: word
            WordAlternative(resultWord, r)
        }.toList().ifEmpty { listOf(WordAlternative(word, null)) }
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
            val lineTrimmed = line.trim()
            val columnValues: List<String>
            if (lineTrimmed.startsWith('"')) {
                val endQuote = lineTrimmed.indexOf('"', 1)
                if (endQuote < 0) {
                    throw ParadigmParseException("Unclosed quote in row name")
                }
                addRow(lineTrimmed.substring(1, endQuote))
                columnValues = lineTrimmed.substring(endQuote + 1).trim().split(' ')
            }
            else {
                val words = line.trim().split(' ')
                addRow(words[0])
                columnValues = words.drop(1)
            }
            for ((colIndex, w) in columnValues.withIndex()) {
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
                if (' ' in rowTitle) {
                    append("\"$rowTitle\"")
                }
                else {
                    append(rowTitle)
                }
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