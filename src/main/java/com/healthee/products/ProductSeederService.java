package com.healthee.products;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.seed-catalogue",
        havingValue = "true",
        matchIfMissing = true
)
public class ProductSeederService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductSeederService.class);

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void seedProducts() {
        long startedAt = System.nanoTime();
        int created = 0;

        log.info("🍍 Starting Healthee product seeding.");

        try {
            log.info("🍊 Processing NORM juices | 25cl and 50cl variants.");

            created += seedJuice("Orange Juice", ProductCategoriesType.NORM, "1500", "3000");
            created += seedJuice("Watermelon Juice", ProductCategoriesType.NORM, "1500", "3000");
            created += seedJuice("Pineapple Juice", ProductCategoriesType.NORM, "1500", "3000");
            created += seedJuice("Norm MIX-OF-2 Juice", ProductCategoriesType.NORM, "1500", "3000");
            created += seedJuice("Norm MIX-OF-3 Juice", ProductCategoriesType.NORM, "1500", "3000");
            created += seedJuice("Norm + Exotic Mix", ProductCategoriesType.NORM, "2500", "5000");

            log.info("🍎 Processing EXOTIC juices | 25cl and 50cl variants.");

            created += seedJuice("Red Apple Juice", ProductCategoriesType.EXOTIC, "5000", "9000");
            created += seedJuice("Carrot Juice", ProductCategoriesType.EXOTIC, "5000", "9000");
            created += seedJuice("Exotic MIX-OF-2 Juice", ProductCategoriesType.EXOTIC, "3500", "6000");
            created += seedJuice("Exotic MIX-OF-3 Juice", ProductCategoriesType.EXOTIC, "3500", "6000");
            created += seedJuice("Zobo", ProductCategoriesType.EXOTIC, "500", "1000");
            created += seedJuice("Clementine", ProductCategoriesType.EXOTIC, "7500", "15000");
            created += seedJuice("Grape Juice", ProductCategoriesType.EXOTIC, "10000", "20000");
            created += seedJuice("Blueberry", ProductCategoriesType.EXOTIC, "20000", "40000");

            log.info("🍰 Processing FOODS | no bottle size.");

            created += seedFood("Wings", "Wings, 6 pieces.", "5000");
            created += seedFood("Cake", "Cake.", "3000");
            created += seedFood("Mini-meatpie", "Mini-meatpie.", "500");
            created += seedFood("Mini-sausage rolls", "Mini-sausage rolls.", "500");

            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            log.info(
                    "🍍 Product seeding completed | created={} | durationMs={} "
                            + "| existing products unchanged.",
                    created,
                    elapsedMs
            );
        } catch (RuntimeException ex) {
            log.info(
                    "❌ Product seeding stopped after a failure. "
                            + "Check the product details in the preceding logs.",
                    ex
            );
            throw ex;
        }
    }

    private int seedJuice(
            String name,
            ProductCategoriesType category,
            String price25cl,
            String price50cl
    ) {
        log.info(
                "🍹 Processing juice | name={} | category={}",
                name,
                category
        );

        log.info(
                "🍋 Juice prices | name={} | 25cl={} NGN | 50cl={} NGN",
                name,
                price25cl,
                price50cl
        );

        String description = name
                + ". Natural juice without processed sugar or preservatives.";

        int created = seedProduct(
                name,
                description,
                ProductBottleSize.CL_25,
                category,
                price25cl
        ) + seedProduct(
                name,
                description,
                ProductBottleSize.CL_50,
                category,
                price50cl
        );

        log.info(
                "🍹 Juice completed | name={} | created={} | skipped={}",
                name,
                created,
                2 - created
        );

        return created;
    }

    private int seedFood(
            String name,
            String description,
            String price
    ) {
        log.info(
                "🍰 Processing food | name={} | price={} NGN",
                name,
                price
        );

        return seedProduct(
                name,
                description,
                null,
                ProductCategoriesType.FOODS,
                price
        );
    }

    private int seedProduct(
            String name,
            String description,
            ProductBottleSize size,
            ProductCategoriesType category,
            String price
    ) {
        String sizeLabel = size == null ? "N/A" : size.getValue();

        log.info(
                "🍏 Checking product | name={} | size={} | category={}",
                name,
                sizeLabel,
                category
        );

        try {
            String productId = generateProductId(name, size, category);

            log.info(
                    "🍋 Generated product ID | name={} | productId={}",
                    name,
                    productId
            );

            // Recognise products created with the previous random IDs.
            Criteria sameProduct = Criteria.where("productName")
                    .regex(Pattern.compile(
                            "^" + Pattern.quote(name) + "$",
                            Pattern.CASE_INSENSITIVE
                    ))
                    .and("productBottleSize").is(size)
                    .and("productCategoriesType").is(category);

            Query existingQuery = Query.query(
                    new Criteria().orOperator(
                            Criteria.where("productId").is(productId),
                            sameProduct
                    )
            );

            log.info(
                    "🍇 Checking MongoDB for the product ID "
                            + "or matching name, size and category."
            );

            if (mongoTemplate.exists(existingQuery, Product.class)) {
                log.info(
                        "🍃 Existing product skipped | name={} | size={} | category={}",
                        name,
                        sizeLabel,
                        category
                );

                return 0;
            }

            log.info(
                    "🍊 Product missing; preparing insert | name={} | size={}",
                    name,
                    sizeLabel
            );

            Product product = new Product();

            product.setId(
                    UUID.nameUUIDFromBytes(
                            ("healthee:product:" + productId)
                                    .getBytes(StandardCharsets.UTF_8)
                    ).toString()
            );

            product.setProductId(productId);
            product.setProductName(name);
            product.setProductDescription(description);
            product.setAvailableForPublicDisplay(true);
            product.setProductBottleSize(size);
            product.setProductCategoriesType(category);
            product.setProductPrice(new BigDecimal(price).setScale(2));

            // Update media on the saved product after uploading to Cloudinary.
            product.setProductThumbnailsUrls(new LinkedHashSet<>());
            product.setDisplayVideoUrl(null);

            log.info(
                    "🍇 Product prepared | id={} | productId={} | price={} NGN "
                            + "| images=0 | video=empty",
                    product.getId(),
                    productId,
                    product.getProductPrice()
            );

            log.info(
                    "🍍 Inserting product | productId={}",
                    productId
            );

            try {
                Product savedProduct = mongoTemplate.insert(product);

                log.info(
                        "🍉 Product created | name={} | size={} | productId={} | id={}",
                        name,
                        sizeLabel,
                        savedProduct.getProductId(),
                        savedProduct.getId()
                );

                return 1;
            } catch (DuplicateKeyException ex) {
                log.info(
                        "🍋 Duplicate key detected; checking whether another "
                                + "instance seeded productId={}",
                        productId
                );

                Query seededQuery = Query.query(
                        Criteria.where("id").is(product.getId())
                                .and("productId").is(productId)
                );

                if (mongoTemplate.exists(seededQuery, Product.class)) {
                    log.info(
                            "🍃 Product already inserted by another instance; "
                                    + "skipped | productId={}",
                            productId
                    );

                    return 0;
                }

                throw ex;
            }
        } catch (RuntimeException ex) {
            log.info(
                    "❌ Product seeding failed | name={} | size={} | category={}",
                    name,
                    sizeLabel,
                    category,
                    ex
            );

            throw ex;
        }
    }

    private String generateProductId(
            String name,
            ProductBottleSize size,
            ProductCategoriesType category
    ) {
        String cleanName = name.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        String cleanSize = size == null
                ? "nosize"
                : size.getValue()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");

        String cleanCategory = category.getValue()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");

        return String.format(
                "%s-%s-%s",
                cleanName,
                cleanSize,
                cleanCategory
        );
    }
}