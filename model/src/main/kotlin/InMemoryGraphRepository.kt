package ru.yole.etymograph

open class InMemoryGraphRepository : GraphRepository() {
    protected val languages = mutableMapOf<String, Language>()
    protected val corpus = mutableListOf<CorpusText>()
    private val words = mutableMapOf<Language, MutableMap<String, MutableList<Word>>>()
    private val linksFrom = mutableMapOf<Word, MutableList<Link>>()
    private val linksTo = mutableMapOf<Word, MutableList<Link>>()
    private val rules = mutableListOf<Rule>()

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
        return wordsInLang[text.toLowerCase()] ?: emptyList()
    }

    override fun allCorpusTexts(): Iterable<CorpusText> {
        return corpus
    }

    override fun corpusTextsInLanguage(lang: Language): Iterable<CorpusText> {
        return corpus.filter { it.language == lang }
    }

    fun addWord(
        text: String,
        language: Language,
        gloss: String?,
        source: String?,
        notes: String?
    ): Word {
        val wordsForLanguage = words.getOrPut(language) { mutableMapOf() }
        val wordsByText = wordsForLanguage.getOrPut(text.toLowerCase()) { mutableListOf() }
        wordsByText.find { it.gloss == gloss }?.let { return it }
        return createWord(text, language, gloss, source, notes).also { wordsByText.add(it) }
    }

    protected open fun createWord(
        text: String,
        language: Language,
        gloss: String?,
        source: String?,
        notes: String?
    ) = Word(text, language, gloss, source, notes)

    fun addLink(fromWord: Word, toWord: Word, type: LinkType, rule: Rule?, source: String?, notes: String?): Link {
        return createLink(fromWord, toWord, type, rule, source, notes).also {
            linksFrom.getOrPut(it.fromWord) { mutableListOf() }.add(it)
            linksTo.getOrPut(it.toWord) { mutableListOf() }.add(it)
        }
    }

    protected open fun createLink(fromWord: Word, toWord: Word, type: LinkType, rule: Rule?, source: String?, notes: String?): Link {
        return Link(fromWord, toWord, type, rule, source, notes)
    }

    override fun getLinksFrom(word: Word): Iterable<Link> {
        return linksFrom[word] ?: emptyList()
    }

    override fun getLinksTo(word: Word): Iterable<Link> {
        return linksTo[word] ?: emptyList()
    }

    fun addRule(
        fromLanguage: Language,
        toLanguage: Language,
        fromPattern: String,
        toPattern: String,
        addedCategories: String?,
        source: String?,
        notes: String?
    ): Rule {
        return createRule(fromLanguage, toLanguage, fromPattern, toPattern, addedCategories, source, notes)
            .also { rules.add(it) }
    }

    protected open fun createRule(
        fromLanguage: Language,
        toLanguage: Language,
        fromPattern: String,
        toPattern: String,
        addedCategories: String?,
        source: String?,
        notes: String?
    ) = Rule(fromLanguage, toLanguage, fromPattern, toPattern, addedCategories, source, notes)

    fun findMatchingRule(fromWord: Word, toWord: Word): Rule? {
        for (rule in rules) {
            if (rule.matches(toWord) && rule.apply(toWord.text) == fromWord.text) {
                return rule
            }
        }
        return null
    }
}
