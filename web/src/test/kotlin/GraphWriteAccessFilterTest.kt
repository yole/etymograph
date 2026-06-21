package page.yole.etymograph.web

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import page.yole.etymograph.Graph
import page.yole.etymograph.InMemoryGraph

@RunWith(SpringRunner::class)
@SpringBootTest
@AutoConfigureMockMvc
@Import(GraphWriteAccessFilterTest.TestController::class)
@TestPropertySource(properties = [
    "etymograph.auth.enabled=true",
    "etymograph.frontendUrl=http://localhost:3000",
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret"
])
class GraphWriteAccessFilterTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var graphService: GraphService

    private val testGraph: Graph = object : InMemoryGraph() {
        override val id: String
            get() = "testgraph"

        override val name: String
            get() = "Test Graph"
    }

    @Before
    fun setup() {
        given(graphService.allGraphs()).willReturn(listOf(testGraph))
        given(graphService.resolveGraph("testgraph")).willReturn(testGraph)
        given(graphService.canWrite("testgraph", "writer@example.com")).willReturn(true)
        given(graphService.canWrite("testgraph", "reader@example.com")).willReturn(false)
    }

    @Test
    fun getRemainsPublic() {
        mockMvc.perform(get("/testgraph/protected-get"))
            .andExpect(status().isOk)
            .andExpect(content().string("ok"))
    }

    @Test
    fun postRequiresAuthentication() {
        mockMvc.perform(post("/testgraph/protected-post"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun postRejectsAuthenticatedUserWithoutWriteAccess() {
        mockMvc.perform(
            post("/testgraph/protected-post").with(
                oauth2Login().attributes {
                    it["email"] = "reader@example.com"
                }
            )
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun postAllowsAuthenticatedWriter() {
        mockMvc.perform(
            post("/testgraph/protected-post").with(
                oauth2Login().attributes {
                    it["email"] = "writer@example.com"
                }
            )
        )
            .andExpect(status().isOk)
            .andExpect(content().string("saved"))
    }

    @RestController
    class TestController {
        @GetMapping("/testgraph/protected-get")
        fun publicGet(): String = "ok"

        @PostMapping("/testgraph/protected-post")
        fun protectedPost(): String = "saved"
    }
}
