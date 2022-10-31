package ru.yole.etymograph

abstract class GraphRepository {
    abstract fun allLanguages(): Iterable<Language>
    abstract fun languageByShortName(languageShortName: String): Language

    abstract fun allCorpusTexts(): Iterable<CorpusText>
    abstract fun corpusTextsInLanguage(lang: Language): Iterable<CorpusText>
    abstract fun corpusTextById(id: Int): CorpusText?

    abstract fun getLinksFrom(word: Word): Iterable<Link>
    abstract fun getLinksTo(word: Word): Iterable<Link>
    abstract fun wordsByText(lang: Language, text: String): List<Word>
    abstract fun dictionaryWords(lang: Language): List<Word>

    abstract fun ruleById(id: Int): Rule?

    abstract fun characterClassByName(lang: Language, name: String): CharacterClass?

    abstract fun addWord(
        text: String,
        language: Language,
        gloss: String?,
        source: String?,
        notes: String?
    ): Word

    abstract fun addRule(
        fromLanguage: Language,
        toLanguage: Language,
        branches: List<RuleBranch>,
        addedCategories: String?,
        source: String?,
        notes: String?
    ): Rule

    abstract fun save()
}
