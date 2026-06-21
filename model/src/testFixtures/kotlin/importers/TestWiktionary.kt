package page.yole.etymograph.importers

import page.yole.etymograph.Language

class TestWiktionary(val ownerClass: Class<*>) : Wiktionary() {
    override fun loadWiktionaryPageSource(language: Language, title: String): String? {
        ownerClass.getResourceAsStream("/wiktionary/${language.shortName.lowercase()}/$title.txt").use {
            return it?.reader()?.readText()
        }
    }
}

