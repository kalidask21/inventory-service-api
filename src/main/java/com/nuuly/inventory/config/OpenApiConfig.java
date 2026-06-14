package com.nuuly.inventory.config;

// import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
// OAuth2 security scheme imports — disabled
// import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenApi() {
        // OAuth2 security scheme and requirement removed — no auth required.
        // To restore, add back:
        //   .components(new Components().addSecuritySchemes("oauth2",
        //       new SecurityScheme().type(SecurityScheme.Type.OAUTH2)
        //           .flows(new OAuthFlows().clientCredentials(
        //               new OAuthFlow().tokenUrl("/oauth2/token")
        //                   .scopes(new Scopes()
        //                       .addString("inventory.read", "read inventory")
        //                       .addString("inventory.write", "modify inventory"))))))
        //   .addSecurityItem(new SecurityRequirement().addList("oauth2"));
        return new OpenAPI()
                .info(new Info().title("Inventory API").version("1.0.0").description("No auth required — OAuth2 disabled"));
    }
}
