package com.healthee.products.request;

import com.healthee.products.ProductBottleSize;
import com.healthee.products.ProductCategoriesType;
import lombok.Builder;
import java.util.LinkedHashSet;

import java.math.BigDecimal;
@Builder
public record CreateProductRequest (

        String productName,
        String productDescription,
        Boolean availableForPublicDisplay,
        LinkedHashSet<String> productThumbnailsUrls,
        ProductBottleSize productBottleSize,
        ProductCategoriesType productCategoriesType,
        BigDecimal productPrice,
        String displayVideoUrl,
        Long revision
){}