package com.healthee.products;

import com.healthee.utils.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document("products")
public class Product extends BaseEntity {

    private String productId;
    private String productName;
    private String productDescription;
    private boolean availableForPublicDisplay;
    private Set<String> productThumbnailsUrls;
    private String displayVideoUrl;
    private ProductBottleSize productBottleSize;
    private ProductCategoriesType productCategoriesType;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal productPrice;

    private long revision;
}