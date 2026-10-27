package com.healthee.utils.appsecurity;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI serviceAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Healthee API")
                        .version("v1")
                        .description(
                                "Use Try it out to execute requests. "
                                        + "Public endpoints need no credentials. "
                                        + "For private endpoints, log in through "
                                        + "/api/v1/admin/public/login, then enter the "
                                        + "returned accessToken and your API_KEY in Authorize."
                        ))
                .components(new Components()
                        .addSecuritySchemes(
                                "BearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Paste the accessToken only; "
                                                        + "Swagger adds the Bearer prefix."
                                        )
                        )
                        .addSecuritySchemes(
                                "ApiKeyAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-API-Key")
                                        .description(
                                                "Enter the API_KEY configured in env.properties."
                                        )
                        ))
                .addSecurityItem(privateSecurity());
    }

    @Bean
    public OpenApiCustomizer endpointSecurity() {
        return api -> {
            if (api.getPaths() == null) {
                return;
            }

            api.getPaths().forEach((path, item) ->
                    item.readOperations().forEach(operation -> {
                        boolean isPublic = path.endsWith("/public")
                                || path.contains("/public/");

                        operation.setSecurity(
                                isPublic
                                        ? List.of()
                                        : List.of(privateSecurity())
                        );

                        if ("/api/v1/admin/public/login".equals(path)
                                && operation.getRequestBody() != null
                                && operation.getRequestBody().getContent() != null) {

                            var json = operation.getRequestBody()
                                    .getContent()
                                    .get("application/json");

                            if (json != null) {
                                json.setExample(Map.of(
                                        "emailAddress", "admin@example.com",
                                        "password", "your-admin-password"
                                ));
                            }
                        }
                    })
            );
        };
    }

    private static SecurityRequirement privateSecurity() {
        // Both schemes in one requirement means both headers are required.
        return new SecurityRequirement()
                .addList("BearerAuth")
                .addList("ApiKeyAuth");
    }
}