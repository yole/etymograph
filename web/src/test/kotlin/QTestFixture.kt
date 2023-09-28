package ru.yole.etymograph.web

import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.InMemoryGraphRepository
import ru.yole.etymograph.Language

class QTestFixture {
    val q = Language("Quenya", "q")
    val repo = InMemoryGraphRepository().apply {
        addLanguage(q)
    }

    val graphService = object : GraphService() {
        override val graph: GraphRepository
            get() = repo
    }
}
