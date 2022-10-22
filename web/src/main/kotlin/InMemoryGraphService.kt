package ru.yole.etymograph.web

import org.springframework.stereotype.Service
import ru.yole.etymograph.GraphRepository
import ru.yole.etymograph.JsonGraphRepository

abstract class GraphService {
    abstract val graph: GraphRepository
}

@Service
class InMemoryGraphService: GraphService() {
    override val graph = JsonGraphRepository.fromJson(InMemoryGraphService::class.java.classLoader.getResourceAsStream("jrrt.json"))
}
