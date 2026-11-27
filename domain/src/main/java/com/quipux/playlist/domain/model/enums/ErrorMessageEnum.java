package com.quipux.playlist.domain.model.enums;

import java.util.Locale;

public enum ErrorMessageEnum {

    PLAYLIST_NAME_REQUIRED(ResponseCodeEnum.INVALID_REQUEST, "The playlist name is required"),
    PLAYLIST_NAME_NOT_ADDRESSABLE(ResponseCodeEnum.INVALID_REQUEST,
            "The playlist name must not be '.' or '..', nor contain '/', '\\' or control characters"),
    PLAYLIST_NAME_BLOCKED_SEQUENCE(ResponseCodeEnum.INVALID_REQUEST,
            "The playlist name must not contain '%2F', '%5C', '%00', '%0A', '%0D' or Unicode line separators"),
    FIELD_TOO_LONG(ResponseCodeEnum.INVALID_REQUEST, "The %s must not exceed %d characters"),
    FIELD_REQUIRED(ResponseCodeEnum.INVALID_REQUEST, "The %s is required"),
    NULL_SONG(ResponseCodeEnum.INVALID_REQUEST, "A song must not be null"),
    PATH_NOT_ALLOWED(ResponseCodeEnum.INVALID_REQUEST, "The request path is not allowed"),
    PLAYLIST_NOT_FOUND(ResponseCodeEnum.PLAYLIST_NOT_FOUND, "The playlist does not exist"),
    PLAYLIST_ALREADY_EXISTS(ResponseCodeEnum.PLAYLIST_ALREADY_EXISTS, "A playlist with that name already exists"),
    INVALID_CREDENTIALS(ResponseCodeEnum.INVALID_CREDENTIALS, "Invalid username or password"),
    AUTHENTICATION_REQUIRED(ResponseCodeEnum.AUTHENTICATION_REQUIRED, "Authentication is required"),
    TOKEN_EXPIRED(ResponseCodeEnum.AUTHENTICATION_REQUIRED, "The token has no expiry or has expired"),
    ACCESS_DENIED(ResponseCodeEnum.ACCESS_DENIED, "You are not allowed to perform this operation"),
    ACCESS_TOKEN_LIFETIME(ResponseCodeEnum.INVALID_SECURITY_CONFIGURATION,
            "An access token must last at least one second"),
    SIGNING_KEY_TOO_SHORT(ResponseCodeEnum.INVALID_SECURITY_CONFIGURATION,
            "The environment variable %s must hold at least %d bytes"),
    CONFIGURED_LIFETIME_TOO_SHORT(ResponseCodeEnum.INVALID_SECURITY_CONFIGURATION,
            "The environment variable %s must be at least one second"),
    DEMO_PASSWORD_UNUSABLE(ResponseCodeEnum.INVALID_SECURITY_CONFIGURATION,
            "The environment variable %s must hold a password of 1 to %d bytes"),
    UNKNOWN_GENRE(ResponseCodeEnum.INVALID_REQUEST, "A song genre is not in the genre catalog"),
    GENRE_PROVIDER_NOT_CONFIGURED(ResponseCodeEnum.GENRE_PROVIDER_UNAVAILABLE,
            "Spotify credentials are not configured"),
    GENRE_PROVIDER_ERROR_RESPONSE(ResponseCodeEnum.GENRE_PROVIDER_UNAVAILABLE,
            "Spotify answered %d to the %s request"),
    GENRE_PROVIDER_TIMEOUT(ResponseCodeEnum.GENRE_PROVIDER_UNAVAILABLE, "Spotify did not answer in time"),
    GENRE_PROVIDER_UNREACHABLE(ResponseCodeEnum.GENRE_PROVIDER_UNAVAILABLE,
            "Spotify could not be reached for the %s request"),
    GENRE_PROVIDER_UNREADABLE(ResponseCodeEnum.GENRE_PROVIDER_UNAVAILABLE,
            "Spotify answered the %s request with unreadable content"),
    GENRE_PROVIDER_NO_GENRES(ResponseCodeEnum.GENRE_PROVIDER_UNAVAILABLE, "Spotify returned no usable genres"),
    GENRE_LOCAL_FALLBACK(ResponseCodeEnum.GENRE_PROVIDER_UNAVAILABLE, "Using the local genre list: %s"),
    LOCAL_GENRES_EMPTY(ResponseCodeEnum.INVALID_GENRE_CONFIGURATION,
            "The local genre list %s must hold at least one genre"),
    GENRE_CACHE_UNAVAILABLE(ResponseCodeEnum.GENRE_CACHE_UNAVAILABLE,
            "Genre cache unavailable during %s"),
    GENRE_CACHE_TIMEOUT(ResponseCodeEnum.GENRE_CACHE_UNAVAILABLE,
            "Genre cache timed out during %s"),
    GENRE_CACHE_UNREADABLE(ResponseCodeEnum.GENRE_CACHE_UNAVAILABLE,
            "Genre cache returned unreadable content"),
    GENRE_CACHE_FALLBACK(ResponseCodeEnum.GENRE_CACHE_UNAVAILABLE,
            "Ignoring genre cache failure: %s"),
    INVALID_GENRE_CACHE_DURATION(ResponseCodeEnum.INVALID_GENRE_CONFIGURATION,
            "The genre cache %s must be greater than zero"),
    INVALID_GENRE_CACHE_KEY(ResponseCodeEnum.INVALID_GENRE_CONFIGURATION,
            "The genre cache key must not be blank"),
    RECOMMENDATION_RATE_LIMIT(ResponseCodeEnum.RECOMMENDATION_RATE_LIMITED,
            "Too many recommendation requests; try again later"),
    RECOMMENDATION_PROVIDER_DISABLED(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The AI provider is disabled"),
    RECOMMENDATION_PROVIDER_NOT_CONFIGURED(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The AI provider has no API key"),
    RECOMMENDATION_PROVIDER_TIMEOUT(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The AI provider did not answer in time"),
    RECOMMENDATION_PROVIDER_ERROR_RESPONSE(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The AI provider answered %d"),
    RECOMMENDATION_PROVIDER_UNREACHABLE(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The AI provider could not be reached"),
    RECOMMENDATION_PROVIDER_EMPTY(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The AI provider returned an empty answer"),
    RECOMMENDATION_PROVIDER_UNREADABLE(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The AI provider returned unreadable content"),
    RECOMMENDATION_PROVIDER_CIRCUIT_OPEN(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The AI provider circuit is open"),
    RECOMMENDATION_PROVIDER_TOO_MANY_CALLS(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "Too many calls to the AI provider are in progress"),
    RECOMMENDATION_NO_VALID_SUGGESTIONS(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The AI provider returned no usable suggestion"),
    RECOMMENDATION_EMPTY_PLAYLIST(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "The playlist has no songs"),
    RECOMMENDATION_FALLBACK(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "Using the default recommendations: %s"),
    INVALID_REQUEST_QUOTA(ResponseCodeEnum.INVALID_RECOMMENDATION_CONFIGURATION,
            "The requests per minute must be at least 1, not %d"),
    DEFAULT_RECOMMENDATIONS_INVALID(ResponseCodeEnum.INVALID_RECOMMENDATION_CONFIGURATION,
            "The default recommendations are not valid: %s"),
    PROMPT_UNAVAILABLE(ResponseCodeEnum.INVALID_RECOMMENDATION_CONFIGURATION,
            "The prompt %s could not be loaded"),
    CATALOG_INVALID(ResponseCodeEnum.INVALID_RECOMMENDATION_CONFIGURATION, "The song catalog is not valid: %s"),
    CATALOG_DISABLED(ResponseCodeEnum.SONG_CATALOG_UNAVAILABLE,
            "The song catalog is not used while the AI is disabled or has no API key"),
    CATALOG_NOT_CONFIGURED(ResponseCodeEnum.SONG_CATALOG_UNAVAILABLE,
            "The song catalog has no database address or password"),
    CATALOG_TIMEOUT(ResponseCodeEnum.SONG_CATALOG_UNAVAILABLE, "The song catalog did not answer in time"),
    CATALOG_EMBEDDINGS_UNAVAILABLE(ResponseCodeEnum.SONG_CATALOG_UNAVAILABLE,
            "The AI provider returned no usable embeddings"),
    CATALOG_UNREACHABLE(ResponseCodeEnum.SONG_CATALOG_UNAVAILABLE, "The song catalog database could not be used"),
    CATALOG_INDEXING_FAILED(ResponseCodeEnum.SONG_CATALOG_UNAVAILABLE, "The song catalog could not be indexed: %s"),
    RECOMMENDATION_NO_CANDIDATES(ResponseCodeEnum.SONG_CATALOG_UNAVAILABLE,
            "The song catalog has no candidates for the playlist"),
    RECOMMENDATION_SIMILARITY(ResponseCodeEnum.RECOMMENDATION_PROVIDER_UNAVAILABLE,
            "Using the most similar songs: %s"),
    UNEXPECTED_ERROR(ResponseCodeEnum.UNEXPECTED_ERROR,"An unexpected error occurred"),
    EVENT_PUBLISH_UNAVAILABLE(ResponseCodeEnum.EVENT_BROKER_UNAVAILABLE,
            "Kafka broker unavailable while publishing %s for playlist '%s'"),
    EVENT_PUBLISH_TIMEOUT(ResponseCodeEnum.EVENT_BROKER_UNAVAILABLE,
            "Kafka publish timed out for %s of playlist '%s'"),
    EVENT_SERIALIZATION_FAILED(ResponseCodeEnum.EVENT_BROKER_UNAVAILABLE,
            "Could not serialize %s event"),
    EVENT_PUBLISH_IGNORED(ResponseCodeEnum.EVENT_BROKER_UNAVAILABLE,
            "Ignoring playlist event publish failure: %s"),
    EVENT_MESSAGE_UNREADABLE(ResponseCodeEnum.EVENT_BROKER_UNAVAILABLE,
            "Ignoring unreadable playlist audit message: %s"),
    INVALID_EVENT_DURATION(ResponseCodeEnum.INVALID_EVENT_CONFIGURATION,
            "The playlist events %s must be greater than zero"),
    INVALID_EVENT_SETTING(ResponseCodeEnum.INVALID_EVENT_CONFIGURATION,
            "The playlist events %s must not be blank");

    private final ResponseCodeEnum responseCode;
    private final String template;

    ErrorMessageEnum(ResponseCodeEnum responseCode, String template) {
        this.responseCode = responseCode;
        this.template = template;
    }

    public ResponseCodeEnum responseCode() {
        return responseCode;
    }

    public String format(Object... values) {
        return values.length == 0 ? template : String.format(Locale.ROOT, template, values);
    }

    public String withCode(Object... values) {
        return responseCode.code() + ": " + format(values);
    }
}
