package ru.yole.etymograph

data class WordAlternative(val word: Word, val expectedWord: Word, val rule: Rule?)

typealias WordAlternatives = List<WordAlternative>

class ParadigmCell(val ruleAlternatives: List<Rule?>) {
    fun generate(word: Word, graph: GraphRepository): WordAlternatives {
        return ruleAlternatives.mapTo(mutableSetOf()) { r ->
            val link = graph.getLinksTo(word).find { it.rules == listOf(r) }
            val expectedWord = r?.apply(word, graph) ?: word
            val resultWord = link?.fromEntity as? Word ?: expectedWord
            WordAlternative(resultWord, expectedWord, r)
        }.toList().ifEmpty { listOf(WordAlternative(word, word, null)) }
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
    var name: String,
    val language: Language,
    var pos: List<String>,
    var preRule: Rule? = null,
    var postRule: Rule? = null
) {
    val rowTitles = mutableListOf<String>()
    val columns = mutableListOf<ParadigmColumn>()
    var allRules: Set<Rule> = emptySet()
        private set

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
        allRules = collectAllRules()
    }

    fun removeRule(rule: Rule) {
        for (column in columns) {
            column.removeRule(rule)
        }
    }

    private fun collectAllRules(): Set<Rule> {
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

fun generateParadigm(
    repo: GraphRepository,
    language: Language,
    name: String,
    pos: List<String>,
    rows: List<String>,
    columns: List<String>,
    prefix: String,
    addedCategories: String,
    endings: List<String>
): Paradigm {

    fun crossProduct(c: List<List<String>>): List<String> {
        // TODO
        return c[0]
    }

    fun mapToGrammaticalCategories(list: List<String>): List<String> {
        val abbreviations = list
            .map { c ->
                language.grammaticalCategories.find { it.name == c }
                    ?: throw ParadigmParseException("No grammatical category $c")
            }
            .map { it.values.map { v -> v.abbreviation } }
        return crossProduct(abbreviations)
    }

    val rowList = mapToGrammaticalCategories(rows)
    val colList = mapToGrammaticalCategories(columns)

    val paradigm = repo.addParadigm(name, language, pos)
    for (rowTitle in rowList) {
        paradigm.addRow(rowTitle)
    }
    for (columnTitle in colList) {
        paradigm.addColumn(columnTitle)
    }

    val prefix = if (prefix.endsWith("-")) prefix else "$prefix-"

    for ((rowIndex, rowTitle) in rowList.withIndex()) {
        for ((colIndex, columnTitle) in colList.withIndex()) {
            val ruleNameSeparator = if (rowTitle.all { it.isDigit() }) "" else "-"
            val categorySeparator = if (rowTitle.all { it.isDigit() }) "" else "."
            val ruleName = "$prefix${rowTitle.lowercase()}$ruleNameSeparator${columnTitle.lowercase().replace(" ", "-")}"
            val addedCategories = addedCategories + "." + rowTitle.uppercase() + categorySeparator + columnTitle.uppercase().replace(" ", ".")
            val rule = repo.ruleByName(ruleName)
                ?: createParadigmRule(repo, name, ruleName, language, addedCategories, pos, endings.getOrNull(rowIndex + colIndex * rowList.size))

            paradigm.setRule(rowIndex, colIndex, listOf(rule))
        }
    }
    return paradigm
}

private fun createParadigmRule(
    repo: GraphRepository,
    name: String,
    ruleName: String,
    language: Language,
    addedCategories: String,
    pos: List<String>,
    ending: String?
): Rule {

    val logic = if (!ending.isNullOrBlank()) {
        val endingGloss = "$name ${addedCategories.removePrefix(".").lowercase()}. ending"
        repo.findOrAddWord(ending, language, endingGloss, pos = KnownPartsOfSpeech.affix.abbreviation)
        MorphoRuleLogic.parse("- append morpheme '$ending: $endingGloss'", RuleParseContext.of(repo, language, language))
    }
    else {
        MorphoRuleLogic.empty()
    }
    return repo.addRule(ruleName, language, language, logic, addedCategories, fromPOS = pos)
}


class ParadigmParseException(message: String) : Exception(message)