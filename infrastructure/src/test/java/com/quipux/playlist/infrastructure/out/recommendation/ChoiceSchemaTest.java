package com.quipux.playlist.infrastructure.out.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ChoiceSchemaTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void allowsOnlyTheOfferedAliasesAndFromOneToFiveChoices() {
        String expected = """
                {"type":"object","additionalProperties":false,"required":["recomendaciones"],
                 "properties":{"recomendaciones":{"type":"array","minItems":1,"maxItems":5,"items":{
                   "type":"object","additionalProperties":false,"required":["idCatalogo","motivo"],
                   "properties":{"idCatalogo":{"type":"string","enum":["c1","c2"]},"motivo":{"type":"string"}}
                 }}}}""";

        assertThat(mapper.readTree(ChoiceSchema.of(List.of("c1", "c2")))).isEqualTo(mapper.readTree(expected));
    }
}
