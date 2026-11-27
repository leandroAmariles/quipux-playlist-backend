package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.domain.model.constants.RecommendationConstraints;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The JSON schema of one request: from one to five choices, each among the offered aliases. Without it a small model
 * rarely answers anything usable. The domain still verifies the answer: a provider may ignore the schema.
 */
public final class ChoiceSchema {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String CHOICES = "recomendaciones";
    private static final String ID = "idCatalogo";
    private static final String REASON = "motivo";

    private ChoiceSchema() {
    }

    public static String of(List<String> aliases) {
        ObjectNode schema = object(CHOICES);
        ObjectNode choices = schema.putObject("properties").putObject(CHOICES);
        choices.put("type", "array")
                .put("minItems", 1)
                .put("maxItems", RecommendationConstraints.MAX_RECOMMENDATIONS);
        ObjectNode choice = object(ID, REASON);
        ObjectNode fields = choice.putObject("properties");
        aliases.forEach(string(fields, ID).putArray("enum")::add);
        string(fields, REASON);
        choices.set("items", choice);
        return MAPPER.writeValueAsString(schema);
    }

    // An object that admits its required fields and nothing else.
    private static ObjectNode object(String... required) {
        ObjectNode node = MAPPER.createObjectNode().put("type", "object").put("additionalProperties", false);
        for (String field : required) {
            node.withArray("required").add(field);
        }
        return node;
    }

    private static ObjectNode string(ObjectNode fields, String name) {
        return fields.putObject(name).put("type", "string");
    }
}
