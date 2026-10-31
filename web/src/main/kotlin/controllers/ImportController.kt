package page.yole.etymograph.web.controllers

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import page.yole.etymograph.Graph
import page.yole.etymograph.importers.TLHDigImportContext
import page.yole.etymograph.importers.importTLHDig

@RestController
@RequestMapping("/{graph}/import")
class ImportController {
    data class TLHDigImportResult(val messages: List<String>, val newWords: List<WordRefViewModel>)

    @PostMapping("/tlhDig", consumes = ["multipart/form-data"])
    fun importTLHDigText(graph: Graph, @RequestParam("file") file: MultipartFile): TLHDigImportResult {
        if (file.isEmpty) {
            badRequest("Select a non-empty TLHDig XML file")
        }
        if (graph.languageByShortName("Hitt") == null) {
            badRequest("The graph has no Hittite language (Hitt)")
        }
        val context = TLHDigImportContext()
        try {
            file.inputStream.use { importTLHDig(graph, it, context) }
        }
        catch (e: Exception) {
            badRequest(e.message ?: "Cannot import TLHDig text")
        }
        return TLHDigImportResult(context.messages, context.newWords.distinctBy { it.id }.map { it.toRefViewModel() })
    }
}
