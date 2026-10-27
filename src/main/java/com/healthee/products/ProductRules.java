package com.healthee.products;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Locale;

public final class ProductRules {
    private ProductRules() {}
    public static String name(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 150)
            throw new IllegalArgumentException("Product name is required and must not exceed 150 characters");
        return value.trim();
    }
    public static String description(String value) {
        String result = value == null ? "" : value.trim();
        if (result.length() > 2000) throw new IllegalArgumentException("Description must not exceed 2000 characters");
        return result;
    }
    public static BigDecimal price(BigDecimal value, boolean allowZero) {
        if (value == null || value.signum() < 0 || (!allowZero && value.signum() == 0)
                || value.compareTo(new BigDecimal("9999999999.99")) > 0
                || value.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Price must be positive, at most 9999999999.99, with at most two decimal places");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
    public static String media(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        try {
            URI uri = URI.create(result);
            if (result.length() > 2048 || uri.getHost() == null || uri.getUserInfo() != null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Media must be an absolute HTTP or HTTPS URL without credentials");
        }
        return result;
    }
    public static String businessId(String value, String size, String category) {
        String clean = name(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (clean.isEmpty()) throw new IllegalArgumentException("Name must contain a letter or number");
        return clean + "-" + (size == null ? "nosize" : size.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""))
                + "-" + category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}