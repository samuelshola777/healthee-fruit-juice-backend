package com.healthee.userauthmgt.user.response;

import lombok.Builder;

@Builder
public record UserForgotPasswordResponse(

        String userId
) {
}
