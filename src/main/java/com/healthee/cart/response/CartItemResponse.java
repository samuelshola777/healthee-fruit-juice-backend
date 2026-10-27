package com.healthee.cart.response;

import com.healthee.products.ProductBottleSize;

import java.math.BigDecimal;
import java.util.Set;

public record CartItemResponse(
        String cartItemId,
        String productId,
        Set<String> productThumbnailsUrls,
        String title,
        boolean available,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        ProductBottleSize productBottleSize
) {
}