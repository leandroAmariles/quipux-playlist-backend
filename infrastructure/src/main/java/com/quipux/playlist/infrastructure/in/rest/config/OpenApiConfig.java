package com.quipux.playlist.infrastructure.in.rest.config;

import com.quipux.playlist.infrastructure.in.rest.doc.OpenApiContractCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String TITLE = "API de listas de reproducción";
    private static final String DESCRIPTION = "Crear, consultar y borrar listas de reproducción y sus canciones. "
            + "Inicia sesión en POST /auth/login y pega el accessToken en Authorize.";

    @Value("${playlist.openapi.version}")
    private String version;

    @Bean
    OpenAPI playlistOpenApi() {
        return new OpenAPI().info(new Info().title(TITLE).description(DESCRIPTION).version(version));
    }

    @Bean
    OpenApiCustomizer playlistContractCustomizer() {
        return new OpenApiContractCustomizer();
    }
}
