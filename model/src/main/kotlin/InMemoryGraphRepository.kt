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

    override fun langEntityById(id: Int): LangEntity? =
        allLangEntities.getOrNull(id)

    override fun addCorpusText(
        text: String,
        title: String?,
        language: Language,
        words: List<Word>,
        source: String?,
        notes: String?
    ): CorpusText {
        return CorpusText(allLangEntities.size, text, title, language, words, source, notes).also {
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
            linksFrom[word.id]?.any {
                link -> link.type == Link.Agglutination && link.toEntity is Word && !link.toEntity.isRoot()
            } == true -> WordKind.COMPOUND
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
            if (link.fromEntity is Word && (link.type == Link.Derived || link.type == Link.Agglutination)) {
                result.add(link.fromEntity)
            }
        }
        return result
    }

    override fun findParseCandidates(word: Word): List<ParseCandidate> {
        return findParseCandidates(word.language, word.text, word.pos, emptyList(), emptySet(), true)
    }

    private fun findParseCandidates(language: Language, text: String, pos: String?, prevRules: List<Rule>,
                                    excludeParadigms: Set<Paradigm>, recurse: Boolean): List<ParseCandidate> {
        return rules
            .filter { rule ->
                rule.fromLanguage == language && rule.toLanguage == language &&
                        (pos == null || rule.toPOS == null || pos == rule.toPOS) &&
                paradigmForRule(rule).let { it == null || it !in excludeParadigms }
            }
            .flatMap { rule ->
                rule.reverseApply(Word(-1, text, language, pos=pos))
                    .filter {
                        language.normalizeWord(it) != language.normalizeWord(text) &&
                                isAcceptableWord(language, it)
                    }
                    .flatMap { text ->
                        val w = wordsByText(language, text)
                        if (w.isNotEmpty())
                            w.map { ParseCandidate(text, listOf(rule) + prevRules, it.pos, it) }
                        else
                            listOf(ParseCandidate(text,  listOf(rule) + prevRules, rule.fromPOS, null))
                    }
                    .flatMap {
                        if (recurse) {
                            val paradigm = paradigmForRule(rule)
                            val recurseExcludeParadigms = if (paradigm == null) excludeParadigms else excludeParadigms + setOf(paradigm)
                            listOf(it) + findParseCandidates(language, it.text, it.pos, it.rules, recurseExcludeParadigms, false)
                        }
                        else {
                            listOf(it)
                        }
                    }
            }
    }

    fun isAcceptableWord(language: Language, wordText: String): Boolean {
        val word = Word(-1, wordText, language)
        val vowels = language.phonemeClassByName(PhonemeClass.vowelClassName)
        val phonemes = PhonemeIterator(word)
        if (language.syllableStructures.isNotEmpty() && vowels != null) {
            val syllables = breakIntoSyllables(word)
            for (syllable in syllables) {
                if (analyzeSyllableStructure(vowels, phonemes, syllable) !in language.syllableStructures) {
                    return false
                }
            }
        }
        if (language.wordFinals.isNotEmpty()) {
            phonemes.advanceTo(phonemes.size - 1)
            val final = phonemes.current
            if (language.wordFinals.none { it == final || language.phonemeClassByName(it)?.matchesCurrent(phonemes) == true }) {
                return false
            }
        }
        return true
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

    override fun updateWordText(word: Word, text: String) {
        mapOfWordsByText(word.language, word.text).remove(word)
        word.text = text
        mapOfWordsByText(word.language, text).add(word)
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
            corpusText.removeWord(word)
        }

        val wordsByText = mapOfWordsByText(word.language, word.text)
        wordsByText.remove(word)
        linksFrom[word.id]?.let {
            for (link in it.toList()) {
                deleteLink(link.fromEntity, link.toEntity, link.type)
            }
        }
        linksTo[word.id]?.let {
            for (link in it.toList()) {
                deleteLink(link.fromEntity, link.toEntity, link.type)
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
            linksFrom.getOrPut(it.fromEntity.id) { mutableListOf() }.add(it)
            linksTo.getOrPut(it.toEntity.id) { mutableListOf() }.add(it)
        }
    }

    override fun substituteKnownWord(baseWord: Word, derivedWord: Word): Word {
        val links = linksTo[baseWord.id] ?: return derivedWord
        for (link in links) {
            if (link.type == Link.Derived) {
                val fromEntity = link.fromEntity
                if (fromEntity is Word && fromEntity.getOrComputeGloss(this) == derivedWord.gloss) {
                    return fromEntity
                }
            }
        }
        return derivedWord
    }

    override fun deleteLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType): Boolean {
        val result = linksFrom.getOrPut(fromEntity.id) { mutableListOf() }.removeIf { it.toEntity == toEntity && it.type == type }
        linksTo.getOrPut(toEntity.id) { mutableListOf() }.removeIf { it.fromEntity == fromEntity && it.type == type }
        return result
    }

    override fun findLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType): Link? {
        return linksFrom[fromEntity.id]?.find { it.toEntity == toEntity && it.type == type } ?:
            linksFrom[toEntity.id]?.find { it.fromEntity == toEntity && it.type == type }
    }

    protected open fun createLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType, rules: List<Rule>, source: String?, notes: String?): Link {
        return Link(fromEntity, toEntity, type, rules, source, notes)
    }

    override fun getLinksFrom(entity: LangEntity): Iterable<Link> {
        return linksFrom[entity.id] ?: emptyList()
    }

    override fun getLinksTo(entity: LangEntity): Iterable<Link> {
        return linksTo[entity.id] ?: emptyList()
    }

    override fun addRule(
        name: String,
        fromLanguage: Language,
        toLanguage: Language,
        logic: RuleLogic,
        addedCategories: String?,
        replacedCategories: String?,
        fromPOS: String?,
        toPOS: String?,
        source: String?,
        notes: String?
    ): Rule {
        return Rule(allLangEntities.size, name, fromLanguage, toLanguage,
            logic,
            addedCategories, replacedCategories, fromPOS, toPOS, source, notes
        ).also {
            addRule(it)
        }
    }

    fun addRule(rule: Rule) {
        rules.add(rule)
        allLangEntities.add(rule)
    }

    override fun paradigmsForLanguage(lang: Language): List<Paradigm> {
        return paradigms.filter { it.language == lang }
    }

    override fun paradigmById(id: Int): Paradigm? {
        return paradigms.getOrNull(id)
    }

    override fun paradigmForRule(rule: Rule): Paradigm? {
        return paradigmsForLanguage(rule.fromLanguage).find { rule in it.collectAllRules() }
    }

    override fun findMatchingRule(fromWord: Word, toWord: Word): Rule? {
        for (rule in rules) {
            if (rule.matches(toWord) && rule.apply(toWord, this).text == fromWord.text) {
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

    companion object {
        val EMPTY = InMemoryGraphRepository()
    }
}
