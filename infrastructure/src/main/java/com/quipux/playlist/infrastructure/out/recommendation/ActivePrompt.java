package com.quipux.playlist.infrastructure.out.recommendation;

/**
 * The prompt in force and the version that names it: the one of the file, such as {@code v2}, or the one of Langfuse,
 * such as {@code v2-lf3}.
 */
public record ActivePrompt(String version, PromptTemplate template) {
}
