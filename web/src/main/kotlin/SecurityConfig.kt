package page.yole.etymograph.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.filter.OncePerRequestFilter

data class AuthStatusViewModel(
    val authEnabled: Boolean,
    val authenticated: Boolean,
    val email: String? = null,
    val name: String? = null,
    val pictureUrl: String? = null,
    val editableGraphs: Array<String>
)

@RestController
@RequestMapping("/auth")
class AuthController(
    @param:Value("\${etymograph.auth.enabled:false}")
    private val authEnabled: Boolean,
    val graphService: GraphService
) {
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: OAuth2User?): AuthStatusViewModel {
        val email = principal?.getAttribute<String?>("email")
        val editableGraphs = if (authEnabled) {
            email?.let { graphService.getEditableGraphs(it) }?.toTypedArray() ?: arrayOf()
        } else {
            graphService.allGraphs().map { it.id }.toTypedArray()
        }
        return AuthStatusViewModel(
            authEnabled = authEnabled,
            authenticated = principal != null,
            email = email,
            name = principal?.getAttribute("name"),
            pictureUrl = principal?.getAttribute("picture"),
            editableGraphs
        )
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): AuthStatusViewModel {
        val authentication = SecurityContextHolder.getContext().authentication
        SecurityContextLogoutHandler().logout(request, response, authentication)
        return AuthStatusViewModel(authEnabled = authEnabled, authenticated = false, editableGraphs = arrayOf())
    }
}

@Configuration
@ConditionalOnProperty(prefix = "etymograph.auth", name = ["enabled"], havingValue = "true")
class AuthEnabledSecurityConfig(
    private val graphService: GraphService,
    @param:Value("\${etymograph.frontendUrl:http://localhost:3000}")
    private val frontendUrl: String
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/oauth2/**", "/login/**", "/auth/**").permitAll()
                    .anyRequest().permitAll()
            }
            .oauth2Login { oauth ->
                oauth.defaultSuccessUrl(frontendUrl, true)
            }
            .logout { logout ->
                logout.logoutSuccessUrl(frontendUrl)
            }
            .addFilterAfter(GraphWriteAccessFilter(graphService), AnonymousAuthenticationFilter::class.java)
        return http.build()
    }
}

@Configuration
@ConditionalOnProperty(prefix = "etymograph.auth", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class AuthDisabledSecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
        return http.build()
    }
}

private class GraphWriteAccessFilter(
    private val graphService: GraphService
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val graph = graphService.graphByRequestPath(request.requestURI)
        if (graph != null && request.method == "POST") {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || authentication is AnonymousAuthenticationToken || !authentication.isAuthenticated) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                return
            }

            val email = (authentication.principal as? OAuth2User)?.getAttribute<String>("email")
            if (email == null || !graphService.canWrite(graph.id, email)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have write access to this graph")
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}
