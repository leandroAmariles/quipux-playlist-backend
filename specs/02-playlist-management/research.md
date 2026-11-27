# Research: Gestión de listas de reproducción

Estado de cada decisión:

- **Observado**: visto el 2026-11-26 en una sonda desechable (Spring Boot 4.1.3, Java 21, H2 en memoria y el
  `schema.sql` actual), fuera del repositorio.
- **Por verificar**: lo debe confirmar la implementación con una prueba.

## R1. Orden de listas y canciones (FR-013)

- **Decision**: ordenar por el `id` de identidad. Las canciones se insertan una a una, en orden (`concatMap`). El
  esquema no cambia.
- **Rationale**: no hay edición ni reordenación de canciones, así que el orden de inserción basta.
- **Alternatives considered**: columna `position` (útil solo si algún día se reordena); inserciones en paralelo
  (`flatMap`), que no garantizan el orden.
- **Estado**: Observado. El orden se mantiene en las pruebas de persistencia y de extremo a extremo.

## R2. Unicidad y nombre duplicado (FR-005)

- **Decision**: confiar en la restricción `uk_playlist_name`, sin consulta previa. El adaptador traduce
  `DuplicateKeyException`, solo en la inserción de la lista, a la excepción de dominio de nombre duplicado.
- **Rationale**: una consulta previa no protege de peticiones simultáneas.
- **Alternatives considered**: consultar antes de insertar; un bloqueo propio en la aplicación.
- **Estado**: Observado. "Rock", "rock", "Rock " y " Rock" se aceptan como distintos y el repetido lanza
  `DuplicateKeyException`. En 2000 rondas de 4 escritores simultáneos: 2000 éxitos y 6000 `DuplicateKeyException`,
  sin otro tipo de error.

## R3. Transacciones (FR-008, FR-016)

- **Decision**: `TransactionalOperator` en el adaptador de persistencia, como indica el ADR. El borrado elimina las
  canciones y después la lista, y decide `204` o `404` según las filas borradas de la lista.
- **Rationale**: `application` no puede depender de Spring, y la atomicidad es propia de la persistencia.
- **Alternatives considered**: `@Transactional` en los casos de uso (viola las dependencias); un puerto de
  transacciones (abstracción sin segundo uso).
- **Estado**: Observado. Borrado simultáneo sin errores (un `204` y el resto `404`) y reversión comprobada al fallar
  una canción y al fallar el borrado de la lista.

## R4. Lectura de listas con sus canciones (FR-012, FR-014)

- **Decision**: una sola consulta `LEFT JOIN` ordenada por `playlist.id, song.id`, agrupada con
  `bufferUntilChanged`.
- **Rationale**: evita una consulta por lista, y al ser una sola sentencia no muestra una lista a medio borrar.
- **Alternatives considered**: una consulta por lista (N+1); dos consultas y unión en memoria (pueden ver estados
  distintos).
- **Estado**: Observado. **Ojo:** Spring Data entrecomilla el nombre de `@Table` tal cual, así que va en mayúsculas
  (`PLAYLIST`, `SONG`); en minúsculas la inserción falla con H2.

## R5. Longitudes (FR-007)

- **Decision**: validar en el dominio con `String.length()`. El esquema conserva los mismos límites como última
  barrera.
- **Rationale**: H2 cuenta igual que Java (unidades UTF-16), así que ambos límites coinciden. Un emoji cuenta 2.
- **Alternatives considered**: Bean Validation en los DTOs (las reglas quedarían fuera del dominio).
- **Estado**: Observado. 255 caracteres pasan, 256 fallan y 128 emojis (256 unidades) también fallan.

## R6. Tipos estrictos en el JSON (FR-011)

- **Decision**: personalizar el `JsonMapper` para que un número o un booleano no se conviertan en texto.
- **Rationale**: Jackson 3 acepta por defecto `"nombre": 5` como `"5"`, y la spec exige `400`.
- **Alternatives considered**: aceptar la conversión (contradice FR-011); deserializadores por campo.
- **Estado**: Observado en el mapper: con la configuración, número, decimal y booleano fallan. Objetos y arreglos mal
  puestos ya fallaban. Un elemento nulo en `canciones` se deserializa como `null`, así que lo rechaza el dominio.
- **Nota**: los campos desconocidos se ignoran. La configuración es global; HU-04 debe tenerlo presente al leer las
  respuestas de Spotify.

## R7. `Location` y nombres en la ruta (FR-009, FR-010)

- **Decision**: `"/lists/" + UriUtils.encode(name, UTF_8)`, que codifica todo carácter reservado.
- **Rationale**: la codificación de segmento (`encodePathSegment`) deja `;` sin codificar y el servidor lo trata como
  parámetro: `a;b` se convierte en `a`.
- **Alternatives considered**: `encodePathSegment` y `UriComponentsBuilder.pathSegment`; prohibir más caracteres.
- **Estado**: Observado de extremo a extremo con 14 nombres especiales, al consultar y al borrar.

## R8. Errores (FR-002)

- **Decision**: excepciones tipadas en el dominio, códigos en `ResponseCodeEnum` (sin tipos HTTP) y un único
  `GlobalExceptionHandler` que extiende `ResponseEntityExceptionHandler` y responde `ProblemDetail` con la propiedad
  `code`.
- **Rationale**: combina el ADR (`ProblemDetail`) con las guías del equipo (códigos tipados y manejador único).
- **Alternatives considered**: el sobre `ResponseDto` de la guía de errores (contradice el ADR).
- **Estado**: Observado. Un cuerpo mayor que el límite da `413` y el cuerpo `null` da `400`.

## R9. Mapeo entre capas

- **Decision**: MapStruct 1.6.3 en `infrastructure`, con DTOs y entidades como `record`.
- **Rationale**: lo exige la guía `domain-modeling` del equipo; 1.6.3 es la última versión estable.
- **Alternatives considered**: mapeo manual; 1.7.0 (en beta).
- **Estado**: Observado.

## R10. Ejecución no bloqueante (FR-017)

- **Decision**: dejar las operaciones de H2 en el hilo que las pide y vigilarlas con BlockHound 1.0.19 en una prueba
  de extremo a extremo.
- **Rationale**: `r2dbc-h2` **ejecuta de forma síncrona en el hilo suscriptor**: la API es reactiva, pero el motor
  embebido no delega en otro hilo. Con datos en memoria las operaciones son cortas.
- **Alternatives considered**: `subscribeOn(boundedElastic())` en el adaptador (complica las transacciones sin un
  bloqueo observado); no comprobarlo.
- **Estado**: Observado sobre la pila HTTP completa **en régimen estable**: BlockHound no detecta bloqueos en las
  cuatro operaciones ni en sus errores. Sí detecta lecturas de `.jar` que ocurren una sola vez: al leer
  `schema.sql` en el arranque y al inicializar `org.h2.message.DbException`, en el hilo del servidor, con
  el primer nombre duplicado. La prueba calienta antes de instalar BlockHound y corre en una JVM aparte,
  porque instrumenta la JVM entera. No hay llamadas en la lista blanca.
- **Riesgos de la prueba**: necesita `-XX:+AllowRedefinitionToAddDeleteMethods`; el `argLine` debe conservar el agente
  de JaCoCo (`@{argLine}`); la carga de clases de la primera petición puede dar falsos positivos.

## R11. Pruebas por módulo

- **Decision**: cada módulo prueba su propio código, porque la cobertura se mide por módulo.
- **Rationale**: ver R4 de HU-01.
- **Alternatives considered**: informe agregado.
- **Estado**: Observado. Ambos módulos de prueba de Boot 4 funcionan y cada módulo supera el 80 %.

## Riesgo para HU-03

El cortafuegos de Spring Security reactivo rechaza por defecto algunas secuencias codificadas, entre ellas `;` y `%`.
Al añadir la seguridad, una lista con esos caracteres dejaría de poder consultarse, contra FR-009. HU-03 debe decidir
entre relajar esas dos reglas o prohibir los caracteres en el nombre. No se ha comprobado todavía.
