package com.quipux.playlist.domain.model;

/**
 * A suggested song. As it arrives from a provider any field may be missing; only validated ones are delivered.
 */
public record Recommendation(String title, String artist, String genre, String reason) {
}
