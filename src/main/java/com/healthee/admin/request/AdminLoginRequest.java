package com.healthee.admin.request;

public record AdminLoginRequest(
        String emailAddress,
        String password
) {
}