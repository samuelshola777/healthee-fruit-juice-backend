package com.healthee.customer.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String emailAddress,

        @NotBlank
        @Size(min = 8, max = 100)
        String password
) {
}