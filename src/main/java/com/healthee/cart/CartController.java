package com.healthee.cart;

import com.healthee.cart.request.AddCartItemRequest;
import com.healthee.cart.request.UpdateCartItemRequest;
import com.healthee.cart.response.CartResponse;
import com.healthee.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart/private")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        CartResponse cart =
                cartService.getCurrentCustomerCart();

        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Cart retrieved successfully")
                        .data(cart)
                        .build()
        );
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @Valid @RequestBody AddCartItemRequest request
    ) {
        CartResponse cart = cartService.addItem(request);

        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Book added to cart successfully")
                        .data(cart)
                        .build()
        );
    }

    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @PathVariable String cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        CartResponse cart = cartService.updateItem(
                cartItemId,
                request
        );

        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Cart item updated successfully")
                        .data(cart)
                        .build()
        );
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @PathVariable String cartItemId
    ) {
        CartResponse cart =
                cartService.removeItem(cartItemId);

        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Book removed from cart successfully")
                        .data(cart)
                        .build()
        );
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart() {
        cartService.clearCart();

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Cart cleared successfully")
                        .data(null)
                        .build()
        );
    }
}