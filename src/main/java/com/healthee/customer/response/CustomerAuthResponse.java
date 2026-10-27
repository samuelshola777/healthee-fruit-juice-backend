package com.healthee.customer.response;

public record CustomerAuthResponse(
        String accessToken,
        String refreshToken,
        CustomerResponse customer
) {
}