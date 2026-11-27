# Auditoría de código: HU-02

Registro de los comentarios de revisión dejados en el código durante la implementación de HU-02, con el código antes y
después de atenderlos.

- **Fecha de la revisión**: 2026-11-26
- **Rama**: `feature/02-playlist-management`
- **Revisor**: propietario del repositorio, directamente sobre los archivos en el IDE
- **Cambios aplicados por**: Claude Code
- **Commits con el código resultante**: `7d5a2a8` (comentario 1) y `fd95aa6` (comentario 2)

## Resumen

| # | Archivo | Comentario | Estado |
|---|---|---|---|
| 1 | `domain/.../model/Playlist.java` | Mover a un enum los valores `.` y `..` | Atendido |
| 2 | `infrastructure/.../in/rest/PlaylistController.java` | Mover `"/"` a una variable estática | Atendido |

### Origen de la evidencia

- Los comentarios nunca se confirmaron en Git, así que **el historial de commits no contiene el código "antes"**.
- El código "antes" de este documento se recuperó de la caché local de archivos de IntelliJ y se copia tal cual.
- En esa caché, en los objetos de Git y en los archivos actuales no se encontraron más comentarios de revisión. Un
  comentario escrito y no guardado no dejaría rastro.

---

## 1. Valores fijos en la validación del nombre

**Archivo**: `domain/src/main/java/com/quipux/playlist/domain/model/Playlist.java`, línea 23 de la versión revisada.

**Comentario** (literal):

```java
// Envia los valores a un enum, no dejes esos valores como "." o ".." hardcodeados
```

### Antes

```java
        FieldRules.requireMaxLength(name, NAME_MAX_LENGTH, "playlist name");
        // Envia los valores a un enum, no dejes esos valores como "." o ".." hardcodeados
        boolean isDotSegment = ".".equals(name) || "..".equals(name);
        if (isDotSegment || name.chars().anyMatch(Playlist::isForbiddenInName)) {
            throw new InvalidPlaylistException(
                    "The playlist name must not be '.' or '..', nor contain '/', '\\' or control characters");
        }
    }

    private static boolean isForbiddenInName(int character) {
        return character == '/' || character == '\\' || Character.isISOControl(character);
    }
```

### Después

```java
        FieldRules.requireMaxLength(name, NAME_MAX_LENGTH, "playlist name");
        if (ReservedPlaylistName.isReserved(name) || name.chars().anyMatch(Playlist::isForbiddenInName)) {
            throw new InvalidPlaylistException(
                    "The playlist name must not be '.' or '..', nor contain '/', '\\' or control characters");
        }
    }

    private static boolean isForbiddenInName(int character) {
        return ForbiddenNameCharacter.isForbidden(character) || Character.isISOControl(character);
    }
```

Archivo nuevo `domain/src/main/java/com/quipux/playlist/domain/model/enums/ReservedPlaylistName.java`:

```java
package com.quipux.playlist.domain.model.enums;

import java.util.Arrays;

public enum ReservedPlaylistName {

    CURRENT_DIRECTORY("."),
    PARENT_DIRECTORY("..");

    private final String value;

    ReservedPlaylistName(String value) {
        this.value = value;
    }

    public static boolean isReserved(String name) {
        return Arrays.stream(values()).anyMatch(reserved -> reserved.value.equals(name));
    }
}
```

Archivo nuevo `domain/src/main/java/com/quipux/playlist/domain/model/enums/ForbiddenNameCharacter.java`:

```java
package com.quipux.playlist.domain.model.enums;

import java.util.Arrays;

public enum ForbiddenNameCharacter {

    SLASH('/'),
    BACKSLASH('\\');

    private final char value;

    ForbiddenNameCharacter(char value) {
        this.value = value;
    }

    public static boolean isForbidden(int character) {
        return Arrays.stream(values()).anyMatch(forbidden -> forbidden.value == character);
    }
}
```

**Alcance**: el comentario cita `.` y `..` "como" ejemplo, así que también se movieron `/` y `\`, que estaban igual de
fijos.

**Sin cambio de comportamiento**. Lo cubre `PlaylistTest.rejectsNameThatCannotBeUsedInAUri` y
`PlaylistTest.keepsAcceptedNameExactlyAsSent`.

**Queda igual**: el mensaje de error sigue nombrando los valores en su texto, y los caracteres de control se detectan
con `Character.isISOControl`.

---

## 2. Separador de ruta fijo en el controlador

**Archivo**: `infrastructure/src/main/java/com/quipux/playlist/infrastructure/in/rest/PlaylistController.java`, línea 63
de la versión revisada.

**Comentario** (literal):

```java
// Pasa esto a una variable estatica "/"
```

### Antes

```java
    static final String PATH = "/lists";
```

```java
    @GetMapping("/{listName}")
    public Mono<PlaylistDto> getByName(@PathVariable("listName") String listName) {
        return getPlaylist.getByName(listName).map(mapper::toDto);
    }

    private static URI locationOf(String name) {
        // Pasa esto a una variable estatica "/"
        return URI.create(PATH + "/" + UriUtils.encode(name, StandardCharsets.UTF_8));
    }
```

### Después

```java
    static final String PATH = "/lists";
    private static final String PATH_SEPARATOR = "/";
    private static final String ITEM_PATH = PATH_SEPARATOR + "{listName}";
```

```java
    @GetMapping(ITEM_PATH)
    public Mono<PlaylistDto> getByName(@PathVariable("listName") String listName) {
        return getPlaylist.getByName(listName).map(mapper::toDto);
    }

    @DeleteMapping(ITEM_PATH)
    public Mono<ResponseEntity<Void>> delete(@PathVariable("listName") String listName) {
        return deletePlaylist.delete(listName)
                .thenReturn(ResponseEntity.noContent().build());
    }

    private static URI locationOf(String name) {
        return URI.create(PATH + PATH_SEPARATOR + UriUtils.encode(name, StandardCharsets.UTF_8));
    }
```

El método `delete` no es parte de este comentario: ver el hallazgo A.

**Alcance**: además de `locationOf`, la ruta `/{listName}` de las anotaciones se unificó en `ITEM_PATH`.

**Sin cambio de comportamiento**. Lo cubren `PlaylistControllerCreateTest`, `PlaylistApiGetTest` y
`PlaylistApiDeleteTest`.

**Queda igual**: `"/lists"` conserva su `/` dentro del literal y `"listName"` sigue escrito en `@PathVariable`.

---

## Otros hallazgos de la sesión

No son comentarios de revisión, pero afectan a la trazabilidad.

### A. Pérdida del borrado por un guardado desde una versión anterior

Al guardar desde el IDE una versión anterior de `PlaylistController.java` y `PlaylistPersistenceAdapter.java`,
desaparecieron el endpoint `DELETE /lists/{listName}`, su dependencia `DeletePlaylistPort` y el método `deleteByName`.
El proyecto dejó de compilar. Con la aprobación del revisor se restauraron ambos, sin reponer los comentarios.

La versión "antes" del hallazgo 2 es la guardada en ese momento, por eso no tiene `delete`.

### B. Comentarios eliminados por el revisor

Se eliminaron en el IDE comentarios de documentación escritos por Claude Code, y no se repusieron:

- `domain`: `Playlist`, `Song`, `BusinessException`, `ResponseCodeEnum`, `PlaylistConstraints`.
- `application`: `PlaylistRepositoryPort`.
- `infrastructure`: `PlaylistController`, `PlaylistDto`, `JacksonStrictTypesConfig`, `GlobalExceptionHandler`,
  `PlaylistPersistenceAdapter`, `PlaylistEntityMapper`, `PlaylistR2dbcRepository`, `PlaylistSongRow`.
- `bootstrap`: `UseCaseConfig`.
- Pruebas: `TestApplication`, `PersistenceTestSupport`, `ApiTestSupport`, `SpecialNames`.

---

## Verificación del resultado

`./mvnw clean verify` sobre el código final: 153 pruebas, 0 fallos, con Checkstyle, reglas de arquitectura y cobertura
mínima del 80 % por módulo.
