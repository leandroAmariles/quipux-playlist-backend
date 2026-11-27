# Data Model: Autenticación y autorización

## Dominio

### User

| Campo | Tipo | Regla |
|---|---|---|
| `username` | texto | obligatorio y único; distingue mayúsculas |
| `passwordHash` | texto | resumen BCrypt; no aparece en `toString` |
| `role` | `Role` | obligatorio |

### Credentials

Lo que llega al inicio de sesión. No aparece la contraseña en `toString`.

| Campo | Regla | Si se incumple |
|---|---|---|
| `username` | obligatorio, no solo espacios | `400` |
| `password` | obligatoria, no solo espacios | `400` |
| `password` | 72 bytes UTF-8 como máximo (`PasswordConstraints`) | `401` en el inicio de sesión; en el arranque, no arranca |

El límite de 72 bytes no lo comprueba el constructor, porque su resultado depende de quién lo use.

### AccessToken

| Campo | Tipo |
|---|---|
| `value` | texto (el JWT) |
| `expiresInSeconds` | entero positivo |

### Enums

| Enum | Valores |
|---|---|
| `Role` | `USER`, `ADMIN` |
| `DemoUser` | `USER("user", Role.USER)`, `ADMIN("admin", Role.ADMIN)` |
| `ForbiddenNameSequence` | `%2F`, `%5C`, `%00`, `%0A`, `%0D`, U+2028, U+2029 |
| `ErrorMessageEnum` | todos los textos de error del proyecto, cada uno con su código |
| `FieldNameEnum` | los nombres de campo que aparecen en esos textos |

### Cambio en Playlist

El nombre se rechaza (`400`) si contiene algún `ForbiddenNameSequence`, sin distinguir mayúsculas. Se mantienen las
reglas de HU-02.

### Errores

| Código | Enum | Estado | Excepción |
|---|---|---|---|
| `PLAYLIST_GE_00001` (ya existe) | `INVALID_REQUEST` | `400` | `InvalidLoginRequestException` |
| `AUTH_ERROR_001` | `INVALID_CREDENTIALS` | `401` | `InvalidCredentialsException` |
| `AUTH_ERROR_002` | `AUTHENTICATION_REQUIRED` | `401` | — (lo escribe la capa de seguridad) |
| `AUTH_ERROR_003` | `ACCESS_DENIED` | `403` | — (lo escribe la capa de seguridad) |
| `AUTH_ERROR_004` | `INVALID_SECURITY_CONFIGURATION` | — (detiene el arranque) | `IllegalStateException` |

## Persistencia

Tabla nueva en `schema.sql`. Se llama `app_user` porque `user` es palabra reservada.

| Columna | Tipo | Restricción |
|---|---|---|
| `id` | `BIGINT` identidad | clave primaria |
| `username` | `VARCHAR(50)` | no nulo, `uk_app_user_username` |
| `password_hash` | `VARCHAR(60)` | no nulo |
| `role` | `VARCHAR(10)` | no nulo |

La entidad declara `@Table("APP_USER")`, en mayúsculas (HU-02, research R4). Las tablas de listas no cambian.

## Token

| Dato | Contenido |
|---|---|
| `sub` | nombre de usuario |
| `role` | `USER` o `ADMIN` |
| `iat` | instante de emisión, en segundos |
| `exp` | `iat` más la vigencia |

Firma HS256. No se guarda en el servidor; vale hasta que vence, aunque la aplicación se reinicie con la misma clave.
