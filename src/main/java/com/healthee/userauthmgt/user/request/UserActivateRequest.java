package com.healthee.userauthmgt.user.request;

public record UserActivateRequest(

        String otp,
        String userId
) {
}
