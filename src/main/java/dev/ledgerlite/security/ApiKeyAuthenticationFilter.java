package dev.ledgerlite.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads {@code Authorization: Bearer <api key>}. Requests without the header carry on
 * unauthenticated and get rejected by the authorization rules; a header with a bad key
 * is rejected straight away.
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final List<SimpleGrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_CLIENT"));

    private final ApiClientRepository clients;
    private final AuthenticationEntryPoint entryPoint;

    ApiKeyAuthenticationFilter(ApiClientRepository clients, AuthenticationEntryPoint entryPoint) {
        this.clients = clients;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        String key = header.substring(BEARER.length()).trim();
        Optional<ApiClient> client = key.isEmpty() ? Optional.empty() : clients.findActiveByKeyHash(ApiKeys.hash(key));
        if (client.isEmpty()) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("Unknown API key"));
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(client.get(), null, AUTHORITIES));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
