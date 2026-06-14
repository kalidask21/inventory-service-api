package com.nuuly.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Inventory API")
                        .version("1.0.0")
                        .description("OAuth2 client-credentials secured. Get a token from /oauth2/token first."))
                .components(new Components().addSecuritySchemes("oauth2",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows().clientCredentials(
                                        new OAuthFlow()
                                                .tokenUrl("/oauth2/token")
                                                .scopes(new Scopes()
                                                        .addString("inventory.read", "Read inventory levels")
                                                        .addString("inventory.write", "Add stock and process purchases"))))))
                .addSecurityItem(new SecurityRequirement().addList("oauth2"));
    }
}
