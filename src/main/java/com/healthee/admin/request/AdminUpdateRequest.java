package com.healthee.admin.request;



import com.healthee.utils.Gender;

import java.time.LocalDate;

public record AdminUpdateRequest(
        String firstName,
        String lastName,
        String phoneNumber,
        String profilePictureUrl,
        LocalDate dateOfBirth,
        Gender gender
) {
}