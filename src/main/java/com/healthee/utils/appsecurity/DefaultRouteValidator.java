package com.healthee.utils.appsecurity;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
public class DefaultRouteValidator {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultRouteValidator.class);

    private static Pattern route(String prefix) {
        return Pattern.compile("^" + Pattern.quote(prefix) + "(?:/.*)?$");
    }

    private static List<Pattern> routes(String... prefixes) {
        return Arrays.stream(prefixes)
                .distinct()
                .map(DefaultRouteValidator::route)
                .toList();
    }

    public static final List<Pattern> ADMIN_ENDPOINTS = routes(
            "/api/v1/product/private",
            "/api/v1/admin/private",
            "/api/v1/customer/private/admin",
            "/api/v1/files/private",
            "/api/v1/teams/private",
            "/api/v1/players/private",
            "/api/v1/player-stats/private",
            "/api/v1/gallery/private",
            "/api/v1/posts/private",
            "/api/v1/competitions/private",
            "/api/v1/competitions/private/admin",
            "/api/v1/activity-logs",
            "/api/v1/activity-logs/private",
            "/api/v1/activity-logs/private/admin",
            "/api/v1/dashboard/private",
            "/api/v1/dashboard/private/admin",
            "/api/v1/dashboard"
    );

    // Keep this list separate so customers cannot reach private/admin routes.
    public static final List<Pattern> CUSTOMER_ENDPOINTS = routes(
            "/api/v1/customer/private/profile",
            "/api/v1/customer/private/change-password",
            "/api/v1/cart/private"
    );

    public final Predicate<HttpServletRequest> isAdminSecured = request ->
            matches(request, ADMIN_ENDPOINTS, "ADMIN");

    public final Predicate<HttpServletRequest> isCustomerSecured = request ->
            matches(request, CUSTOMER_ENDPOINTS, "CUSTOMER");

    public DefaultRouteValidator() {
        log.info("Route validator ready: adminPatterns={}, customerPatterns={}",
                ADMIN_ENDPOINTS.size(), CUSTOMER_ENDPOINTS.size());
    }

    private boolean matches(
            HttpServletRequest request,
            List<Pattern> patterns,
            String group
    ) {
        String path = pathWithinApplication(request);
        boolean allowed = patterns.stream()
                .anyMatch(pattern -> pattern.matcher(path).matches());

        log.info("Route access: group={}, method={}, path={}, allowed={}",
                group, request.getMethod(), path, allowed);
        return allowed;
    }

    public static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) return "/";

        String context = request.getContextPath();
        if (context != null && !context.isEmpty()) {
            if (uri.equals(context)) return "/";
            if (uri.startsWith(context + "/")) {
                return uri.substring(context.length());
            }
        }
        return uri;
    }
}