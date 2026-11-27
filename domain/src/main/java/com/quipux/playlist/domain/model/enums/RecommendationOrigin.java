package com.quipux.playlist.domain.model.enums;

public enum RecommendationOrigin {

    AI("IA"),
    SIMILARITY("SIMILITUD"),
    FALLBACK("RESPALDO");

    private final String externalValue;

    RecommendationOrigin(String externalValue) {
        this.externalValue = externalValue;
    }

    public String externalValue() {
        return externalValue;
    }
}
