package com.healthee.customer.response;

public record CustomerForgotPasswordResponse(
        String userId,
        String emailAddress,
        String otpExpiresAt
) {
}