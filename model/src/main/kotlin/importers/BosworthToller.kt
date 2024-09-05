package ru.yole.etymograph.importers

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import org.jdom2.Element
import org.jdom2.Text
import org.jdom2.input.SAXBuilder
import ru.yole.etymograph.*
import ru.yole.etymograph.Dictionary
import java.io.FileReader
import java.io.StringReader
import java.nio.file.Path
import java.util.*

val posMap = mapOf(
    "noun" to "N",
    "verb" to "V",
    "adjective" to "ADJ",
    "adverb" to "ADV",
    "conjunction" to "CONJ",
    "preposition" to "PREP",
    "interjection" to "INTJ",
    "pronoun" to "PRON"
)

val genderMap = mapOf(
    "masculine" to "masc",
    "feminine" to "fem",
    "neuter" to "neut"
)

class BosworthTollerEntry(
    val id: Int,
    val form: String,
    val searchForm: String,
    val formVariants: List<String>?,
    val pos: List<String>,
    val gender: List<String>?,
    val subclass: List<String>?,
    val def: List<String>
) {
    override fun toString(): String {
        return "ID=$id form=$form POS=$pos def=$def"
    }

    companion object {
        private val defTagsToIgnore = setOf("i-s", "i-e", "b-s", "b-e", "page", "references", "problem", "ex", "etym", "examples", "grammar")
        private val defTagsToInline = setOf("greek", "hebrew", "rune", "a", "see", "def", "snum", "sense", "trans", "edit", "thornbar")

        fun parse(body: String, errorConsumer: (String) -> Unit): BosworthTollerEntry? {
            val document = SAXBuilder().build(StringReader(body))
            val root = document.rootElement
            val id = root.getAttributeValue("id").toInt()
            if (root.getAttributeValue("add") == "1") {
                errorConsumer("Skipping added entry with ID $id")
                return null
            }
            val formElement = root.getChild("form")
            val orthForm = formElement.getChildText("orth")
            val searchForm = formElement.getChildText("search")

            val gramGrp = root.getChild("gramGrp")
            val pos = mutableListOf<String>()
            val gender = mutableListOf<String>()
            val subclass = mutableListOf<String>()
            for (child in gramGrp.children) {
                when (child.name) {
                    "pos" -> pos.add(child.text)
                    "gen" -> gender.add(child.text)
                    "subc" -> subclass.add(child.text)
                    else -> {
                        errorConsumer("Unknown grammar tag ${child.name}")
                    }
                }
            }

            if (pos.isEmpty()) {
                errorConsumer("Skipping entry with no part of speech: $orthForm")
                return null
            }

            val bodyColumn = root.getChildren("column").singleOrNull { it.getAttributeValue("name") == "body" }
            if (bodyColumn == null) {
                errorConsumer("Skipping entry without body column $id")
                return null
            }

            val defs: List<String>
            val defElement = bodyColumn.getChild("def")
            if (defElement != null) {
                val def = parseAnyDefinition(defElement, errorConsumer)
                    ?: run {
                        errorConsumer("Cannot parse definition for word $orthForm")
                        return null
                    }
                defs = listOf(def)
            }
            else {
                val senses = bodyColumn.getChildren("sense")
                if (senses.isNotEmpty()) {
                    defs = senses.mapNotNull { parseSense(it, errorConsumer) }
                }
                else {
                    // TODO sometimes the definition is erroneously included in the <grammar> tag
                    errorConsumer("No definition or senses for word $orthForm")
                    return null
                }
            }

            val grammarElement = bodyColumn.getChild("grammar") ?: run {
                errorConsumer("no grammar for word $orthForm")
                return null
            }

            val (inflections, variants) = parseGrammar(grammarElement, orthForm, pos, errorConsumer)

            return BosworthTollerEntry(id, orthForm, searchForm, variants.takeIf { it.isNotEmpty() },
                pos, gender.takeIf { it.isNotEmpty() }, subclass.takeIf { it.isNotEmpty() }, defs)
        }

        private fun parseSense(senseElement: Element, errorConsumer: (String) -> Unit): String? =
            senseElement.getChild("def")?.let { parseAnyDefinition(it, errorConsumer) }
                ?: parseAnyDefinition(senseElement, errorConsumer)

        private fun parseAnyDefinition(defElement: Element, errorConsumer: (String) -> Unit): String? =
            parseDefinition(defElement, "eng", errorConsumer)
                ?: parseDefinition(defElement, null, errorConsumer)

        private fun parseDefinition(defElement: Element, language: String?, errorConsumer: (String) -> Unit): String? {
            val result = StringBuilder()
            for (child in defElement.content) {
                if (child is Element) {
                    if (child.name in defTagsToIgnore) continue
                    if (child.name == "equiv") {
                        if (child.getAttributeValue("lang")?.lowercase() == language || language == null) {
                            result.append(parseDefinition(child, language, errorConsumer))
                        }
                    }
                    else if (child.name in defTagsToInline) {
                        result.append(parseDefinition(child, language, errorConsumer))
                    }
                    else {
                        errorConsumer("Unknown tag ${child.name}")
                    }
                }
                else if (child is Text && child.text.isNotBlank()) {
                    result.append(child.text)
                }
            }
            return if (result.isEmpty()) null else result.toString().trimEnd(';', ',', ' ', '.')
        }

        data class GrammarParseResult(val inflections: Map<String, List<String>>, val variants: List<String>)

        private fun parseGrammar(grammarElement: Element, form: String, pos: List<String>, errorConsumer: (String) -> Unit): GrammarParseResult {
            val inflections = mutableMapOf<String, List<String>>()
            val variants = mutableListOf<String>()
            var lastText: String? = null
            for (child in grammarElement.content) {
                if (child is Element) {
                    if (child.name == "infl") {
                        val full = child.getAttributeValue("full")
                        val func = child.getAttributeValue("func")
                        val variantElements = child.getChildren("var")
                        if (variantElements.isNotEmpty()) {
                            val variantFullTexts = variantElements.map { it.getAttributeValue("full") }
                                .takeIf { it.all { v -> v != null } }
                                ?: variantElements.map { it.text }
                            if (func == null && lastText == null) {
                                errorConsumer("Don't know where to attach variants")
                            }
                            else {
                                inflections[func ?: lastText!!] = variantFullTexts
                            }
                        }
                        else if (func != null) {
                            inflections[func] = listOf(full ?: child.text)
                        }
                        else {
                            if (lastText != null) {
                                inflections[lastText] = listOf(full ?: child.text)
                            }
                            else if ("noun" in pos) {
                                inflections["g."] = listOf(child.text)
                            }
                            else {
                                errorConsumer("Don't know where to attach inflection text")
                            }
                        }
                    }
                    else if (child.name in defTagsToIgnore || child.name == "a") {
                        continue
                    }
                    else if (child.name == "var") {
                        if (lastText == null) {
                            var t = child.text
                            if (t.startsWith("-")) {
                                val i = form.indexOf('-')
                                if (i >= 0) {
                                    variants.add(form.substring(0, i) + t)
                                }
                            }
                            else {
                                variants.add(t)
                            }
                        }
                        else {
                            // TODO
                        }
                    }
                    else {
                        errorConsumer("Unknown <grammar> child tag ${child.name}")
                    }
                }
                else if (child is Text) {
                    val trimmedText = child.text.trimStart(',', ' ').trimEnd(' ', ',')
                    if (trimmedText.isNotEmpty()) {
                        lastText = trimmedText
                    }
                }
            }

            return GrammarParseResult(inflections, variants)
        }
    }
}

// Works with Bosworth-Toller CSV export from https://lindat.cz/repository/xmlui/handle/11234/1-3532
class BosworthToller(dataPath: String) : Dictionary {
    private val entries = mutableMapOf<String, MutableList<BosworthTollerEntry>>()
    private val entriesBySearchForm = mutableMapOf<String, MutableList<BosworthTollerEntry>>()
    private val entriesByVariant = mutableMapOf<String, MutableList<BosworthTollerEntry>>()
    private val entryById = mutableMapOf<Int, BosworthTollerEntry>()
    private val parseErrors = mutableMapOf<Int, String>()

    init {
        val reader = CSVReaderBuilder(FileReader(dataPath))
            .withCSVParser(
                CSVParserBuilder()
                    .withSeparator(';')
                    .build()
            )
            .build()
        reader.readNext()  // skip header line

        while (true) {
            val (id, headword, body) = reader.readNext() ?: break
            val entry = BosworthTollerEntry.parse(body) { parseErrors[id.toInt()] = it } ?: continue
            val normalizedForm = normalizeText(entry.form)
            val entryList = entries.getOrPut(normalizedForm) { mutableListOf() }
            entryList.add(entry)
            val searchEntryList = entriesBySearchForm.getOrPut(entry.searchForm) { mutableListOf() }
            searchEntryList.add(entry)
            entry.formVariants?.let {
                for (variant in it) {
                    val entryListByVariant = entriesByVariant.getOrPut(normalizeText(variant)) { mutableListOf() }
                    entryListByVariant.add(entry)
                }
            }
            entryById[id.toInt()] = entry
        }
    }

    private fun normalizeText(form: String): String =
        form
            .lowercase()
            .replace("eó", "ēo")
            .replace("eá", "ēa")
            .replace("ié", "īe")
            .replace("á", "ā")
            .replace("ó", "ō")
            .replace("ú", "ū")
            .replace("é", "ē")
            .replace("í", "ī")
            .replace("ý", "ȳ")
            .replace("ǽ", "ǣ")
            .replace("ð", "þ")
            .replace("-", "")

    override fun lookup(language: Language, word: String): List<DictionaryWord> {
        val lookupText = word
            .replace("ġ", "g")
            .replace("ċ", "c")
            .replace("ð", "þ")
            .replace("-", "")
        val entryList = entries[lookupText] ?: entriesByVariant[lookupText] ?: entriesBySearchForm[lookupText]
        return entryList?.map { entryToWord(it, language) } ?: emptyList()
    }

    private fun entryToWord(entry: BosworthTollerEntry, language: Language): DictionaryWord {
        val pos = posMap[entry.pos.first()]
        if (pos == null) {
            println("Unknown part of speech ${entry.pos.first()}")
        }
        val classes = mutableListOf<String>()
        val genderClass = entry.gender?.singleOrNull()?.let { genderMap[it] }
        if (genderClass != null) {
            classes.add(genderClass)
        }
        val gloss = entry.def.first().replaceFirstChar { it.lowercase(Locale.getDefault()) }
        return DictionaryWord(language, extractShortGloss(gloss), fullGloss = gloss, pos = pos, classes = classes,
            source = "https://bosworthtoller.com/${entry.id}")
    }

    private fun extractShortGloss(gloss: String): String {
        val words = gloss.split(' ').filter { it.isNotBlank() }
        val uppercaseWord = words.find { it.all { c -> c.isUpperCase() } }
        if (uppercaseWord != null) {
            return uppercaseWord.lowercase()
        }
        if (';' in gloss) {
            val beforeSemi = gloss.split(';').first()
            if (beforeSemi.isNotBlank()) {
                if (',' in beforeSemi) {
                    val firstWord = beforeSemi.split(',').first()
                    if (firstWord.isNotBlank()) {
                        return firstWord
                    }
                }
                return beforeSemi
            }
        }
        val firstWord = gloss.split(',').first()
        return firstWord.ifBlank { gloss }
    }
}

fun main() {
    val bosworthToller = BosworthToller("dictionaries/bosworth_entries_export.csv")

    val ieRepo = JsonGraphRepository.fromJson(Path.of("data/ie"))
    val oe = ieRepo.languageByShortName("OE")!!
    augmentWithDictionary(ieRepo, oe, bosworthToller)
    ieRepo.save()
}
