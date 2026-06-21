package page.yole.etymograph.web

import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringRunner::class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = [
    "etymograph.auth.enabled=true",
    "etymograph.frontendUrl=http://localhost:3000",
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret"
])
class AuthControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun meAnonymous() {
        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authEnabled").value(true))
            .andExpect(jsonPath("$.authenticated").value(false))
    }

    @Test
    fun meAuthenticated() {
        mockMvc.perform(
            get("/auth/me").with(
                oauth2Login().attributes {
                    it["email"] = "editor@example.com"
                    it["name"] = "Editor"
                    it["picture"] = "https://example.com/avatar.png"
                }
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authEnabled").value(true))
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.email").value("editor@example.com"))
            .andExpect(jsonPath("$.name").value("Editor"))
            .andExpect(jsonPath("$.pictureUrl").value("https://example.com/avatar.png"))
    }

    @Test
    fun googleAuthorizationRedirectAvailable() {
        mockMvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun logoutClearsAuthentication() {
        mockMvc.perform(
            post("/auth/logout").with(
                oauth2Login().attributes {
                    it["email"] = "editor@example.com"
                }
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(false))
    }
}
