# Data Model: Postman, OpenAPI y README

La historia no cambia el dominio ni la base de datos. Estas son las estructuras que describen la API.

## `DocumentationPath` (enum)

Patrones de las rutas de documentación. El filtro usa todas; la seguridad abre sin token solo las públicas.

| Valor | Patrón | Pública |
|---|---|---|
| `API_DOCS` | `/v3/api-docs/**` | sí |
| `SWAGGER_UI_ENTRY` | `/swagger-ui.html` | sí |
| `SWAGGER_UI` | `/swagger-ui/**` | sí |
| `API_DOCS_YAML` | `/v3/api-docs.yaml` | no |
| `WEBJARS` | `/webjars/**` | no |

Las dos últimas se añadieron al implementar: con token también responden y hacen las mismas lecturas bloqueantes.

## `ApiOperationDocEnum`

Una entrada por operación.

| Atributo | Contenido |
|---|---|
| método y ruta | tomados de las constantes de los controladores |
| resumen | texto en español |
| roles | `Role` permitidos; vacío si es pública |
| éxito | `200`, `201` o `204` |
| errores | respuestas de `ApiResponseDocEnum` |

Los valores de cada operación están en el [contrato](contracts/openapi.md).

## `ApiResponseDocEnum`

| Atributo | Contenido |
|---|---|
| estado | código HTTP |
| descripción | texto en español |
| `code` | `ResponseCodeEnum` asociado; ausente en `413` y `415` |

Dos situaciones comparten estado y cambian de `code`: el `401` del inicio de sesión (`AUTH_ERROR_001`) y el de las rutas
protegidas (`AUTH_ERROR_002`), así que son dos entradas distintas.

## Esquemas

| Esquema | Campo | Obligatorio | Máximo | Ejemplo |
|---|---|---|---|---|
| `PlaylistDto` | `nombre` | sí | 255 | `Mis favoritas` |
| | `descripcion` | no | 1000 | `Para trabajar` |
| | `canciones` | no | — | — |
| `SongDto` | `titulo`, `artista`, `album` | no | 255 | texto ficticio |
| | `anno` | no | 10 | `1999` |
| | `genero` | no | 100 | `rock` |
| `LoginRequestDto` | `username` | sí | — | `admin` |
| | `password` | sí | — | `<contraseña>` |
| `TokenResponseDto` | `accessToken`, `tokenType`, `expiresIn` | sí | — | `tokenType`: `Bearer` |
| `GenresResponseDto` | `generos`, `origen` | sí | — | `origen`: `LOCAL` |
| `Problem` | `type`, `title`, `status`, `detail`, `instance`, `code` | no | — | — |

Las longitudes salen de `PlaylistConstraints`.

## Variables de la colección

| Variable | Valor inicial | Quién la escribe |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | quien prueba, si cambia el puerto |
| `userPassword`, `adminPassword` | vacío | quien prueba |
| `userToken`, `adminToken` | vacío | los inicios de sesión |
| `listName` | `Lista de Postman` | fija |
| `genre` | vacío | la consulta de géneros |
