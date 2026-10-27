package com.healthee.products.request;

import com.healthee.products.ProductBottleSize;
import com.healthee.products.ProductCategoriesType;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ProductFilterRequest(
        String search,
        ProductCategoriesType productCategoriesType,
        ProductBottleSize productBottleSize,
        Boolean availableForPublicDisplay,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}