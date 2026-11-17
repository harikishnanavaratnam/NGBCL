package lk.ngbcl.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document at /v3/api-docs — the frontend generates its types from it (pnpm gen:api). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ngbclOpenApi() {
        return new OpenAPI()
                .info(new Info().title("NGBCL Platform API").version("0.1.0")
                        .description("Next Gen Badminton Champions League 1.0 — tournament management"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
