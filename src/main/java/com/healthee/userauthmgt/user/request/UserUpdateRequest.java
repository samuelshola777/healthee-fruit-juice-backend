package com.healthee.userauthmgt.user.request;
import com.healthee.utils.Gender;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record UserUpdateRequest (

        String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String phoneNumber,
      String profilePictureUrl,
      Gender gender
) {
}