package ru.yole.etymograph.web

import org.springframework.stereotype.Service
import ru.yole.etymograph.Dictionary
import ru.yole.etymograph.importers.Wiktionary
import ru.yole.etymograph.web.controllers.badRequest

@Service
class DictionaryService {
    fun createDictionary(id: String): Dictionary {
        return when (id) {
            "wiktionary" -> Wiktionary()
            else -> badRequest("Unknown dictionary ID $id")
        }
    }
}
