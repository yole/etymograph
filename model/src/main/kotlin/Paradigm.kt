package ru.yole.etymograph

typealias RuleSeq = List<Rule>

data class ParadigmRuleSeq(val rules: RuleSeq) {
    fun generate(word: Word, graph: GraphRepository): Word {
        return rules.fold(word) { w, r ->
            val link = graph.getLinksTo(word).find { it.rules == listOf(r) }
            link?.fromWord ?: r.apply(w)
        }
    }
}

typealias WordAlternatives = List<Word>

class ParadigmCell(ruleAlternatives: List<RuleSeq>) {
    val alternatives = ruleAlternatives.map { ParadigmRuleSeq(it) }

    fun generate(word: Word, graph: GraphRepository): WordAlternatives {
        return alternatives.map { it.generate(word, graph) }
    }
}

data class ParadigmColumn(val title: String) {
    val cells = mutableListOf<ParadigmCell?>()

    fun setRule(row: Int, rules: List<RuleSeq>) {
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

    fun setRule(row: Int, column: Int, rules: List<RuleSeq>) {
        if (row >= rowTitles.size || column >= columns.size) {
            throw IllegalArgumentException("Invalid row or column index")
        }

        columns[column].setRule(row, rules)
    }

    fun generate(word: Word, graph: GraphRepository): List<List<WordAlternatives?>> {
        return columns.map {
            it.generate(word, graph)
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
                val rules = if (w == ".") {
                    emptyList()
                }
                else {
                    val alternatives = w.split('|')
                    alternatives.map { alt ->
                        val ruleNames = alt.split(',')
                        ruleNames.map { ruleLookup(it) ?: throw ParadigmParseException("Can't find rule $it") }
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
                    val alternatives = col.cells.getOrNull(index)?.alternatives
                    alternatives?.joinToString("|") { seq ->
                        seq.rules.joinToString(",") { it.name }
                    }?.ifEmpty { "." } ?: "-"
                })
            }
        }.trimEnd('\n')
    }
}

class ParadigmParseException(message: String) : Exception(message)