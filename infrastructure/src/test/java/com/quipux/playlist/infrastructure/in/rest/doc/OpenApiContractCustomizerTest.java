package com.quipux.playlist.infrastructure.in.rest.doc;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiContractCustomizerTest {

    private static final String SCHEMA_REF = "#/components/schemas/PlaylistDto";
    private static final String JSON = "application/json";
    private static final String PROBLEM_JSON = "application/problem+json";

    private final OpenApiContractCustomizer customizer = new OpenApiContractCustomizer();

    private OpenAPI openApi;

    @BeforeEach
    void generateTheDefaultContract() {
        openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/auth/login", new PathItem().post(generated()))
                .addPathItem("/lists", new PathItem().post(generated()))
                .addPathItem("/lists/{listName}", new PathItem().get(generated()).delete(generated()))
                .addPathItem("/unknown", new PathItem().get(generated())));
        customizer.customise(openApi);
    }

    @Test
    void movesTheCreatedPlaylistTo201WithItsLocation() {
        ApiResponses responses = openApi.getPaths().get("/lists").getPost().getResponses();

        assertThat(responses).doesNotContainKey("200");
        assertThat(responses.get("201").getContent().get(JSON).getSchema().get$ref()).isEqualTo(SCHEMA_REF);
        assertThat(responses.get("201").getHeaders()).containsKey("Location");
    }

    @Test
    void answers204WithoutABodyWhenDeleting() {
        ApiResponses responses = openApi.getPaths().get("/lists/{listName}").getDelete().getResponses();

        assertThat(responses).doesNotContainKey("200");
        assertThat(responses.get("204").getContent()).isNull();
    }

    @Test
    void describesTheErrorsAsProblems() {
        ApiResponses responses = openApi.getPaths().get("/lists/{listName}").getGet().getResponses();

        assertThat(responses).containsOnlyKeys("200", "400", "401", "403", "404", "500");
        assertThat(responses.get("404").getDescription()).contains("PLAYLIST_GE_00002");
        assertThat(responses.get("404").getContent().get(PROBLEM_JSON).getSchema().get$ref())
                .isEqualTo("#/components/schemas/Problem");
        assertThat(openApi.getComponents().getSchemas()).containsKey("Problem");
    }

    @Test
    void securesEveryOperationButTheLogin() {
        assertThat(openApi.getPaths().get("/auth/login").getPost().getSecurity()).isNullOrEmpty();
        assertThat(openApi.getPaths().get("/lists").getPost().getSecurity())
                .singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    }

    @Test
    void leavesAnOperationThatIsNotListedAsGenerated() {
        Operation unknown = openApi.getPaths().get("/unknown").getGet();

        assertThat(unknown.getResponses()).containsOnlyKeys("200");
        assertThat(unknown.getSecurity()).isNull();
    }

    @Test
    void keepsTheContractWhenItIsAppliedAgain() {
        customizer.customise(openApi);

        ApiResponse created = openApi.getPaths().get("/lists").getPost().getResponses().get("201");
        assertThat(created.getContent().get(JSON).getSchema().get$ref()).isEqualTo(SCHEMA_REF);
    }

    private static Operation generated() {
        Content content = new Content().addMediaType(JSON, new MediaType().schema(new Schema<>().$ref(SCHEMA_REF)));
        return new Operation()
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().content(content)));
    }
}
