package com.healthee.admin.response;

public record AdminAuthResponse(
        String accessToken,
        String refreshToken,
        AdminResponse admin
) {
}