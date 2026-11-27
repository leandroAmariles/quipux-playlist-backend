# Code Review — 03-authentication-authorization

> Resumen de observaciones y comentarios en el código implementado para la especificación.

## Resumen

- **Especificación / Cambio**: `03-authentication-authorization` (HU-03)
- **Fecha**: 2026-11-26
- **Revisor**: propietario del repositorio, directamente sobre los archivos en el IDE
- **Total comentarios**: 6
- **Pendientes**: 0
- **Resueltos**: 6

La línea es la que ocupaba el comentario cuando se detectó; el comentario se quitó al resolverlo.

| # | Archivo | Línea | Comentario | Estado |
|---|---|---|---|---|
| 1 | `domain/src/main/java/com/quipux/playlist/domain/model/Credentials.java` | 11 | Enum o static, no uses string hardcodeados | Resuelto |
| 2 | `domain/src/main/java/com/quipux/playlist/domain/model/AccessToken.java` | 10 | Maneja los mensajes en un enum por codigo de error como AUTH_ERROR_001 | Resuelto |
| 3 | `domain/src/main/java/com/quipux/playlist/domain/model/Playlist.java` | 31 | Estos errores se deben mapear desde un enum | Resuelto |
| 4 | `infrastructure/src/main/java/com/quipux/playlist/infrastructure/security/config/JwtConfig.java` | 17 | Usa un parametro y para la inyeccion del value por parametro, no lo hagas directamente en el metodo | Resuelto |
| 5 | `infrastructure/src/main/java/com/quipux/playlist/infrastructure/out/security/JwtTokenIssuerAdapter.java` | 28 | El mismo del punto 4 | Resuelto |
| 6 | `bootstrap/src/test/java/com/quipux/playlist/SpecialNames.java` | 11 | Esta lista desde el properties por favor | Resuelto |

## Decisiones tomadas con el revisor

Varios comentarios admitían más de una lectura y se preguntó antes de cambiar nada:

| Pregunta | Respuesta |
|---|---|
| ¿Qué mensajes entran en el enum? | Todo el proyecto, incluidos los de HU-02 |
| ¿Cambia el campo `code` de los errores de autenticación? | Sí: la API devuelve `AUTH_ERROR_00X` |
| ¿Cómo se inyectan los `@Value`? | En un atributo de la clase, no en parámetros de métodos |

---

## Hallazgos detallados

### 1. `domain/.../model/Credentials.java` (Línea 11)

**Comentario:**
> // Enum o static, no uses string hardcodeados

**Código Antes (Before):**
```java
public Credentials {
    // Enum o static, no uses string hardcodeados
    requireText(username, "username");
    requireText(password, "password");
}

private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
        throw new InvalidLoginRequestException("The " + field + " is required");
    }
}
```

**Código Después (After):**
```java
public Credentials {
    requireText(username, FieldNameEnum.USERNAME);
    requireText(password, FieldNameEnum.PASSWORD);
}

private static void requireText(String value, FieldNameEnum field) {
    if (value == null || value.isBlank()) {
        throw new InvalidLoginRequestException(ErrorMessageEnum.FIELD_REQUIRED, field.label());
    }
}
```

**Estado**: Resuelto
**Acción / Notas**: `FieldNameEnum` reúne los nombres de campo de todo el proyecto; `Song`, `Playlist`, `FieldRules` y
`User` también lo usan.

### 2. `domain/.../model/AccessToken.java` (Línea 10)

**Comentario:**
> // Maneja los mensajes en un enum por codigo de error como AUTH_ERROR_001

**Código Antes (Before):**
```java
public AccessToken {
    Objects.requireNonNull(value, "value");
    if (expiresInSeconds <= 0) {
        // Maneja los mensajes en un enum por codigo de error como AUTH_ERROR_001
        throw new IllegalArgumentException("An access token must last at least one second");
    }
}
```

**Código Después (After):**
```java
public AccessToken {
    Objects.requireNonNull(value, FieldNameEnum.ACCESS_TOKEN.label());
    if (expiresInSeconds <= 0) {
        throw new IllegalArgumentException(ErrorMessageEnum.ACCESS_TOKEN_LIFETIME.withCode());
    }
}
```

**Estado**: Resuelto
**Acción / Notas**: `ErrorMessageEnum` contiene todos los textos de error del proyecto, cada uno con su
`ResponseCodeEnum`. Las excepciones de dominio reciben un valor del enum en lugar de un texto, y también lo usan
`SigningKey`, `TokenLifetime`, `DemoUsersInitializer`, `SecurityErrorHandlers`, `TokenExpiryValidator` y
`GlobalExceptionHandler`. Los textos que recibe el cliente no cambian.

Cambio de contrato, aprobado por el revisor:

| Error | Antes | Después |
|---|---|---|
| Credenciales inválidas (`401`) | `PLAYLIST_GE_00004` | `AUTH_ERROR_001` |
| Falta token válido (`401`) | `PLAYLIST_GE_00005` | `AUTH_ERROR_002` |
| Sin permiso (`403`) | `PLAYLIST_GE_00006` | `AUTH_ERROR_003` |
| Configuración de seguridad inválida (arranque) | — | `AUTH_ERROR_004` |

Eran códigos nuevos de HU-03, así que no hay clientes que dependan de ellos. Los de listas no cambian. Un inicio de
sesión mal formado (`400`) sigue respondiendo `PLAYLIST_GE_00001`, porque no estaba entre los códigos consultados.
El texto que `GlobalExceptionHandler` escribe en el log ante un error inesperado sigue siendo un literal.

### 3. `domain/.../model/Playlist.java` (Línea 31)

**Comentario:**
> // Estos errores se deben mapear desde un enum

**Código Antes (Before):**
```java
if (ForbiddenNameSequence.isPresentIn(name)) {
    // Estos errores se deben mapear desde un enum
    throw new InvalidPlaylistException("The playlist name must not contain '%2F', '%5C', '%00', '%0A', "
            + "'%0D' or Unicode line separators");
}
```

**Código Después (After):**
```java
if (ForbiddenNameSequence.isPresentIn(name)) {
    throw new InvalidPlaylistException(ErrorMessageEnum.PLAYLIST_NAME_BLOCKED_SEQUENCE);
}
```

**Estado**: Resuelto
**Acción / Notas**: lo mismo en las demás excepciones de `Playlist` y en las de HU-02.

### 4. `infrastructure/.../security/config/JwtConfig.java` (Línea 17)

**Comentario:**
> // Usa un parametro y para la inyeccion del value por parametro, no lo hagas directamente en el metodo

**Código Antes (Before):**
```java
@Bean
// Usa un parametro y para la inyeccion del value por parametro, no lo hagas directamente en el metodo
SigningKey signingKey(@Value("${playlist.security.jwt.secret:}") String secret) {
    return new SigningKey(secret);
}
```

**Código Después (After):**
```java
@Value("${playlist.security.jwt.secret:}")
private String secret;

@Value("${playlist.security.jwt.ttl:30m}")
private Duration lifetime;

@Bean
SigningKey signingKey() {
    return new SigningKey(secret);
}

@Bean
TokenLifetime tokenLifetime() {
    return new TokenLifetime(lifetime);
}
```

**Estado**: Resuelto
**Acción / Notas**: `DemoUsersInitializer` tenía el mismo patrón en su constructor y también pasó a atributos. Sus
contraseñas se validan ahora en `afterSingletonsInstantiated`, que sigue siendo antes de abrir el puerto.

### 5. `infrastructure/.../out/security/JwtTokenIssuerAdapter.java` (Línea 28)

**Comentario:**
> // Usa un parametro y para la inyeccion del value por parametro, no lo hagas directamente en el metodo

**Código Antes (Before):**
```java
public JwtTokenIssuerAdapter(
        // Usa un parametro y para la inyeccion del value por parametro, no lo hagas directamente en el metodo
        JwtEncoder encoder, Clock clock, @Value("${playlist.security.jwt.ttl:30m}") Duration lifetime) {
    if (lifetime.toSeconds() <= 0) {
        throw new IllegalStateException("The environment variable JWT_TTL must be at least one second");
    }
```

**Código Después (After):**
```java
public JwtTokenIssuerAdapter(JwtEncoder encoder, Clock clock, TokenLifetime tokenLifetime) {
    this.encoder = encoder;
    this.clock = clock;
    this.lifetime = tokenLifetime.duration();
}
```

**Estado**: Resuelto
**Acción / Notas**: la vigencia pasa a ser un objeto, `TokenLifetime`, que valida que sea positiva. El adaptador no lee
propiedades y la regla se sigue probando sin Spring (`TokenLifetimeTest`).

### 6. `bootstrap/src/test/java/com/quipux/playlist/SpecialNames.java` (Línea 11)

**Comentario:**
> // Esta lista desde el properties por favor

**Código Antes (Before):**
```java
static Stream<String> all() {
    // Esta lista desde el properties por favor
    return Stream.of(
            "Mis favoritas", "Canción número 1", "ñandú", "a;b", "a+b", "a&b", "a?b", "a#b", "100%", "%",
            "%25", "%2E", "%2", "a=b,c", " con espacios ", "...", "🎵 musica");
}
```

**Código Después (After):**
```java
private static final String FILE = "special-names.properties";

static Stream<String> all() {
    try {
        Properties names = PropertiesLoaderUtils.loadProperties(
                new EncodedResource(new ClassPathResource(FILE), StandardCharsets.UTF_8));
        return names.stringPropertyNames().stream().sorted().map(names::getProperty);
    } catch (IOException exception) {
        throw new UncheckedIOException(exception);
    }
}
```

**Estado**: Resuelto
**Acción / Notas**: la lista está en `bootstrap/src/test/resources/special-names.properties`. "properties" se entendió
como un archivo `.properties` de los recursos de prueba, no como propiedades de la configuración de la aplicación.

---

## Comentarios eliminados por el revisor

Se quitaron en el IDE comentarios explicativos escritos por Claude Code, y no se repusieron: en `JwtConfig`,
`BCryptPasswordHasherAdapter`, `ProblemDetails`, `SecurityConfig`, `SecurityErrorHandlers`, `TokenExpiryValidator`,
`JwtTokenIssuerAdapter`, `LoginUseCase`, `UserRepositoryPort`, `PasswordConstraints`, `DemoUsersInitializer`,
`ControllerTestSupport`, `SchemaTest` y los dos `application.yml`.

## Verificación del resultado

`./mvnw clean verify` sobre el código final: 295 pruebas, 0 fallos, con Checkstyle, reglas de arquitectura y cobertura
mínima del 80 % por módulo.
