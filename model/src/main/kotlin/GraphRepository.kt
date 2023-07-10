package ru.yole.etymograph

abstract class GraphRepository {
    abstract fun allLanguages(): Iterable<Language>
    abstract fun languageByShortName(languageShortName: String): Language?

    abstract fun addLanguage(language: Language)

    abstract fun langEntityById(id: Int): LangEntity?

    abstract fun allCorpusTexts(): Iterable<CorpusText>
    abstract fun corpusTextsInLanguage(lang: Language): Iterable<CorpusText>
    abstract fun corpusTextById(id: Int): CorpusText?

    abstract fun getLinksFrom(entity: LangEntity): Iterable<Link>
    abstract fun getLinksTo(entity: LangEntity): Iterable<Link>
    abstract fun wordById(id: Int): Word?
    abstract fun wordsByText(lang: Language, text: String): List<Word>
    abstract fun updateWordText(word: Word, text: String)
    abstract fun dictionaryWords(lang: Language): List<Word>
    abstract fun compoundWords(lang: Language): List<Word>
    abstract fun nameWords(lang: Language): List<Word>
    abstract fun allWords(lang: Language): List<Word>
    abstract fun findAttestations(word: Word): List<Attestation>
    abstract fun isHomonym(word: Word): Boolean
    abstract fun isCompound(word: Word): Boolean
    abstract fun findParseCandidates(word: Word): List<ParseCandidate>
    abstract fun restoreSegments(word: Word): Word

    abstract fun allRules(): Iterable<Rule>
    abstract fun ruleById(id: Int): Rule?
    abstract fun ruleByName(ruleName: String): Rule?
    abstract fun deleteRule(rule: Rule)

    abstract fun findOrAddWord(
        text: String,
        language: Language,
        gloss: String?,
        fullGloss: String? = null,
        pos: String? = null,
        source: String? = null,
        notes: String? = null
    ): Word

    abstract fun deleteWord(word: Word)

    abstract fun addRule(
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
    ): Rule

    abstract fun addCorpusText(
        text: String,
        title: String?,
        language: Language,
        words: List<Word>,
        source: String?,
        notes: String?
    ): CorpusText

    abstract fun addParadigm(
        name: String,
        language: Language,
        pos: String
    ): Paradigm

    abstract fun allParadigms(): List<Paradigm>
    abstract fun paradigmsForLanguage(lang: Language): List<Paradigm>
    abstract fun paradigmById(id: Int): Paradigm?
    abstract fun paradigmForRule(rule: Rule): Paradigm?

    abstract fun addLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType, rules: List<Rule>, source: String?, notes: String?): Link
    abstract fun deleteLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType): Boolean
    abstract fun findLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType): Link?

    abstract fun substituteKnownWord(baseWord: Word, derivedWord: Word): Word

    abstract fun findMatchingRule(fromWord: Word, toWord: Word): Rule?
    abstract fun findRuleExamples(rule: Rule): List<Link>

    abstract fun save()
}
