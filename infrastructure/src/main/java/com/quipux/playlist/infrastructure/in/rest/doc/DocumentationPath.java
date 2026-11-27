package com.quipux.playlist.infrastructure.in.rest.doc;

import java.util.Arrays;

public enum DocumentationPath {

    API_DOCS("/v3/api-docs/**", true),
    SWAGGER_UI_ENTRY("/swagger-ui.html", true),
    SWAGGER_UI("/swagger-ui/**", true),
    API_DOCS_YAML("/v3/api-docs.yaml", false),
    WEBJARS("/webjars/**", false);

    private final String pattern;
    private final boolean openToEveryone;

    DocumentationPath(String pattern, boolean openToEveryone) {
        this.pattern = pattern;
        this.openToEveryone = openToEveryone;
    }

    public String pattern() {
        return pattern;
    }

    public static String[] publicPatterns() {
        return Arrays.stream(values())
                .filter(path -> path.openToEveryone)
                .map(DocumentationPath::pattern)
                .toArray(String[]::new);
    }
}
