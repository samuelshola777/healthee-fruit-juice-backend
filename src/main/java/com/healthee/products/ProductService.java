package com.healthee.products;


import com.healthee.products.request.ProductFilterRequest;
import com.healthee.products.request.CreateProductRequest;
import com.healthee.utils.PaginatedData;
import com.healthee.utils.PaginationService;
import com.healthee.utils.exception.InvalidInputException;
import com.healthee.utils.exception.ResourceNotFoundException;
import com.healthee.utils.exception.ResourceExistsException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import java.nio.charset.StandardCharsets;
import com.healthee.utils.fileupload.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private static final Set<String> SORT_FIELDS = Set.of("productName", "productPrice", "createdAt", "updatedAt");

    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;
    private final FileUploadService fileUploadService;
    private final PaginationService paginationService;

    public Product createProduct(CreateProductRequest request) {
        CreateProductRequest valid = validateRequest(request);
        String businessId = generateProductId(valid);
        if (mongoTemplate.exists(Query.query(Criteria.where("productId").is(businessId)), Product.class)) {
            throw new ResourceExistsException("This product name, size and category already exist");
        }
        Product product = new Product();
        product.setId(UUID.nameUUIDFromBytes(("healthee:product:" + businessId)
                .getBytes(StandardCharsets.UTF_8)).toString());
        product.setProductId(businessId);
        applyRequest(product, valid);
        try {
            Product saved = mongoTemplate.insert(product);
            log.info("Product created: id={}", saved.getId());
            return saved;
        } catch (DuplicateKeyException exception) {
            throw new ResourceExistsException("This product already exists");
        }
    }

    public Product getProductById(String productId) {
        return productRepository.findById(requireId(productId)).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    public Product getPublicProductById(String productId) {
        Query query = Query.query(Criteria.where("id").is(requireId(productId)).and("availableForPublicDisplay").is(true));
        Product product = mongoTemplate.findOne(query, Product.class);
        if (product == null) {
            throw new ResourceNotFoundException("Product not found");
        }
        return product;
    }

    public PaginatedData getAllProducts(String search, int page, int size, String sortBy, String sortDirection) {
        ProductFilterRequest filterRequest = ProductFilterRequest.builder()
                .search(search)
                .build();
        return findProducts(filterRequest, false, page, size, sortBy, sortDirection);
    }

    public PaginatedData getPublicProducts(String search, int page, int size, String sortBy, String sortDirection) {
        ProductFilterRequest filterRequest = ProductFilterRequest.builder()
                .search(search)
                .build();

        return findProducts(filterRequest, true, page, size, sortBy, sortDirection);
    }

    public PaginatedData filterProducts(ProductFilterRequest request, int page, int size, String sortBy, String sortDirection) {
        return findProducts(request, false, page, size, sortBy, sortDirection);
    }

    public PaginatedData filterPublicProducts(ProductFilterRequest request, int page, int size, String sortBy, String sortDirection) {
        return findProducts(request, true, page, size, sortBy, sortDirection);
    }

    public Product updateProduct(String productId, CreateProductRequest request) {
        if (request == null) throw new InvalidInputException("Product request body is required");
        Product current = getProductById(productId);
        if (request.revision() != null && request.revision() != current.getRevision()) {
            throw new ResourceExistsException("This product changed. Reload it before saving again");
        }
        ProductCategoriesType category = request.productCategoriesType() == null
                ? current.getProductCategoriesType() : request.productCategoriesType();
        CreateProductRequest valid = validateRequest(CreateProductRequest.builder()
                .productName(request.productName() == null ? current.getProductName() : request.productName())
                .productDescription(request.productDescription() == null ? current.getProductDescription() : request.productDescription())
                .availableForPublicDisplay(request.availableForPublicDisplay() == null ? current.isAvailableForPublicDisplay() : request.availableForPublicDisplay())
                .productCategoriesType(category)
                .productBottleSize(category == ProductCategoriesType.FOODS ? null :
                        request.productBottleSize() == null ? current.getProductBottleSize() : request.productBottleSize())
                .productThumbnailsUrls(request.productThumbnailsUrls() == null ? new LinkedHashSet<>(copyImages(current.getProductThumbnailsUrls())) : request.productThumbnailsUrls())
                .displayVideoUrl(request.displayVideoUrl() == null ? current.getDisplayVideoUrl() : request.displayVideoUrl())
                .productPrice(request.productPrice() == null ? current.getProductPrice() : request.productPrice())
                .build());

        Criteria revision = current.getRevision() == 0
                ? new Criteria().orOperator(Criteria.where("revision").is(0), Criteria.where("revision").exists(false))
                : Criteria.where("revision").is(current.getRevision());
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("id").is(current.getId()), revision));
        Update update = new Update()
                .set("productName", valid.productName())
                .set("productDescription", valid.productDescription())
                .set("availableForPublicDisplay", valid.availableForPublicDisplay())
                .set("productBottleSize", valid.productBottleSize())
                .set("productCategoriesType", valid.productCategoriesType())
                .set("productThumbnailsUrls", new LinkedHashSet<>(valid.productThumbnailsUrls()))
                .set("displayVideoUrl", valid.displayVideoUrl())
                .set("productPrice", valid.productPrice())
                .set("updatedAt", LocalDateTime.now()).inc("revision", 1);
        Product saved = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Product.class);
        if (saved == null) throw new ResourceExistsException("This product changed. Reload it before saving again");

        Set<String> removed = copyImages(current.getProductThumbnailsUrls());
        removed.removeAll(valid.productThumbnailsUrls());
        cleanUpImages(removed, current.getId());
        log.info("Product updated: id={}, revision={}, thumbnails={}", saved.getId(), saved.getRevision(), saved.getProductThumbnailsUrls().size());
        return saved;
    }

    public void deleteProduct(String productId) {
        Product deleted = mongoTemplate.findAndRemove(Query.query(Criteria.where("id").is(requireId(productId))), Product.class);

        if (deleted == null) {
            throw new ResourceNotFoundException("Product not found");
        }

        cleanUpImages(copyImages(deleted.getProductThumbnailsUrls()), deleted.getId());
    }

    private PaginatedData findProducts(ProductFilterRequest request, boolean publicOnly, int page, int size, String sortBy, String sortDirection) {
        if (request == null) {
            throw new InvalidInputException("Filter request body is required");
        }

        Query query = new Query();

        String search = request.search() == null ? "" : request.search().trim();

        if (search.length() > 100) {
            throw new InvalidInputException("Search must not exceed 100 characters");
        }

        if (!search.isEmpty()) {
            Pattern literal = Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE);

            query.addCriteria(new Criteria().orOperator(Criteria.where("productName").regex(literal), Criteria.where("productDescription").regex(literal), Criteria.where("productId").regex(literal)));
        }

        if (request.productCategoriesType() != null) {
            query.addCriteria(Criteria.where("productCategoriesType").is(request.productCategoriesType()));
        }

        if (request.productBottleSize() != null) {
            query.addCriteria(Criteria.where("productBottleSize").is(request.productBottleSize()));
        }

        Boolean visible = publicOnly ? Boolean.TRUE : request.availableForPublicDisplay();

        if (visible != null) {
            query.addCriteria(Criteria.where("availableForPublicDisplay").is(visible));
        }

        BigDecimal min = request.minPrice() == null ? null : validatePrice(request.minPrice(), true);

        BigDecimal max = request.maxPrice() == null ? null : validatePrice(request.maxPrice(), true);

        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new InvalidInputException("Minimum price cannot exceed maximum price");
        }

        if (min != null || max != null) {
            Criteria price = Criteria.where("productPrice");

            if (min != null) {
                price.gte(min);
            }

            if (max != null) {
                price.lte(max);
            }

            query.addCriteria(price);
        }

        return paginationService.paginate(query, Product.class, page, size, sortBy, sortDirection, SORT_FIELDS);
    }

    private CreateProductRequest validateRequest(CreateProductRequest request) {
        if (request == null) throw new InvalidInputException("Product request body is required");
        try {
            if (request.productCategoriesType() == null || request.availableForPublicDisplay() == null) {
                throw new IllegalArgumentException("Product category and public display flag are required");
            }
            if (request.productCategoriesType() == ProductCategoriesType.FOODS && request.productBottleSize() != null) {
                throw new IllegalArgumentException("Food products must not have a bottle size");
            }
            if (request.productCategoriesType() != ProductCategoriesType.FOODS && request.productBottleSize() == null) {
                throw new IllegalArgumentException("Bottle size is required for juice products");
            }
            LinkedHashSet<String> images = new LinkedHashSet<>();
            for (String url : request.productThumbnailsUrls() == null ? Set.<String>of() : request.productThumbnailsUrls()) {
                String valid = ProductRules.media(url);
                if (valid == null) throw new IllegalArgumentException("Thumbnail URLs must not be blank");
                images.add(valid);
            }
            if (images.size() > 12) throw new IllegalArgumentException("A product may have up to 12 thumbnails");
            return CreateProductRequest.builder()
                    .productName(ProductRules.name(request.productName()))
                    .productDescription(ProductRules.description(request.productDescription()))
                    .availableForPublicDisplay(request.availableForPublicDisplay())
                    .productBottleSize(request.productBottleSize())
                    .productCategoriesType(request.productCategoriesType())
                    .productThumbnailsUrls(images)
                    .displayVideoUrl(ProductRules.media(request.displayVideoUrl()))
                    .productPrice(ProductRules.price(request.productPrice(), false))
                    .revision(request.revision()).build();
        } catch (IllegalArgumentException exception) {
            throw new InvalidInputException(exception.getMessage());
        }
    }

    private BigDecimal validatePrice(BigDecimal price, boolean allowZero) {
        try { return ProductRules.price(price, allowZero); }
        catch (IllegalArgumentException exception) { throw new InvalidInputException(exception.getMessage()); }
    }

    private void applyRequest(Product product, CreateProductRequest request) {
        product.setProductName(request.productName());
        product.setProductDescription(request.productDescription());
        product.setAvailableForPublicDisplay(request.availableForPublicDisplay());
        product.setProductThumbnailsUrls(new LinkedHashSet<>(request.productThumbnailsUrls()));
        product.setProductBottleSize(request.productBottleSize());
        product.setProductCategoriesType(request.productCategoriesType());
        product.setProductPrice(request.productPrice());
        product.setDisplayVideoUrl(request.displayVideoUrl());
    }

    private Set<String> copyImages(Set<String> images) {
        return images == null ? new LinkedHashSet<>() : new LinkedHashSet<>(images);
    }

    private String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("Product ID is required");
        }

        return id.trim();
    }

    private void cleanUpImages(Set<String> images, String productId) {
        for (String url : images) {
            try {
                boolean usedByProduct = mongoTemplate.exists(Query.query(Criteria.where("productThumbnailsUrls").is(url)), Product.class);

                if (!usedByProduct) {
                    fileUploadService.deleteFileAsync(url);
                }
            } catch (RuntimeException ex) {
                log.warn("Product image cleanup needs attention: productId={}, failure={}", productId, ex.getClass().getSimpleName());
            }
        }
    }


    private String generateProductId(CreateProductRequest request) {
        try {
            return ProductRules.businessId(request.productName(),
                    request.productBottleSize() == null ? null : request.productBottleSize().getValue(),
                    request.productCategoriesType().getValue());
        } catch (IllegalArgumentException exception) { throw new InvalidInputException(exception.getMessage()); }
    }

    public Product findById(String productId) {
        String id = requireId(productId);

        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

}