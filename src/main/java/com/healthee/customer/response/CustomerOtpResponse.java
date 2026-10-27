package com.healthee.customer.response;

public record CustomerOtpResponse(
        String userId,
        String emailAddress,
        String otpExpiresAt
) {
}