package ru.yole.etymograph.web

import ru.yole.etymograph.Dictionary
import ru.yole.etymograph.importers.TestWiktionary

class TestDictionaryService : DictionaryService() {
    override fun createDictionary(id: String): Dictionary {
        if (id == "wiktionary") {
            return TestWiktionary(TestDictionaryService::class.java)
        }
        return super.createDictionary(id)
    }
}