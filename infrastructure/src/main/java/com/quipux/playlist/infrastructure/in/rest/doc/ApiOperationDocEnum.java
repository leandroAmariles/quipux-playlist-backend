package com.quipux.playlist.infrastructure.in.rest.doc;

import static com.quipux.playlist.infrastructure.in.rest.doc.ApiResponseDocEnum.ACCESS_DENIED;
import static com.quipux.playlist.infrastructure.in.rest.doc.ApiResponseDocEnum.AUTHENTICATION_REQUIRED;
import static com.quipux.playlist.infrastructure.in.rest.doc.ApiResponseDocEnum.CONTENT_TOO_LARGE;
import static com.quipux.playlist.infrastructure.in.rest.doc.ApiResponseDocEnum.INVALID_CREDENTIALS;
import static com.quipux.playlist.infrastructure.in.rest.doc.ApiResponseDocEnum.INVALID_REQUEST;
import static com.quipux.playlist.infrastructure.in.rest.doc.ApiResponseDocEnum.PLAYLIST_ALREADY_EXISTS;
import static com.quipux.playlist.infrastructure.in.rest.doc.ApiResponseDocEnum.PLAYLIST_NOT_FOUND;
import static com.quipux.playlist.infrastructure.in.rest.doc.ApiResponseDocEnum.UNEXPECTED_ERROR;
import static com.quipux.playlist.infrastructure.in.rest.doc.ApiResponseDocEnum.UNSUPPORTED_MEDIA_TYPE;

import com.quipux.playlist.domain.model.enums.Role;
import com.quipux.playlist.infrastructure.in.rest.AuthController;
import com.quipux.playlist.infrastructure.in.rest.GenreController;
import com.quipux.playlist.infrastructure.in.rest.PlaylistController;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

public enum ApiOperationDocEnum {

    LOGIN(HttpMethod.POST, AuthController.LOGIN_PATH, "Iniciar sesión", List.of(),
            HttpStatus.OK, "Token de acceso",
            INVALID_REQUEST, INVALID_CREDENTIALS, CONTENT_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, UNEXPECTED_ERROR),
    GET_PLAYLISTS(HttpMethod.GET, PlaylistController.PATH, "Ver todas las listas", List.of(Role.USER, Role.ADMIN),
            HttpStatus.OK, "Listas con sus canciones",
            AUTHENTICATION_REQUIRED, ACCESS_DENIED, UNEXPECTED_ERROR),
    GET_PLAYLIST(HttpMethod.GET, PlaylistController.PLAYLIST_PATH, "Ver una lista",
            List.of(Role.USER, Role.ADMIN),
            HttpStatus.OK, "La lista",
            INVALID_REQUEST, AUTHENTICATION_REQUIRED, ACCESS_DENIED, PLAYLIST_NOT_FOUND, UNEXPECTED_ERROR),
    GET_GENRES(HttpMethod.GET, GenreController.PATH, "Ver los géneros disponibles", List.of(Role.USER, Role.ADMIN),
            HttpStatus.OK, "Géneros y su origen",
            AUTHENTICATION_REQUIRED, ACCESS_DENIED, UNEXPECTED_ERROR),
    CREATE_PLAYLIST(HttpMethod.POST, PlaylistController.PATH, "Crear una lista", List.of(Role.ADMIN),
            HttpStatus.CREATED, "Lista creada",
            INVALID_REQUEST, AUTHENTICATION_REQUIRED, ACCESS_DENIED, PLAYLIST_ALREADY_EXISTS, CONTENT_TOO_LARGE,
            UNSUPPORTED_MEDIA_TYPE, UNEXPECTED_ERROR),
    DELETE_PLAYLIST(HttpMethod.DELETE, PlaylistController.PLAYLIST_PATH, "Borrar una lista", List.of(Role.ADMIN),
            HttpStatus.NO_CONTENT, "Lista borrada",
            INVALID_REQUEST, AUTHENTICATION_REQUIRED, ACCESS_DENIED, PLAYLIST_NOT_FOUND, UNEXPECTED_ERROR);

    private static final String PUBLIC_ACCESS = "Pública: no requiere token.";
    private static final String ROLE_ACCESS = "Rol: %s.";
    private static final String ROLE_SEPARATOR = " o ";

    private final HttpMethod method;
    private final String path;
    private final String summary;
    private final List<Role> roles;
    private final HttpStatus success;
    private final String successDescription;
    private final List<ApiResponseDocEnum> errors;

    ApiOperationDocEnum(
            HttpMethod method,
            String path,
            String summary,
            List<Role> roles,
            HttpStatus success,
            String successDescription,
            ApiResponseDocEnum... errors) {
        this.method = method;
        this.path = path;
        this.summary = summary;
        this.roles = roles;
        this.success = success;
        this.successDescription = successDescription;
        this.errors = List.of(errors);
    }

    public static Optional<ApiOperationDocEnum> find(HttpMethod method, String path) {
        return Arrays.stream(values())
                .filter(operation -> operation.method.equals(method) && operation.path.equals(path))
                .findFirst();
    }

    public String summary() {
        return summary;
    }

    public boolean requiresToken() {
        return !roles.isEmpty();
    }

    public String access() {
        return requiresToken()
                ? ROLE_ACCESS.formatted(roles.stream().map(Role::name).collect(Collectors.joining(ROLE_SEPARATOR)))
                : PUBLIC_ACCESS;
    }

    public HttpStatus success() {
        return success;
    }

    public String successDescription() {
        return successDescription;
    }

    public List<ApiResponseDocEnum> errors() {
        return errors;
    }
}
