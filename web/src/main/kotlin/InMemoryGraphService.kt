package ru.yole.etymograph.web

import org.springframework.stereotype.Service
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.parser.parseGraph

abstract class GraphService {
    abstract val graph: GraphRepository
}

@Service
class InMemoryGraphService: GraphService() {
    override val graph = parseGraph(InMemoryGraphService::class.java.classLoader.getResourceAsStream("jrrt.txt"))
}