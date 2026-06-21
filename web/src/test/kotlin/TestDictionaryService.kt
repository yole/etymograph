package page.yole.etymograph.web

import page.yole.etymograph.Dictionary
import page.yole.etymograph.importers.TestWiktionary

class TestDictionaryService : DictionaryService() {
    override fun createDictionary(id: String): Dictionary {
        if (id == "wiktionary") {
            return TestWiktionary(TestDictionaryService::class.java)
        }
        return super.createDictionary(id)
    }
}