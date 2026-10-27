package com.healthee.products;

import com.healthee.products.request.ProductFilterRequest;
import com.healthee.products.request.CreateProductRequest;
import com.healthee.utils.ApiResponse;
import com.healthee.utils.PaginatedData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final com.healthee.utils.fileupload.FileUploadService fileUploadService;

    @PostMapping("/private")
    @PreAuthorize("@adminAccess.canManage(authentication)")
    public ResponseEntity<ApiResponse<Product>> createProduct(@RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Product created successfully", productService.createProduct(request)));
    }

    @GetMapping("/private")
    @PreAuthorize("@adminAccess.canManage(authentication)")
    public ResponseEntity<ApiResponse<PaginatedData>> getAllProducts(@RequestParam(name = "search", required = false) String search,
                                                                     @RequestParam(name = "page", defaultValue = "1") int page,
                                                                     @RequestParam(name = "size", defaultValue = "20") int size,
                                                                     @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
                                                                     @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Products fetched successfully", productService.getAllProducts(search, page, size, sortBy, sortDirection)));
    }

    @GetMapping("/private/{productId}")
    @PreAuthorize("@adminAccess.canManage(authentication)")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable("productId") String productId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Product fetched successfully", productService.getProductById(productId)));
    }

    @PostMapping("/private/filter")
    @PreAuthorize("@adminAccess.canManage(authentication)")
    public ResponseEntity<ApiResponse<PaginatedData>> filterProducts(@RequestBody ProductFilterRequest request,

                                                                     @RequestParam(name = "page", defaultValue = "1") int page,

                                                                     @RequestParam(name = "size", defaultValue = "20") int size,

                                                                     @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,

                                                                     @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Products filtered successfully", productService.filterProducts(request, page, size, sortBy, sortDirection)));
    }

    @PutMapping("/private/{productId}")
    @PreAuthorize("@adminAccess.canManage(authentication)")
    public ResponseEntity<ApiResponse<Product>> updateProduct(@PathVariable("productId") String productId, @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Product updated successfully", productService.updateProduct(productId, request)));
    }

    @DeleteMapping("/private/{productId}")
    @PreAuthorize("@adminAccess.canManage(authentication)")
    public ResponseEntity<Void> deleteProduct(@PathVariable("productId") String productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<PaginatedData>> getPublicProducts(@RequestParam(name = "search", required = false) String search,

                                                                        @RequestParam(name = "page", defaultValue = "1") int page,

                                                                        @RequestParam(name = "size", defaultValue = "20") int size,

                                                                        @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,

                                                                        @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Products fetched successfully", productService.getPublicProducts(search, page, size, sortBy, sortDirection)));
    }

    @GetMapping("/public/{productId}")
    public ResponseEntity<ApiResponse<Product>> getPublicProductById(@PathVariable("productId") String productId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Product fetched successfully", productService.getPublicProductById(productId)));
    }

    @PostMapping("/public/filter")
    public ResponseEntity<ApiResponse<PaginatedData>> filterPublicProducts(@RequestBody ProductFilterRequest request,

                                                                           @RequestParam(name = "page", defaultValue = "1") int page,

                                                                           @RequestParam(name = "size", defaultValue = "20") int size,

                                                                           @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,

                                                                           @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Products filtered successfully", productService.filterPublicProducts(request, page, size, sortBy, sortDirection)));
    }
    @PostMapping("/private/thumbnail/upload")
    @PreAuthorize("@adminAccess.canManage(authentication)")
    public ResponseEntity<ApiResponse<com.healthee.utils.fileupload.response.FileUploadResponse>> uploadThumbnail(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) throws java.io.IOException {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new com.healthee.utils.exception.InvalidInputException("Choose an image up to 5 MB");
        }
        byte[] header;
        try (var stream = file.getInputStream()) { header = stream.readNBytes(12); }
        String mime = file.getContentType();
        boolean jpeg = header.length >= 3 && (header[0] & 255) == 255 && (header[1] & 255) == 216 && (header[2] & 255) == 255;
        boolean png = header.length >= 8 && java.util.Arrays.equals(java.util.Arrays.copyOf(header, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
        boolean webp = header.length >= 12 && new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
        if (!(jpeg && "image/jpeg".equals(mime) || png && "image/png".equals(mime) || webp && "image/webp".equals(mime))) {
            throw new com.healthee.utils.exception.InvalidInputException("Choose a valid JPG, PNG or WebP image");
        }
        var uploaded = fileUploadService.uploadFile(file,
                com.healthee.utils.fileupload.FileUploadCategory.IMAGE,
                com.healthee.utils.fileupload.FileUsageType.GENERAL);
        if (!"image".equals(uploaded.resourceType())) {
            fileUploadService.deleteFileAsync(uploaded.fileUrl());
            throw new com.healthee.utils.exception.InvalidInputException("The uploaded file is not an image");
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Thumbnail uploaded successfully", uploaded));
    }
}