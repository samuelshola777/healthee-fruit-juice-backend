package com.healthee.utils.appsecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ApiSecurityFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(ApiSecurityFilter.class);

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Value("${app.api-key}")
    private String apiKey;

    private static final List<Pattern> PUBLIC_PATTERNS =
            Arrays.asList(
                    Pattern.compile(
                            "^/api/v1/product/public($|/.*)$"
                    ),
                    Pattern.compile(
                            "^/api/v1/customer/public(?:/.*)?$"
                    ),
                    Pattern.compile(
                            "^/api/v1/admin/public/login"
                    ),
                    Pattern.compile(
                            "^/api/v1/utilities/public($|/.*)"
                    ),
                    Pattern.compile(
                            "^/swagger-ui\\.html$"
                    ),
                    Pattern.compile(
                            "^/swagger-ui/?$"
                    ),
                    Pattern.compile(
                            "^/swagger-ui/.*$"
                    ),
                    Pattern.compile(
                            "^/v3/api-docs($|/.*)$"
                    ),
                    Pattern.compile(
                            "^/api-docs($|/.*)$"
                    ),
                    Pattern.compile(
                            "^/ws($|/.*)$"
                    ),
                    Pattern.compile(
                            "^/ws-native($|/.*)$"
                    ),
                    Pattern.compile(
                            "^/ws-sockjs($|/.*)$"
                    ),
                    Pattern.compile(
                            "^/api/v1/webhook/stripe($|/.*)$"
                    ),
                    Pattern.compile(
                            "^/api/v1/oauth/public($|/.*)$"
                    ),
                    Pattern.compile(
                            "^/api/v1/admin/public/forgot-password"
                    ),
                    Pattern.compile(
                            "^/api/v1/admin/public/reset-password"
                    )
            );

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain
    ) throws ServletException, IOException {

        log.info(
                "[M01] ENTER {} {}",
                req.getMethod(),
                req.getRequestURI()
        );

        try {
            if (isPublicEndpoint(req)) {
                log.info(
                        "[M01-PUB] Public endpoint: {}",
                        req.getRequestURI()
                );

                chain.doFilter(req, res);
                return;
            }

            validateApiKey(req);

            String token =
                    resolveBearerOrRealtimeQueryToken(req);

            if (token == null) {
                log.info(
                        "[M01-TOKEN] Missing token: {}",
                        req.getRequestURI()
                );

                sendErrorResponse(
                        res,
                        HttpStatus.UNAUTHORIZED,
                        "Missing token"
                );

                return;
            }

            if (!jwtService.isTokenValid(token)) {
                log.info(
                        "[M01-TOKEN] Invalid or expired token"
                );

                sendErrorResponse(
                        res,
                        HttpStatus.UNAUTHORIZED,
                        "Session expired or invalid please login again"
                );

                return;
            }

            Claims claims =
                    jwtService.extractAllClaims(token);

            String email = claims.getSubject();

            setUserAttributes(req, claims);

            @SuppressWarnings("unchecked")
            var authList =
                    (List<Object>) claims.get("auth");

            var authorities =
                    authList == null
                            ? List
                            .<org.springframework.security.core.authority.SimpleGrantedAuthority>of()
                            : authList.stream()
                            .map(Object::toString)
                            .map(
                                    org.springframework.security.core.authority.SimpleGrantedAuthority::new
                            )
                            .toList();

            var authentication =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            authorities
                    );

            org.springframework.security.core.context.SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

            log.info(
                    "[M01-AUTH] Security context set; authorities={}",
                    authorities.size()
            );

            chain.doFilter(req, res);

            log.info(
                    "[M01-END] Completed {}",
                    req.getRequestURI()
            );

        } catch (SecurityException se) {
            log.info(
                    "[M01-ERR] Authentication rejected"
            );

            sendErrorResponse(
                    res,
                    HttpStatus.UNAUTHORIZED,
                    se.getMessage()
            );

        } catch (Exception ex) {
            log.error(
                    "[M01-EX] Request processing failed",
                    ex
            );

            sendErrorResponse(
                    res,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal server error"
            );
        }
    }

    private static String pathWithinApplication(
            HttpServletRequest req
    ) {
        String uri = req.getRequestURI();

        if (uri == null) {
            return "/";
        }

        String ctx = req.getContextPath();

        if (
                ctx != null &&
                        !ctx.isEmpty() &&
                        uri.startsWith(ctx)
        ) {
            uri = uri.substring(ctx.length());
        }

        return uri.isEmpty() ? "/" : uri;
    }

    private static boolean isWebSocketSockJsPath(
            String path
    ) {
        if (
                path.contains("ws") ||
                        path.contains("ws-native") ||
                        path.contains("ws-sockjs")
        ) {
            return true;
        }

        return "/ws".equals(path) ||
                path.startsWith("/ws/") ||
                "/ws-native".equals(path) ||
                path.startsWith("/ws-native/") ||
                "/ws-sockjs".equals(path) ||
                path.startsWith("/ws-sockjs/");
    }

    private static boolean isRealtimeSsePath(
            String path
    ) {
        return "/api/v1/realtime/private/stream"
                .equals(path);
    }

    private String resolveBearerOrRealtimeQueryToken(
            HttpServletRequest req
    ) {
        String header = req.getHeader("Authorization");

        if (
                header != null &&
                        header.startsWith("Bearer ")
        ) {
            return header.substring(7);
        }

        String path = pathWithinApplication(req);

        if (
                isWebSocketSockJsPath(path) ||
                        isRealtimeSsePath(path)
        ) {
            String token = req.getParameter("token");

            if (
                    token != null &&
                            !token.isBlank()
            ) {
                String value = token.trim();

                return value.startsWith("Bearer ")
                        ? value.substring(7).trim()
                        : value;
            }
        }

        return null;
    }

    private boolean isPublicEndpoint(
            HttpServletRequest req
    ) {
        String path = pathWithinApplication(req);

        if (isWebSocketSockJsPath(path)) {
            log.info(
                    "[M02] WebSocket/SockJS path: {}",
                    path
            );

            return true;
        }

        boolean match = PUBLIC_PATTERNS.stream()
                .anyMatch(pattern ->
                        pattern.matcher(path).matches()
                );

        log.info(
                "[M02] path={} public={}",
                path,
                match
        );

        return match;
    }

    private void validateApiKey(
            HttpServletRequest req
    ) {
        if (isPublicEndpoint(req)) {
            return;
        }

        String provided = resolveApiKey(req);

        if (
                provided == null ||
                        provided.isBlank()
        ) {
            throw new SecurityException(
                    "Request could not be authenticated."
            );
        }

        if (!provided.equals(apiKey)) {
            throw new SecurityException(
                    "Request could not be authenticated."
            );
        }

        log.info("[M03] API key validated");
    }

    private String resolveApiKey(
            HttpServletRequest req
    ) {
        String provided = req.getHeader("X-API-Key");

        if (
                (provided == null || provided.isBlank()) &&
                        isRealtimeSsePath(
                                pathWithinApplication(req)
                        )
        ) {
            provided = req.getParameter("apiKey");
        }

        return provided;
    }

    private void validateJwtToken(
            HttpServletRequest req
    ) {
        String authHeader =
                req.getHeader("Authorization");

        if (
                authHeader == null ||
                        !authHeader.startsWith("Bearer ")
        ) {
            throw new SecurityException(
                    "Authentication is required"
            );
        }

        String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            throw new SecurityException(
                    "Request could not be authenticated."
            );
        }

        setUserAttributes(req, token);
    }

    private void setUserAttributes(
            HttpServletRequest req,
            String token
    ) {
        Claims claims =
                jwtService.extractAllClaims(token);

        if (claims == null) {
            throw new SecurityException(
                    "Invalid token payload"
            );
        }

        setUserAttributes(req, claims);
    }

    private void setUserAttributes(
            HttpServletRequest req,
            Claims claims
    ) {
        if (claims == null) {
            throw new SecurityException(
                    "Invalid token payload"
            );
        }

        req.setAttribute(
                "userId",
                claims.get("userId")
        );

        req.setAttribute(
                "email",
                claims.getSubject()
        );

        req.setAttribute(
                "userRole",
                claims.get("userRole")
        );

        req.setAttribute(
                "userType",
                claims.get("userType")
        );

        if (claims.get("clientId") != null) {
            req.setAttribute(
                    "clientId",
                    claims.get("clientId")
            );
        }

        log.info("[M05] User attributes set");
    }

    private void sendErrorResponse(
            HttpServletResponse res,
            HttpStatus status,
            String message
    ) throws IOException {
        res.setStatus(status.value());

        res.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        objectMapper.writeValue(
                res.getWriter(),
                new ApiResponse(
                        false,
                        message,
                        null
                )
        );
    }

    private String preview(String token) {
        if (token == null) {
            return "null";
        }

        int keep = Math.min(6, token.length());

        return token.substring(0, keep) +
                "...len=" +
                token.length();
    }
}