# Research: Postman, OpenAPI y README

Las comprobaciones con springdoc se hicieron el 2026-11-26 sobre una copia del backend, fuera del repositorio.

## R1. Librería y versión

- **Decisión**: `org.springdoc:springdoc-openapi-starter-webflux-ui:3.2.0`.
- **Motivo**: el ADR-0001 (sección 4) ya elige springdoc. La 3.2.0 se compila contra Spring Boot 4.1.2 y el proyecto usa
  4.1.3. Con ella la aplicación arranca y las 361 pruebas actuales pasan.
- **Alternativas**: 3.2.1 (la última), descartada porque se compila contra Spring Boot 4.2.0. Un `openapi.yaml` escrito
  a mano, descartado porque se desincroniza y el ADR pide springdoc.
- **Nota**: swagger-core trae Jackson 2 (`com.fasterxml.jackson` 2.21). La API sigue serializando con Jackson 3.

## R2. Qué genera por defecto y qué hay que completar

Sin configuración, el contrato sale así:

| Aspecto | Por defecto | Requisito |
|---|---|---|
| Códigos | todo `200`, también crear y borrar | FR-008 |
| Errores | ninguno | FR-008, FR-009 |
| Tipo de respuesta | `*/*` | FR-009 |
| Campos obligatorios | ninguno | FR-009 |
| Seguridad | sin esquema | FR-010 |

## R3. Cómo documentar las operaciones

- **Decisión**: dos enums (`ApiOperationDocEnum` y `ApiResponseDocEnum`) y un personalizador de springdoc que completa
  códigos, errores, `Location` y seguridad. Los controladores no se llenan de anotaciones.
- **Motivo**: mantiene los textos en enums, como pide la convención del proyecto, y deja los controladores sin cambios de
  comportamiento (FR-020). Una operación nueva sin entrada en el enum hace fallar la prueba del contrato (FR-012).
- **Alternativa**: `@Operation` y `@ApiResponse` en cada método, descartada por repetir textos y códigos en seis sitios.
- **Esquemas**: los obligatorios, las longitudes máximas y los ejemplos van como `@Schema` en los DTO, con las constantes
  de `PlaylistConstraints`.
- **Ejemplos**: sin ellos, "Try it out" envía `"genero": "string"` y crear una lista responde `400`.

## R4. Llamadas bloqueantes de springdoc

Con BlockHound, `/v3/api-docs` y los archivos de Swagger UI respondieron `500` por
`BlockingOperationError: java.io.RandomAccessFile#readBytes`:

- el contrato, porque springdoc recorre el classpath al generarlo;
- los archivos, porque el servidor de recursos estáticos de Spring lee del JAR.

- **Decisión**: un `WebFilter` que ejecuta en `boundedElastic` solo las rutas de documentación.
- **Comprobado**: contrato, configuración y archivos de la interfaz responden `200`, en dos idiomas y sin ninguna llamada
  bloqueante.
- **Alternativas descartadas**: declarar el bean `OpenAPI` (sigue bloqueando); precargar el contrato al arrancar
  (`springdoc.pre-loading-enabled`), que lo cachea por idioma, así que otro `Accept-Language` lo regenera en el event
  loop, y no cubre los archivos.

## R5. Rutas públicas

- **Decisión**: permitir sin token solo `GET` en `/v3/api-docs/**`, `/swagger-ui.html` y `/swagger-ui/**`.
- **Comprobado**: la interfaz carga todos sus archivos desde `/swagger-ui/`, así que `/webjars/**` sigue en `401`.
  `/v3/api-docs.yaml` también sigue en `401`: la spec pide un contrato y basta con el JSON.

## R6. Versión del contrato

- **Decisión**: `playlist.openapi.version: "@project.version@"` en `application.yml`, filtrado por Maven.
- **Motivo**: evita repetir la versión. El valor por defecto de springdoc (`v0`) no dice nada.

## R7. Colección de Postman

- **Decisión**: un archivo en formato Collection v2.1 en `postman/`, con variables de colección y sin archivo de entorno.
- **Motivo**: se importa de una vez. Las variables de entorno de newman o Postman pueden sustituirlas sin editar el
  archivo.
- **Repetible** (FR-004): la colección empieza borrando la lista de prueba y acepta `204` o `404`.
- **Género** (caso límite de Spotify): se toma de la respuesta de `GET /genres`.

## R8. Cómo se verifica la colección

- **Decisión**: dos niveles. Una prueba JUnit revisa el archivo (operaciones cubiertas, comprobaciones, contraseñas
  vacías, sin tokens) y la ejecución real se hace a mano con `npx newman@6.2.3` contra la API arrancada (SC-002).
- **Alternativa**: ejecutar newman dentro de `mvn verify`, descartada porque obliga a tener Node para probar el backend.
