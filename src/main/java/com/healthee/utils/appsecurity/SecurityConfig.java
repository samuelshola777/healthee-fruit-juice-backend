package com.healthee.utils.appsecurity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log =
            LoggerFactory.getLogger(SecurityConfig.class);

    private final ApiSecurityFilter apiSecurityFilter;
    private final RestAuthEntryPoint authEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            ApiSecurityFilter apiSecurityFilter,
            RestAuthEntryPoint authEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        log.info("SecurityConfig constructor initialized");

        this.apiSecurityFilter = apiSecurityFilter;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        log.info("Initializing SecurityFilterChain");

        http
                .csrf(csrf -> {
                    csrf.disable();
                    log.debug("CSRF disabled");
                })
                .cors(cors -> {
                    cors.configurationSource(
                            corsConfigurationSource()
                    );
                    log.debug("CORS configuration source set");
                })
                .sessionManagement(sm -> {
                    sm.sessionCreationPolicy(
                            SessionCreationPolicy.STATELESS
                    );
                    log.debug("Session management set to stateless");
                })
                .exceptionHandling(eh -> {
                    eh.authenticationEntryPoint(authEntryPoint)
                            .accessDeniedHandler(
                                    accessDeniedHandler
                            );

                    log.debug("Exception handlers configured");
                })
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                                    HttpMethod.OPTIONS,
                                    "/**"
                            )
                            .permitAll()
                            .requestMatchers(
                                    "/api/v1/market-place/public/**",
                                    "/magik/public/**",
                                    "/api/v1/utilities/public/**",
                                    "/api/v1/utilities/public",
                                    "/swagger-ui.html",
                                    "/swagger-ui/**",
                                    "/api-docs/**",
                                    "/v3/api-docs/**",
                                    "/v3/api-docs",
                                    "/v3/api-docs.yaml",
                                    "/api/v1/admin/public",
                                    "/api/v1/admin/public/**",
                                    "/api/v1/customer/public",
                                    "/api/v1/customer/public/**",
                                    "/api/v1/product/public",
                                    "/api/v1/product/public/**",
                                    "/ws",
                                    "/ws/**",
                                    "/ws-native",
                                    "/ws-native/**",
                                    "/ws-sockjs",
                                    "/ws-sockjs/**",
                                    "/api/v1/admin/auth/public/**",
                                    "/api/v1/players/public/**",
                                    "/api/v1/players/public",
                                    "/api/v1/player-stats/public/**",
                                    "/api/v1/gallery/public/**",
                                    "/api/v1/gallery/public",
                                    "/api/v1/posts/public/**",
                                    "/api/v1/posts/public",
                                    "/api/v1/system/public/**",
                                    "/api/v1/teams/public/**",
                                    "/api/v1/teams/public",
                                    "/api/v1/competitions/public/**",
                                    "/api/v1/competitions/public",
                                    "/api/v1/admin/auth/public/forgot-password",
                                    "/api/v1/admin/auth/public/reset-password"
                            )
                            .permitAll()
                            .anyRequest()
                            .authenticated();

                    log.info("Authorization rules set");
                })
                .addFilterBefore(
                        apiSecurityFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        log.info(
                "ApiSecurityFilter added before UsernamePasswordAuthenticationFilter"
        );

        SecurityFilterChain chain = http.build();

        log.info("SecurityFilterChain created successfully");

        return chain;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration cfg
    ) throws Exception {
        log.debug("AuthenticationManager bean created");
        return cfg.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration apiCors =
                new CorsConfiguration();

        apiCors.setAllowedOriginPatterns(
                Arrays.asList("*")
        );

        apiCors.setAllowedMethods(
                Arrays.asList(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS",
                        "PATCH"
                )
        );

        apiCors.setAllowedHeaders(
                Arrays.asList("*")
        );

        apiCors.setAllowCredentials(true);

        CorsConfiguration wsCors =
                new CorsConfiguration();

        wsCors.setAllowedOriginPatterns(
                Arrays.asList("*")
        );

        wsCors.setAllowedMethods(
                Arrays.asList("GET", "OPTIONS")
        );

        wsCors.setAllowedHeaders(
                Arrays.asList("*")
        );

        wsCors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src =
                new UrlBasedCorsConfigurationSource();

        src.registerCorsConfiguration(
                "/api/**",
                apiCors
        );

        src.registerCorsConfiguration(
                "/ws/**",
                wsCors
        );

        src.registerCorsConfiguration(
                "/ws-sockjs/**",
                wsCors
        );

        return src;
    }
}