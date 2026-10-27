package com.healthee.utils.appsecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtService jwtService;
    private final DefaultRouteValidator routeValidator;
    private final RouteValidator publicRouteValidator;
    private final ObjectMapper objectMapper;

    public JwtFilter(
            JwtService jwtService,
            DefaultRouteValidator routeValidator,
            RouteValidator publicRouteValidator,
            ObjectMapper objectMapper
    ) {
        this.jwtService = jwtService;
        this.routeValidator = routeValidator;
        this.publicRouteValidator = publicRouteValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String path = DefaultRouteValidator.pathWithinApplication(request);
        log.info("JWT filter: method={}, path={}", request.getMethod(), path);

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || publicRouteValidator.isPublic.test(request)
                || path.equals("/magik/public")
                || path.startsWith("/magik/public/")) {
            chain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (token == null) {
            reject(response, HttpStatus.UNAUTHORIZED, "Authentication is required");
            return;
        }

        Claims claims;
        String userType;
        String email;
        List<GrantedAuthority> authorities;

        try {
            // parse verifies the signature and expiration using the existing JwtService.
            claims = jwtService.parse(token);
            if (claims == null
                    || !"ACCESS_TOKEN".equals(claims.get("type", String.class))) {
                throw new IllegalArgumentException("Invalid access token");
            }

            userType = claims.get("userType", String.class);
            email = claims.get("email", String.class);
            if (email == null || email.isBlank()) email = claims.getSubject();

            authorities = readAuthorities(claims);
            if (userType == null || userType.isBlank()
                    || email == null || email.isBlank()
                    || authorities == null) {
                throw new IllegalArgumentException("Invalid token payload");
            }
        } catch (Exception exception) {
            log.info("JWT rejected: reason={}", exception.getClass().getSimpleName());
            reject(response, HttpStatus.UNAUTHORIZED, "Session expired or invalid. Please sign in again.");
            return;
        }

        boolean allowed = switch (userType) {
            case "ADMIN" -> routeValidator.isAdminSecured.test(request);
            case "CUSTOMER" -> routeValidator.isCustomerSecured.test(request);
            default -> false;
        };

        if (!allowed) {
            log.info("JWT route denied: userType={}, path={}", userType, path);
            reject(response, HttpStatus.FORBIDDEN, "Account Unauthorized");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                email, null, authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("JWT route allowed: userType={}, path={}, authorities={}",
                userType, path, authorities.size());

        // Do not catch downstream application exceptions or write a second response.
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;

        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private List<GrantedAuthority> readAuthorities(Claims claims) {
        Object raw = claims.get("auth");
        if (!(raw instanceof List<?> values)) return null;

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String authority) || authority.isBlank()) {
                return null;
            }
            authorities.add(new SimpleGrantedAuthority(authority));
        }
        return authorities;
    }

    private void reject(
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {
        SecurityContextHolder.clearContext();

        if (response.isCommitted()) {
            log.info("JWT response already committed; skipping error response");
            return;
        }

        response.setStatus(status.value());
        response.setContentType(APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(response.getWriter(),
                new ApiResponse<>(false, message, null));
    }
}