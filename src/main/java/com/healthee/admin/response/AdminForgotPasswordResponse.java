package com.healthee.admin.response;

public record AdminForgotPasswordResponse(
        String userId,
        String emailAddress,
        String otp,
        String expiresAt
) {
}