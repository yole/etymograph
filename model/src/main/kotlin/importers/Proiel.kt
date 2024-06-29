package ru.yole.etymograph.importers

import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import ru.yole.etymograph.*
import java.io.File
import java.nio.file.Path

class ProielDiv(private val reader: ProielReader, private val element: Element) {
    val title: String?
        get() = element.getChild("title")?.text

    fun convertSentence(index: Int): LemmatizedText {
        val sentenceElement = element.getChildren("sentence")[index]
        val textBuilder = StringBuilder()
        val words = mutableListOf<LemmatizedWord>()
        var tokens = mutableListOf<LemmatizedToken>()
        var wordForm = ""
        for (token in sentenceElement.getChildren("token")) {
            val form = token.getAttributeValue("form") ?: continue
            wordForm += form
            val presentationAfter = token.getAttributeValue("presentation-after", "")
            textBuilder
                .append(form.replace(' ', '-'))
                .append(presentationAfter)
            val pos = reader.pos[token.getAttributeValue("part-of-speech")]!!
            val morphology = convertMorphology(token.getAttributeValue("morphology"))
            tokens.add(LemmatizedToken(form, token.getAttributeValue("lemma"), pos, morphology))
            if (presentationAfter.isNotEmpty()) {
                words.add(LemmatizedWord(wordForm, tokens))
                wordForm = ""
                tokens = mutableListOf()
            }
        }
        return LemmatizedText(textBuilder.toString(), words)
    }

    private fun convertMorphology(morphology: String): List<String> {
        val result = mutableListOf<String>()
        for ((index, map) in reader.morphology.withIndex()) {
            val tag = morphology.substring(index, index + 1)
            if (tag != "-") {
                result.add(map[tag]!!)
            }
        }
        return result
    }
}

class ProielReader(private val doc: Document) {
    val title: String?
    val pos: Map<String, String>
    val morphology: List<Map<String, String>>
    val divs: List<ProielDiv>

    init {
        pos = loadPOS(doc)
        morphology = loadMorphology(doc)
        val source = doc.rootElement.getChild("source")
        title = source.getChild("title")?.text
        divs = source.getChildren("div").map { ProielDiv(this, it) }
    }

    private fun loadPOS(document: Document): Map<String, String> {
        val node = document.rootElement.getChild("annotation").getChild("parts-of-speech")
        return loadTagValueMap(node)
    }

    private fun loadTagValueMap(node: Element): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (child in node.getChildren("value")) {
            result[child.getAttributeValue("tag")] = child.getAttributeValue("summary")
        }
        return result
    }

    private fun loadMorphology(document: Document): List<Map<String, String>> {
        val node = document.rootElement.getChild("annotation").getChild("morphology")
        return node.getChildren("field").map {
            loadTagValueMap(it)
        }
    }
}

// http://dev.syntacticus.org
fun main() {
    val bosworthToller = BosworthToller("dictionaries/bosworth_entries_export.csv")

    val doc = SAXBuilder().build(File("corpus/æls.xml"))
    val proiel = ProielReader(doc)
    val div = proiel.divs[0]
    val sentence = div.convertSentence(3)

    val ieRepo = JsonGraphRepository.fromJson(Path.of("data/ie"))
    val language = ieRepo.languageByShortName("OE")!!
    importLemmatizedText(ieRepo, language, bosworthToller, proiel.title + " - " + div.title, sentence)
    ieRepo.save()
}
