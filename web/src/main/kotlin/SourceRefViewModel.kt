package ru.yole.etymograph.web

import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.SourceRef

class SourceRefViewModel(val pubId: Int?, val pubRefId: String?, val refText: String)

fun List<SourceRef>.toViewModel(graph: GraphRepository): List<SourceRefViewModel>  {
    return map { ref ->
        SourceRefViewModel(
            ref.pubId,
            ref.pubId?.let { graph.publicationById(it) }?.refId,
            ref.refText
        )
    }
}

fun List<SourceRef>.toEditableText(graph: GraphRepository): String {
    return joinToString(", ") { ref ->
        ref.pubId?.let { oubId ->
            graph.publicationById(oubId)?.let { "${it.refId}:${ref.refText}"}
        } ?: ref.refText
    }
}

fun parseSourceRefs(graph: GraphRepository, source: String?): List<SourceRef> {
    if (source.isNullOrBlank()) return emptyList()
    return source.split(',').map { sourceRefText ->
        val text = sourceRefText.trim()
        if (':' in text) {
            val (refId, refText) = text.split(':', limit = 2)
            val pub = graph.publicationByRefId(refId)
            pub?.let { SourceRef(it.id, refText.trim()) } ?: SourceRef(null, text)
        }
        else {
            SourceRef(null, text)
        }
    }
}
