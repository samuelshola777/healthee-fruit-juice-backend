package com.healthee.cart.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CartResponse(
        String cartId,
        String customerId,
        List<CartItemResponse> items,
        int distinctItemCount,
        int totalQuantity,
        BigDecimal subtotal,
        String currencyCode,
        LocalDateTime updatedAt
) {
}