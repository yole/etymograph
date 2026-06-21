package page.yole.etymograph.web

import org.springframework.stereotype.Service
import page.yole.etymograph.Dictionary
import page.yole.etymograph.importers.Wiktionary
import page.yole.etymograph.web.controllers.badRequest

@Service
class DictionaryService {
    fun createDictionary(id: String): Dictionary {
        return when (id) {
            "wiktionary" -> Wiktionary()
            else -> badRequest("Unknown dictionary ID $id")
        }
    }
}
