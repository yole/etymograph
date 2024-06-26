package ru.yole.etymograph.importers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.yole.etymograph.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path

fun loadWiktionaryPage(title: String): String? {
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder()
        .header("User-Agent", "https://github.com/yole/etymograph")
        .uri(URI.create("https://en.wiktionary.org/w/rest.php/v1/page/$title"))
        .build()
    val response = client.send(request, BodyHandlers.ofString())
    if (response.statusCode() == 404) return null
    return response.body()
}

@Serializable
data class WiktionaryPageData(val source: String)

val wiktionaryPosNames = setOf("Noun", "Adjective", "Verb", "Article", "Determiner", "Pronoun", "Adverb", "Conjunction")

class WiktionaryPosSection(val pos: String) {
    val senses = mutableListOf<String>()
    val classes = mutableListOf<String>()

    fun parse(lines: LineBuffer, dictionarySettings: Map<String, List<String>>) {
        var subsection: String? = null
        while (true) {
            val line = lines.peek() ?: return
            if (line.startsWith("==") && !line.startsWith("====")) {
                return
            }
            lines.next()

            for ((key, value) in dictionarySettings) {
                if (key in line) {
                    classes.addAll(value)
                }
            }

            if (line.startsWith("====")) {
                subsection = line.trimStart('=').trimEnd('=')
            }
            else if (subsection == null) {
                parseSenseLine(line)
            }
        }
    }

    private fun parseSenseLine(line: String) {
        if (line.startsWith("# ")) {
            senses.add(line.removePrefix("# ").replace("[[", "").replace("]]", ""))
        }
    }
}

class WiktionaryPage(source: String) {
    val lines = LineBuffer(source)
    val posSections = mutableListOf<WiktionaryPosSection>()

    private fun seekToLanguage(language: String): Boolean {
        while (true) {
            val line = lines.next() ?: return false
            if (line == "==$language==") {
                return true
            }
        }
    }

    fun parse(language: String, dictionarySettings: Map<String, List<String>>): Boolean {
        if (!seekToLanguage(language)) return false
        var currentSection: String? = null
        while (true) {
            val line = lines.next() ?: break
            if (line.startsWith("===")) {
                currentSection = line.trimStart('=').trimEnd('=')
                if (currentSection in wiktionaryPosNames) {
                    posSections.add(WiktionaryPosSection(currentSection).apply { parse(lines, dictionarySettings) })
                }
            }
            else if (line.startsWith("==")) {
                break
            }
        }
        return true
    }
}

class Wiktionary : Dictionary {
    private fun parseDictionarySettings(settings: String?): Map<String, List<String>> {
        if (settings == null) return emptyMap()
        return settings
            .split('\n')
            .mapNotNull { it.trim().takeIf { line -> line.isNotEmpty() } }
            .mapNotNull { parseDictionarySettingsLine(it) }
            .toMap()
    }

    private fun parseDictionarySettingsLine(line: String): Pair<String, List<String>>? {
        val keyValue = line.split(':')
        if (keyValue.size != 2) return null
        return keyValue[0].trim() to keyValue[1].split(',').map { it.trim() }
    }

    override fun lookup(language: Language, word: String): List<Word> {
        val normalizedWord = word.removeDiacritics()
        val pageJson = loadWiktionaryPage(normalizedWord) ?: return emptyList()
        val pageData = json.decodeFromString<WiktionaryPageData>(pageJson)
        val wiktionaryPage = WiktionaryPage(pageData.source)
        if (!wiktionaryPage.parse(language.name, parseDictionarySettings(language.dictionarySettings))) {
            return emptyList()
        }
        return wiktionaryPage.posSections.map { section ->
            Word(-1, word, language, section.senses.first(), section.senses.joinToString("; "),
                pos = language.pos.find { it.name == section.pos }?.abbreviation,
                classes = section.classes,
                source = listOf(SourceRef(null, "https://en.wiktionary.org/wiki/$normalizedWord#${language.name.replace(' ', '_')}")))
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

fun main() {
    val ieRepo = JsonGraphRepository.fromJson(Path.of("data/ie"))
    val oe = ieRepo.languageByShortName("OE")!!
    val wiktionary = Wiktionary()
    val result = wiktionary.lookup(oe, "wer")
    for (word in result) {
        println(word.fullGloss)
        println(word.pos)
        println(word.classes)
    }
}
