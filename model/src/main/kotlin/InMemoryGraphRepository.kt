package ru.yole.etymograph

import java.text.Collator
import java.util.*

open class InMemoryGraphRepository : GraphRepository() {
    protected val languages = mutableMapOf<String, Language>()
    protected val corpus = mutableListOf<CorpusText>()
    private val words = mutableMapOf<Language, MutableMap<String, MutableList<Word>>>()
    protected val allWords = mutableListOf<Word>()
    private val linksFrom = mutableMapOf<Word, MutableList<Link>>()
    private val linksTo = mutableMapOf<Word, MutableList<Link>>()
    protected val rules = mutableListOf<Rule>()
    protected val namedCharacterClasses = mutableMapOf<Language, MutableList<CharacterClass>>()

    fun addLanguage(language: Language) {
        languages[language.shortName] = language
    }

    override fun allLanguages(): Iterable<Language> {
        return languages.values
    }

    override fun languageByShortName(languageShortName: String): Language {
        return languages[languageShortName] ?: UnknownLanguage
    }

    override fun addCorpusText(
        text: String,
        title: String?,
        language: Language,
        words: List<Word>,
        source: String?,
        notes: String?
    ): CorpusText {
        return CorpusText(corpus.size + 1, text, title, language, words, source, notes).also { corpus += it }
    }

    override fun corpusTextById(id: Int): CorpusText? {
        return corpus.getOrNull(id - 1)
    }

    override fun wordsByText(lang: Language, text: String): List<Word> {
        val wordsInLang = words[lang] ?: return emptyList()
        return wordsInLang[text.toLowerCase()] ?: emptyList()
    }

    override fun wordById(id: Int): Word? {
        return allWords.getOrNull(id)
    }

    override fun dictionaryWords(lang: Language): List<Word> {
        val wordsInLang = words[lang] ?: return emptyList()
        return wordsInLang.flatMap { it.value }
            .filter {
                it.gloss != null &&
                        linksFrom[it]?.none { link -> link.type == Link.Derived && !link.toWord.isRoot() } != false }
            .sortedWith { o1, o2 -> Collator.getInstance(Locale.FRANCE).compare(o1.text, o2.text) }
    }

    private fun Word.isRoot() = text.all { c -> c.isUpperCase() || c == '-' }

    override fun allCorpusTexts(): Iterable<CorpusText> {
        return corpus
    }

    override fun corpusTextsInLanguage(lang: Language): Iterable<CorpusText> {
        return corpus.filter { it.language == lang }
    }

    fun addNamedCharacterClass(language: Language, name: String, characters: String) {
        namedCharacterClasses.getOrPut(language) { mutableListOf() }.add(CharacterClass(name, characters))
    }

    override fun characterClassByName(lang: Language, name: String): CharacterClass? {
        return namedCharacterClasses[lang]?.singleOrNull { it.name == name }
    }

    override fun addWord(
        text: String,
        language: Language,
        gloss: String?,
        source: String?,
        notes: String?
    ): Word {
        val wordsForLanguage = words.getOrPut(language) { mutableMapOf() }
        val wordsByText = wordsForLanguage.getOrPut(text.toLowerCase()) { mutableListOf() }
        wordsByText.find { it.gloss == gloss || gloss.isNullOrEmpty() }?.let {
            return it
        }
        return createWord(text, language, gloss, source, notes).also { wordsByText.add(it) }
    }

    override fun save() {
    }

    private fun createWord(
        text: String,
        language: Language,
        gloss: String?,
        source: String?,
        notes: String?
    ) = Word(allWords.size, text, language, gloss, source, notes).also {
        allWords.add(it)
    }

    override fun addLink(fromWord: Word, toWord: Word, type: LinkType, rule: Rule?, source: String?, notes: String?): Link {
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

    override fun addRule(
        fromLanguage: Language,
        toLanguage: Language,
        branches: List<RuleBranch>,
        addedCategories: String?,
        source: String?,
        notes: String?
    ): Rule {
        return Rule(rules.size, fromLanguage, toLanguage, branches, addedCategories, source, notes)
            .also { rules.add(it) }
    }

    override fun findMatchingRule(fromWord: Word, toWord: Word): Rule? {
        for (rule in rules) {
            if (rule.matches(toWord) && rule.apply(toWord) == fromWord.text) {
                return rule
            }
        }
        return null
    }

    override fun allRules(): Iterable<Rule> {
        return rules
    }

    override fun ruleById(id: Int): Rule? {
        return rules.getOrNull(id)
    }
}
