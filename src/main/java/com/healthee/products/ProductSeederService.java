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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
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
        int skipped = 0;

        log.info("🍍 Starting Healthee product seeding.");

        try {
            List<Product> products = seedDefinitions();

            log.info(
                    "🍊 Seed definitions loaded | products={}",
                    products.size()
            );

            for (Product product : products) {
                if (seedProduct(product)) {
                    created++;
                } else {
                    skipped++;
                }
            }

            log.info(
                    "🍍 Product seeding completed | total={} | created={} | skipped={} "
                            + "| durationMs={} | existing products unchanged",
                    products.size(),
                    created,
                    skipped,
                    (System.nanoTime() - startedAt) / 1_000_000
            );
        } catch (RuntimeException ex) {
            log.info(
                    "❌ Product seeding failed | created={} | skipped={}",
                    created,
                    skipped,
                    ex
            );

            throw ex;
        }
    }

    private List<Product> seedDefinitions() {
        return List.of(
                product(
                        "mango-juice-50cl-norm-3195e9",
                        "Mango Juice",
                        "Freshly squeezed mango juice, no added sugar.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.NORM,
                        "3000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073785/mandate-sports/image/general/2026/10/27/kbzlzkrypsar2c7blpcu.png"
                ),
                product(
                        "mango-juice-50cl-norm-b999e4",
                        "Mango Juice",
                        "Freshly squeezed mango juice, no added sugar.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.NORM,
                        "1500.00",
                        true,
                        "https://res.cloudinary.com/healthee/video/upload/v1/products/mango-promo.mp4",
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073734/mandate-sports/image/general/2026/10/27/lx3yq4atewetmijbe6lv.png"
                ),
                product(
                        "orange-juice-25cl-norm",
                        "Orange Juice",
                        "Orange Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.NORM,
                        "1500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073572/mandate-sports/image/general/2026/10/27/oux0tzgkk3rm8rmrv8xm.png"
                ),
                product(
                        "orange-juice-50cl-norm",
                        "Orange Juice",
                        "Orange Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.NORM,
                        "3000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073637/mandate-sports/image/general/2026/10/27/wupm06xtoo8umxg3sxtg.png"
                ),
                product(
                        "watermelon-juice-25cl-norm",
                        "Watermelon Juice",
                        "Watermelon Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.NORM,
                        "1500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793019195/lv_0_20260506155053.jpg_-_Copy.jpg"
                ),
                product(
                        "watermelon-juice-50cl-norm",
                        "Watermelon Juice",
                        "Watermelon Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.NORM,
                        "3000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073581/mandate-sports/image/general/2026/10/27/afgfxcxygo10vqdsx37w.png"
                ),
                product(
                        "pineapple-juice-25cl-norm",
                        "Pineapple Juice",
                        "Pineapple Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.NORM,
                        "1500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073511/mandate-sports/image/general/2026/10/27/jqjv5qpvcqmd71joposd.png"
                ),
                product(
                        "pineapple-juice-50cl-norm",
                        "Pineapple Juice",
                        "Pineapple Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.NORM,
                        "3000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073540/mandate-sports/image/general/2026/10/27/xlhp1d4afhoblchicarv.png"
                ),
                product(
                        "norm-mix-of-2-juice-25cl-norm",
                        "Norm MIX-OF-2 Juice",
                        "Norm MIX-OF-2 Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.NORM,
                        "1500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073479/mandate-sports/image/general/2026/10/27/o34skjryr54dpq8bm2tk.png"
                ),
                product(
                        "norm-mix-of-2-juice-50cl-norm",
                        "Norm MIX-OF-2 Juice",
                        "Norm MIX-OF-2 Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.NORM,
                        "3000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073485/mandate-sports/image/general/2026/10/27/cqznfofnnnf2dahxyvpt.png"
                ),
                product(
                        "norm-mix-of-3-juice-25cl-norm",
                        "Norm MIX-OF-3 Juice",
                        "Norm MIX-OF-3 Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.NORM,
                        "1500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073397/mandate-sports/image/general/2026/10/27/tdag7v3asy3wilosylcz.png"
                ),
                product(
                        "norm-mix-of-3-juice-50cl-norm",
                        "Norm MIX-OF-3 Juice",
                        "Norm MIX-OF-3 Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.NORM,
                        "3000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073422/mandate-sports/image/general/2026/10/27/bhb3suvmb0zocvf5gruj.png"
                ),
                product(
                        "norm-exotic-mix-25cl-norm",
                        "Norm + Exotic Mix",
                        "Norm + Exotic Mix. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.NORM,
                        "2500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073347/mandate-sports/image/general/2026/10/27/t3tin3i4fu8sndb8ru5d.png"
                ),
                product(
                        "norm-exotic-mix-50cl-norm",
                        "Norm + Exotic Mix",
                        "Norm + Exotic Mix. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.NORM,
                        "5000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073365/mandate-sports/image/general/2026/10/27/devrd1leimjd71qhvnxe.png"
                ),
                product(
                        "red-apple-juice-25cl-exotic",
                        "Red Apple Juice",
                        "Red Apple Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.EXOTIC,
                        "5000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073301/mandate-sports/image/general/2026/10/27/yr8l3yvw4ihxl0u5wsci.png"
                ),
                product(
                        "red-apple-juice-50cl-exotic",
                        "Red Apple Juice",
                        "Red Apple Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.EXOTIC,
                        "9000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073304/mandate-sports/image/general/2026/10/27/uev19kzmyjnz2qtiq2rm.png"
                ),
                product(
                        "carrot-juice-25cl-exotic",
                        "Carrot Juice",
                        "Carrot Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.EXOTIC,
                        "5000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073226/mandate-sports/image/general/2026/10/27/jjy9tkxzk9pnjnnquzqo.png"
                ),
                product(
                        "carrot-juice-50cl-exotic",
                        "Carrot Juice",
                        "Carrot Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.EXOTIC,
                        "9000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073244/mandate-sports/image/general/2026/10/27/hifnpgbknue76gyzko35.png"
                ),
                product(
                        "exotic-mix-of-2-juice-25cl-exotic",
                        "Exotic MIX-OF-2 Juice",
                        "Exotic MIX-OF-2 Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.EXOTIC,
                        "3500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073174/mandate-sports/image/general/2026/10/27/s3jhzmrgoh4cfyscps3t.png"
                ),
                product(
                        "exotic-mix-of-2-juice-50cl-exotic",
                        "Exotic MIX-OF-2 Juice",
                        "Exotic MIX-OF-2 Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.EXOTIC,
                        "6000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073165/mandate-sports/image/general/2026/10/27/eut4ar4vvh83vrxvyv3x.png"
                ),
                product(
                        "exotic-mix-of-3-juice-25cl-exotic",
                        "Exotic MIX-OF-3 Juice",
                        "Exotic MIX-OF-3 Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.EXOTIC,
                        "3500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073137/mandate-sports/image/general/2026/10/27/fbgugs6cpwapu3garuq9.png"
                ),
                product(
                        "exotic-mix-of-3-juice-50cl-exotic",
                        "Exotic MIX-OF-3 Juice",
                        "Exotic MIX-OF-3 Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.EXOTIC,
                        "6000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073110/mandate-sports/image/general/2026/10/27/m7gz3vm8z10fb7eq5ipr.png"
                ),
                product(
                        "zobo-25cl-exotic",
                        "Zobo",
                        "Zobo. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.EXOTIC,
                        "500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073052/mandate-sports/image/general/2026/10/27/ffhzedyejh5omxncfyid.png"
                ),
                product(
                        "zobo-50cl-exotic",
                        "Zobo",
                        "Zobo. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.EXOTIC,
                        "1000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073080/mandate-sports/image/general/2026/10/27/zpbjdnzmhrwycpirkgfo.png"
                ),
                product(
                        "clementine-25cl-exotic",
                        "Clementine",
                        "Clementine. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.EXOTIC,
                        "7500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793073033/mandate-sports/image/general/2026/10/27/rwlryvwqflrkpfcq8gzb.png"
                ),
                product(
                        "clementine-50cl-exotic",
                        "Clementine",
                        "Clementine. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.EXOTIC,
                        "15000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793072995/mandate-sports/image/general/2026/10/27/rpnqqoxyo4liqvee7nsv.png"
                ),
                product(
                        "grape-juice-25cl-exotic",
                        "Grape Juice",
                        "Grape Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.EXOTIC,
                        "10000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793072835/mandate-sports/image/general/2026/10/27/dandi5ot3u08zyqwvpai.png"
                ),
                product(
                        "grape-juice-50cl-exotic",
                        "Grape Juice",
                        "Grape Juice. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.EXOTIC,
                        "20000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793072874/mandate-sports/image/general/2026/10/27/dpobq6dkarwy2kpaigtl.png"
                ),
                product(
                        "blueberry-25cl-exotic",
                        "Blueberry",
                        "Blueberry. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_25,
                        ProductCategoriesType.EXOTIC,
                        "20000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793072968/mandate-sports/image/general/2026/10/27/ujeteucjdkiq7zqmvogi.png"
                ),
                product(
                        "blueberry-50cl-exotic",
                        "Blueberry",
                        "Blueberry. Natural juice without processed sugar or preservatives.",
                        ProductBottleSize.CL_50,
                        ProductCategoriesType.EXOTIC,
                        "40000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793072950/mandate-sports/image/general/2026/10/27/tfnjhdtgenewtgj1zhmv.png"
                ),
                product(
                        "wings-nosize-foods",
                        "Wings",
                        "Wings, 6 pieces.",
                        null,
                        ProductCategoriesType.FOODS,
                        "5000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793066692/mandate-sports/image/general/2026/10/27/yincgguc2bl8aqzhckke.png"
                ),
                product(
                        "cake-nosize-foods",
                        "Cake",
                        "Cake.",
                        null,
                        ProductCategoriesType.FOODS,
                        "3000.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793066724/mandate-sports/image/general/2026/10/27/hmut1yicqm0opyh5bssr.png"
                ),
                product(
                        "mini-meatpie-nosize-foods",
                        "Mini-meatpie",
                        "Mini-meatpie.",
                        null,
                        ProductCategoriesType.FOODS,
                        "500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793066667/mandate-sports/image/general/2026/10/27/nbbmxe4up1uilo2zc50q.png"
                ),
                product(
                        "mini-sausage-rolls-nosize-foods",
                        "Mini-sausage rolls",
                        "Mini-sausage rolls.",
                        null,
                        ProductCategoriesType.FOODS,
                        "500.00",
                        true,
                        null,
                        "https://res.cloudinary.com/xaae5cze/image/upload/v1793066633/mandate-sports/image/general/2026/10/27/niu2eyg2h5wxcaxeo6rq.png"
                )
        );
    }

    private Product product(
            String productId,
            String name,
            String description,
            ProductBottleSize size,
            ProductCategoriesType category,
            String price,
            boolean visible,
            String videoUrl,
            String... imageUrls
    ) {
        Product product = new Product();

        // Preserve exported business IDs; use deterministic UUIDs for new documents.
        // Exported MongoDB _id, audit timestamps and revision are not restored.
        product.setId(
                UUID.nameUUIDFromBytes(
                        ("healthee:product:" + productId)
                                .getBytes(StandardCharsets.UTF_8)
                ).toString()
        );

        product.setProductId(productId);
        product.setProductName(name);
        product.setProductDescription(description);
        product.setProductBottleSize(size);
        product.setProductCategoriesType(category);
        product.setProductPrice(new BigDecimal(price).setScale(2));
        product.setAvailableForPublicDisplay(visible);
        product.setDisplayVideoUrl(videoUrl);
        product.setProductThumbnailsUrls(
                new LinkedHashSet<>(Arrays.asList(imageUrls))
        );

        return product;
    }

    private boolean seedProduct(Product product) {
        String sizeLabel = product.getProductBottleSize() == null
                ? "Per serving"
                : product.getProductBottleSize().getValue();

        log.info(
                "🍏 Checking product | name={} | size={} | productId={}",
                product.getProductName(),
                sizeLabel,
                product.getProductId()
        );

        Criteria sameProduct = Criteria.where("productName")
                .regex(Pattern.compile(
                        "^" + Pattern.quote(product.getProductName()) + "$",
                        Pattern.CASE_INSENSITIVE
                ))
                .and("productBottleSize").is(product.getProductBottleSize())
                .and("productCategoriesType").is(product.getProductCategoriesType());

        Query existingQuery = Query.query(
                new Criteria().orOperator(
                        Criteria.where("productId").is(product.getProductId()),
                        sameProduct
                )
        );

        if (mongoTemplate.exists(existingQuery, Product.class)) {
            log.info(
                    "🍃 Existing product skipped | productId={}",
                    product.getProductId()
            );

            return false;
        }

        try {
            log.info(
                    "🍇 Inserting product | productId={} | price={} NGN | images={}",
                    product.getProductId(),
                    product.getProductPrice(),
                    product.getProductThumbnailsUrls().size()
            );

            mongoTemplate.insert(product);

            log.info(
                    "🍉 Product created | productId={}",
                    product.getProductId()
            );

            return true;
        } catch (DuplicateKeyException ex) {
            // Concurrent seeders use the same deterministic UUID for this product.
            if (mongoTemplate.exists(existingQuery, Product.class)) {
                log.info(
                        "🍃 Product inserted by another instance; skipped | productId={}",
                        product.getProductId()
                );

                return false;
            }

            throw ex;
        }
    }
}