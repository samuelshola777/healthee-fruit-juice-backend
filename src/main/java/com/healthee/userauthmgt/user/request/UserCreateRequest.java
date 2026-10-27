package com.healthee.userauthmgt.user.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthee.userauthmgt.user.UserType;
import com.healthee.utils.Gender;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record UserCreateRequest(

        @JsonProperty(required = true) String firstName,
        @JsonProperty(required = true) String lastName,
        @JsonProperty(required = true) String emailAddress,
        @JsonProperty(required = true) String password,
        @JsonProperty(required = true) UserType userType,
        @JsonProperty(required = true) String profileColourCode,
        String profilePictureUrl,
        LocalDate dateOfBirth,
        String phoneNumber,
        Gender gender

                ) {
}
