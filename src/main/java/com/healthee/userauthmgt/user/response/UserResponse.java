package com.healthee.userauthmgt.user.response;
import com.healthee.userauthmgt.user.UserType;
import com.healthee.utils.Gender;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record UserResponse(
        String userId,
        String firstName,
        String lastName,
        String emailAddress,
        String phoneNumber,
        UserType userType,
        String profilePictureUrl,
        LocalDate dateOfBirth,
        boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean isVerified,
        String age,
        Gender gender

        
) {
}
