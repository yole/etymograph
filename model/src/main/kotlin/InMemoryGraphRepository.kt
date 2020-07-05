package ru.yole.etymograph

class InMemoryGraphRepository : GraphRepository() {
    private val languages = mutableMapOf<String, Language>()
    private val corpus = mutableListOf<CorpusText>()
    private val words = mutableMapOf<Language, MutableMap<String, MutableList<Word>>>()
    private val linksFrom = mutableMapOf<Word, MutableList<Link>>()
    private val linksTo = mutableMapOf<Word, MutableList<Link>>()

    fun addLanguage(language: Language) {
        languages[language.shortName] = language
    }

    override fun allLanguages(): Iterable<Language> {
        return languages.values
    }

    override fun languageByShortName(languageShortName: String): Language {
        return languages[languageShortName] ?: UnknownLanguage
    }

    fun addCorpusText(
        text: String,
        title: String?,
        language: Language,
        words: List<Word>,
        source: String?,
        notes: String?
    ) {
        corpus += CorpusText(corpus.size + 1, text, title, language, words, source, notes)
    }

    override fun corpusTextById(id: Int): CorpusText? {
        return corpus.getOrNull(id - 1)
    }

    override fun wordsByText(lang: Language, text: String): List<Word> {
        val wordsInLang = words[lang] ?: return emptyList()
        return wordsInLang[text] ?: emptyList()
    }

    override fun allCorpusTexts(): Iterable<CorpusText> {
        return corpus
    }

    override fun corpusTextsInLanguage(lang: Language): Iterable<CorpusText> {
        return corpus.filter { it.language == lang }
    }

    fun addWord(word: Word) {
        val wordsForLanguage = words.getOrPut(word.language) { mutableMapOf() }
        wordsForLanguage.getOrPut(word.text) { mutableListOf() }.add(word)
    }

    fun addLink(link: Link) {
        linksFrom.getOrPut(link.fromWord) { mutableListOf() }.add(link)
        linksTo.getOrPut(link.toWord) { mutableListOf() }.add(link)
    }

    override fun getLinksFrom(word: Word): Iterable<Link> {
        return linksFrom[word] ?: emptyList()
    }

    override fun getLinksTo(word: Word): Iterable<Link> {
        return linksTo[word] ?: emptyList()
    }
}
