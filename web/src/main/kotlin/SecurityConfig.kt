package ru.yole.etymograph.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AuthStatusViewModel(
    val authEnabled: Boolean,
    val authenticated: Boolean,
    val email: String? = null,
    val name: String? = null
)

@RestController
@RequestMapping("/auth")
class AuthController(
    @param:Value("\${etymograph.auth.enabled:false}")
    private val authEnabled: Boolean
) {
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: OAuth2User?): AuthStatusViewModel {
        return AuthStatusViewModel(
            authEnabled = authEnabled,
            authenticated = principal != null,
            email = principal?.getAttribute("email"),
            name = principal?.getAttribute("name")
        )
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): AuthStatusViewModel {
        val authentication = SecurityContextHolder.getContext().authentication
        SecurityContextLogoutHandler().logout(request, response, authentication)
        return AuthStatusViewModel(authEnabled = authEnabled, authenticated = false)
    }
}

@Configuration
@ConditionalOnProperty(prefix = "etymograph.auth", name = ["enabled"], havingValue = "true")
class AuthEnabledSecurityConfig(
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
