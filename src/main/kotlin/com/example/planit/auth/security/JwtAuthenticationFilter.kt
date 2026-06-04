package com.example.planit.auth.security

import com.example.planit.user.domain.UserAccountRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userAccountRepository: UserAccountRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        val token = header?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()

        if (!token.isNullOrBlank()) {
            runCatching {
                val claims = jwtTokenProvider.parse(token)
                if (claims.type == JwtTokenType.ACCESS) {
                    val userId = claims.subject.toLong()
                    val user = userAccountRepository.findById(userId).orElse(null)
                    if (user != null) {
                        val principal = AuthenticatedUser(user.id!!, user.email)
                        val authentication = UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        )
                        SecurityContextHolder.getContext().authentication = authentication
                    }
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
