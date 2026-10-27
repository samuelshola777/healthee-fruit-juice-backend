package com.healthee.customer.response;

public record CustomerRegistrationResponse(
        CustomerResponse customer,
        String otpExpiresAt
) {
}