package com.healthee.admin.request;


import com.healthee.utils.Gender;

public record AdminCreateRequest(
        String firstName,
        String lastName,
        String emailAddress,
        String password,
        String phoneNumber,
        String profilePictureUrl,
        String profileColourCode,
        Gender gender
) {
}