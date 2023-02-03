package ru.yole.etymograph

abstract class GraphRepository {
    abstract fun allLanguages(): Iterable<Language>
    abstract fun languageByShortName(languageShortName: String): Language

    abstract fun allCorpusTexts(): Iterable<CorpusText>
    abstract fun corpusTextsInLanguage(lang: Language): Iterable<CorpusText>
    abstract fun corpusTextById(id: Int): CorpusText?

    abstract fun getLinksFrom(word: Word): Iterable<Link>
    abstract fun getLinksTo(word: Word): Iterable<Link>
    abstract fun wordById(id: Int): Word?
    abstract fun wordsByText(lang: Language, text: String): List<Word>
    abstract fun dictionaryWords(lang: Language): List<Word>
    abstract fun compoundWords(lang: Language): List<Word>

    abstract fun allRules(): Iterable<Rule>
    abstract fun ruleById(id: Int): Rule?
    abstract fun ruleByName(ruleName: String): Rule?

    abstract fun addWord(
        text: String,
        language: Language,
        gloss: String?,
        pos: String?,
        source: String?,
        notes: String?
    ): Word

    abstract fun deleteWord(word: Word)

    abstract fun addRule(
        name: String,
        fromLanguage: Language,
        toLanguage: Language,
        branches: List<RuleBranch>,
        addedCategories: String?,
        replacedCategories: String?,
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

    abstract fun paradigmsForLanguage(lang: Language): List<Paradigm>
    abstract fun paradigmById(id: Int): Paradigm?

    abstract fun addLink(fromWord: Word, toWord: Word, type: LinkType, rules: List<Rule>, source: String?, notes: String?): Link
    abstract fun deleteLink(fromWord: Word, toWord: Word, type: LinkType): Boolean
    abstract fun findLink(fromWord: Word, toWord: Word, type: LinkType): Link?

    abstract fun substituteKnownWord(baseWord: Word, derivedWord: Word): Word

    abstract fun findMatchingRule(fromWord: Word, toWord: Word): Rule?
    abstract fun findRuleExamples(rule: Rule): List<Link>

    abstract fun save()
}
