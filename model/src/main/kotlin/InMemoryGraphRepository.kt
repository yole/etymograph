package ru.yole.etymograph

import java.text.Collator
import java.util.*

open class InMemoryGraphRepository : GraphRepository() {
    protected val languages = mutableMapOf<String, Language>()
    protected val corpus = mutableListOf<CorpusText>()
    private val words = mutableMapOf<Language, MutableMap<String, MutableList<Word>>>()
    val allLangEntities = mutableListOf<LangEntity?>()
    private val linksFrom = mutableMapOf<Int, MutableList<Link>>()
    private val linksTo = mutableMapOf<Int, MutableList<Link>>()
    protected val rules = mutableListOf<Rule>()
    protected val paradigms = mutableListOf<Paradigm>()

    fun addLanguage(language: Language) {
        languages[language.shortName] = language
    }

    override fun allLanguages(): Iterable<Language> {
        return languages.values
    }

    override fun languageByShortName(languageShortName: String): Language? {
        return languages[languageShortName]
    }

    override fun addCorpusText(
        text: String,
        title: String?,
        language: Language,
        words: List<Word>,
        source: String?,
        notes: String?
    ): CorpusText {
        return CorpusText(allLangEntities.size, text, title, language, words.toMutableList(), source, notes).also {
            corpus += it
            allLangEntities += it
        }
    }

    override fun corpusTextById(id: Int): CorpusText? {
        return allLangEntities.getOrNull(id) as? CorpusText
    }

    override fun addParadigm(name: String, language: Language, pos: String): Paradigm {
        return Paradigm(paradigms.size, name, language, pos).also { paradigms += it }
    }

    override fun wordsByText(lang: Language, text: String): List<Word> {
        val wordsInLang = words[lang] ?: return emptyList()
        return wordsInLang[lang.normalizeWord(text)] ?: emptyList()
    }

    override fun wordById(id: Int): Word? {
        return allLangEntities.getOrNull(id) as? Word
    }

    override fun dictionaryWords(lang: Language): List<Word> {
        return filteredWords(lang, WordKind.NORMAL)
    }

    override fun compoundWords(lang: Language): List<Word> {
        return filteredWords(lang, WordKind.COMPOUND)
    }

    override fun nameWords(lang: Language): List<Word> {
        return filteredWords(lang, WordKind.NAME)
    }

    enum class WordKind { NAME, COMPOUND, DERIVED, NORMAL }

    private fun classifyWord(word: Word): WordKind {
        return when {
            word.pos == "NP" -> WordKind.NAME
            word.gloss == null -> WordKind.DERIVED
            word.hasGrammarCategory() -> WordKind.DERIVED
            linksFrom[word.id]?.any { link -> link.type == Link.Agglutination && !link.toWord.isRoot() } == true -> WordKind.COMPOUND
            else -> WordKind.NORMAL
        }
    }

    private fun Word.hasGrammarCategory(): Boolean {
        val suffix = gloss?.substringAfterLast('.')
        return !suffix.isNullOrEmpty() && suffix.all { it.isUpperCase() || it.isDigit() }
    }

    private fun filteredWords(lang: Language, kind: WordKind): List<Word> {
        val wordsInLang = words[lang] ?: return emptyList()
        return wordsInLang.flatMap { it.value }
            .filter { classifyWord(it) == kind }
            .sortedWith { o1, o2 -> Collator.getInstance(Locale.FRANCE).compare(o1.text, o2.text) }
    }

    private fun Word.isRoot() = text.all { c -> c.isUpperCase() || c == '-' }

    override fun findAttestations(word: Word): List<Attestation> {
        val allDerivedWords = collectDerivedWords(word)
        val result = mutableListOf<Attestation>()
        corpusText@ for (corpusText in corpusTextsInLanguage(word.language)) {
            for (derivedWord in allDerivedWords) {
                if (corpusText.containsWord(derivedWord)) {
                    result.add(Attestation(derivedWord, corpusText))
                    continue@corpusText
                }
            }
        }
        return result
    }

    private fun collectDerivedWords(word: Word): Collection<Word> {
        val result = mutableSetOf<Word>()
        result.add(word)
        for (link in getLinksTo(word)) {
            result.add(link.fromWord)
        }
        return result
    }

    override fun isHomonym(word: Word): Boolean {
        return wordsByText(word.language, word.text).size > 1
    }

    override fun allCorpusTexts(): Iterable<CorpusText> {
        return corpus
    }

    override fun corpusTextsInLanguage(lang: Language): Iterable<CorpusText> {
        return corpus.filter { it.language == lang }
    }

    override fun findOrAddWord(
        text: String,
        language: Language,
        gloss: String?,
        fullGloss: String?,
        pos: String?,
        source: String?,
        notes: String?
    ): Word {
        val wordsByText = mapOfWordsByText(language, text)
        wordsByText.find { it.getOrComputeGloss(this) == gloss || gloss.isNullOrEmpty() }?.let {
            return it
        }
        return addWord(language, text, gloss, fullGloss, pos, source, notes)
    }

    protected fun addWord(
        language: Language,
        text: String,
        gloss: String?,
        fullGloss: String?,
        pos: String?,
        source: String?,
        notes: String?
    ): Word {
        val wordsByText = mapOfWordsByText(language, text)
        return Word(allLangEntities.size, text, language, gloss, fullGloss, pos, source, notes).also {
            allLangEntities.add(it)
            wordsByText.add(it)
        }
    }

    override fun deleteWord(word: Word) {
        for (corpusText in corpus) {
            corpusText.words.removeIf { it.id == word.id }
        }

        val wordsByText = mapOfWordsByText(word.language, word.text)
        wordsByText.remove(word)
        linksFrom[word.id]?.let {
            for (link in it.toList()) {
                deleteLink(link.fromWord, link.toWord, link.type)
            }
        }
        linksTo[word.id]?.let {
            for (link in it.toList()) {
                deleteLink(link.fromWord, link.toWord, link.type)
            }
        }
        allLangEntities[word.id] = null
    }

    private fun mapOfWordsByText(
        language: Language,
        text: String
    ): MutableList<Word> {
        val wordsForLanguage = words.getOrPut(language) { mutableMapOf() }
        val wordsByText = wordsForLanguage.getOrPut(language.normalizeWord(text)) { mutableListOf() }
        return wordsByText
    }

    override fun save() {
    }

    override fun addLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType, rules: List<Rule>, source: String?, notes: String?): Link {
        return createLink(fromEntity, toEntity, type, rules, source, notes).also {
            linksFrom.getOrPut(it.fromWord.id) { mutableListOf() }.add(it)
            linksTo.getOrPut(it.toWord.id) { mutableListOf() }.add(it)
        }
    }

    override fun substituteKnownWord(baseWord: Word, derivedWord: Word): Word {
        val links = linksTo[baseWord.id] ?: return derivedWord
        for (link in links) {
            if (link.type == Link.Derived && link.fromWord.getOrComputeGloss(this) == derivedWord.gloss) {
                return link.fromWord
            }
        }
        return derivedWord
    }

    override fun deleteLink(fromWord: Word, toWord: Word, type: LinkType): Boolean {
        val result = linksFrom.getOrPut(fromWord.id) { mutableListOf() }.removeIf { it.toWord == toWord && it.type == type }
        linksTo.getOrPut(toWord.id) { mutableListOf() }.removeIf { it.fromWord == fromWord && it.type == type }
        return result
    }

    override fun findLink(fromWord: Word, toWord: Word, type: LinkType): Link? {
        return linksFrom[fromWord.id]?.find { it.toWord == toWord && it.type == type } ?:
            linksFrom[toWord.id]?.find { it.fromWord == toWord && it.type == type }
    }

    protected open fun createLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType, rules: List<Rule>, source: String?, notes: String?): Link {
        return Link(fromEntity as Word, toEntity as Word, type, rules, source, notes)
    }

    override fun getLinksFrom(word: Word): Iterable<Link> {
        return linksFrom[word.id] ?: emptyList()
    }

    override fun getLinksTo(word: Word): Iterable<Link> {
        return linksTo[word.id] ?: emptyList()
    }

    override fun addRule(
        name: String,
        fromLanguage: Language,
        toLanguage: Language,
        branches: List<RuleBranch>,
        addedCategories: String?,
        replacedCategories: String?,
        source: String?,
        notes: String?
    ): Rule {
        return Rule(allLangEntities.size, name, fromLanguage, toLanguage, branches, addedCategories, replacedCategories, source, notes)
            .also {
                rules.add(it)
                allLangEntities.add(it)
            }
    }

    override fun paradigmsForLanguage(lang: Language): List<Paradigm> {
        return paradigms.filter { it.language == lang }
    }

    override fun paradigmById(id: Int): Paradigm? {
        return paradigms.getOrNull(id)
    }

    override fun findMatchingRule(fromWord: Word, toWord: Word): Rule? {
        for (rule in rules) {
            if (rule.matches(toWord) && rule.apply(toWord).text == fromWord.text) {
                return rule
            }
        }
        return null
    }

    override fun findRuleExamples(rule: Rule): List<Link> {
        return linksFrom.values.flatten().filter { rule in it.rules }
    }

    override fun allRules(): Iterable<Rule> {
        return rules
    }

    override fun ruleById(id: Int): Rule? {
        return allLangEntities.getOrNull(id) as? Rule
    }

    override fun ruleByName(ruleName: String): Rule? {
        return rules.find { it.name == ruleName }
    }
}
