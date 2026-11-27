package com.quipux.playlist.infrastructure.in.rest.doc;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class OpenApiContractCustomizer implements OpenApiCustomizer {

    private static final String SCHEME_NAME = "bearerAuth";
    private static final String SCHEME = "bearer";
    private static final String BEARER_FORMAT = "JWT";
    private static final String PROBLEM = "Problem";
    private static final String PROBLEM_REF = "#/components/schemas/" + PROBLEM;
    private static final String LOCATION_DESCRIPTION = "Dirección relativa de la lista creada";
    private static final String GENERATED_STATUS = "200";
    private static final String TEXT = "string";
    private static final String NUMBER = "integer";
    private static final String OBJECT = "object";
    private static final List<String> PROBLEM_TEXTS = List.of("type", "title", "detail", "instance", "code");
    private static final String PROBLEM_STATUS = "status";

    @Override
    public void customise(OpenAPI openApi) {
        addComponents(openApi);
        openApi.getPaths().forEach((path, item) -> item.readOperationsMap()
                .forEach((method, operation) -> ApiOperationDocEnum
                        .find(HttpMethod.valueOf(method.name()), path)
                        .ifPresent(documentation -> describe(operation, documentation))));
    }

    private static void addComponents(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        openApi.getComponents()
                .addSecuritySchemes(SCHEME_NAME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme(SCHEME)
                        .bearerFormat(BEARER_FORMAT))
                .addSchemas(PROBLEM, problemSchema());
    }

    private static Schema<?> problemSchema() {
        Schema<?> problem = typed(OBJECT);
        PROBLEM_TEXTS.forEach(field -> problem.addProperty(field, typed(TEXT)));
        problem.addProperty(PROBLEM_STATUS, typed(NUMBER));
        return problem;
    }

    private static Schema<?> typed(String type) {
        return new JsonSchema().type(type).typesItem(type);
    }

    private static void describe(Operation operation, ApiOperationDocEnum documentation) {
        operation.setSummary(documentation.summary());
        operation.setDescription(documentation.access());
        if (documentation.requiresToken()) {
            operation.setSecurity(List.of(new SecurityRequirement().addList(SCHEME_NAME)));
        }
        ApiResponses responses = new ApiResponses();
        responses.addApiResponse(codeOf(documentation.success()), successOf(operation, documentation));
        documentation.errors()
                .forEach(error -> responses.addApiResponse(codeOf(error.status()), problemOf(error)));
        operation.setResponses(responses);
    }

    private static ApiResponse successOf(Operation operation, ApiOperationDocEnum documentation) {
        ApiResponse current = operation.getResponses()
                .getOrDefault(codeOf(documentation.success()), operation.getResponses().get(GENERATED_STATUS));
        ApiResponse success = new ApiResponse().description(documentation.successDescription());
        if (documentation.success() != HttpStatus.NO_CONTENT && current != null && current.getContent() != null) {
            current.getContent().values().stream()
                    .findFirst()
                    .ifPresent(body -> success.setContent(
                            new Content().addMediaType(MediaType.APPLICATION_JSON_VALUE, body)));
        }
        if (documentation.success() == HttpStatus.CREATED) {
            success.addHeaderObject(HttpHeaders.LOCATION,
                    new Header().description(LOCATION_DESCRIPTION).schema(typed(TEXT)));
        }
        return success;
    }

    private static ApiResponse problemOf(ApiResponseDocEnum error) {
        io.swagger.v3.oas.models.media.MediaType body = new io.swagger.v3.oas.models.media.MediaType()
                .schema(new JsonSchema().$ref(PROBLEM_REF));
        return new ApiResponse()
                .description(error.description())
                .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE, body));
    }

    private static String codeOf(HttpStatus status) {
        return String.valueOf(status.value());
    }
}
