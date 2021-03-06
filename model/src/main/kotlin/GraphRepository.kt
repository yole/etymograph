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
}
