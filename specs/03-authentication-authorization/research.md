# Research: Autenticación y autorización

Estado de cada decisión:

- **Observado**: visto el 2026-11-26 en una sonda desechable (Spring Boot 4.1.3, Spring Security 7.1.3, Java 21),
  fuera del repositorio.
- **Confirmado**: comprobado por las pruebas de la implementación.

## R1. Nombres de lista y protección de rutas (FR-018)

- **Decision**: permitir en `StrictServerWebExchangeFirewall` solo `;` (`setAllowSemicolon`) y `%`
  (`setAllowUrlEncodedPercent`), y rechazar en el dominio los nombres que el filtro seguiría bloqueando.
- **Rationale**: las reglas relajadas son las mínimas; el resto de la protección queda intacta.
- **Alternatives considered**: relajar diez reglas para aceptar todos los nombres de HU-02, en toda la API (deja
  pasar rutas como `//x`) o solo en `/lists/{nombre}` (funciona, pero desactiva casi todo el filtro en esa ruta). El
  usuario eligió prohibir los nombres.
- **Estado**: Observado.
  - Por defecto rechaza `%`, `;`, U+2028 y U+2029.
  - Con las dos reglas relajadas rechaza exactamente los nombres que contienen `%2F`, `%5C`, `%00`, `%0A`, `%0D` (en
    cualquier combinación de mayúsculas), U+2028 o U+2029. Comprobado sobre 373 026 nombres: 0 discrepancias entre
    esa regla y el filtro.
  - `%2E`, `%25` y `%` pasan.
  - `;` como parámetro de matriz no salta las reglas de rol: `DELETE /lists;x=1/Rock` y `/lists/Rock;x=1` con `USER`
    dan `403`.
  - El bean del filtro y un `ServerExchangeRejectedHandler` propio sustituyen a los de por defecto.

## R2. Emisión y validación del token (FR-001, FR-002)

- **Decision**: `spring-boot-starter-security-oauth2-resource-server`, con `NimbusJwtEncoder` y
  `NimbusReactiveJwtDecoder`, firma HS256 y los datos `sub`, `role`, `iat` y `exp`.
- **Rationale**: es la validación de JWT que ya integra Spring Security reactivo, como pide el ADR; no hace falta otra
  librería ni un filtro propio.
- **Alternatives considered**: jjwt con un `WebFilter` propio; firma asimétrica (solo hay un emisor y validador).
- **Estado**: Observado. Token con `alg: none`, alterado, ilegible o con otro esquema da `401`. Con el bean del
  decodificador no se genera el usuario por defecto de Spring Boot.

## R3. Vencimiento (FR-002, FR-003)

- **Decision**: validador propio que exige `exp` y rechaza si `ahora >= exp`, con un `Clock` inyectado.
- **Rationale**: la spec rechaza desde el instante del vencimiento. El validador por defecto tolera desfase, y
  `JwtTimestampValidator` con desfase cero todavía acepta el instante exacto.
- **Alternatives considered**: `JwtTimestampValidator(Duration.ZERO)` con `setAllowEmptyExpiryClaim(false)`.
- **Estado**: Observado el comportamiento de `JwtTimestampValidator`: en `exp` acepta y 1 ms después rechaza.
  Confirmado el validador propio con `TokenExpiryValidatorTest`.

## R4. Dos cadenas de seguridad (FR-010)

- **Decision**: una cadena solo para `POST /auth/login`, sin validación de token, y otra para todo lo demás. Sin CSRF,
  sesión, cookies, caché de peticiones, formulario, HTTP Basic ni cierre de sesión.
- **Rationale**: con una sola cadena, una cabecera con un token inválido haría fallar el inicio de sesión.
- **Alternatives considered**: un conversor que ignore la cabecera según la ruta.
- **Estado**: Observado. El inicio de sesión responde `200` con una cabecera `Bearer abc`; no hay `Set-Cookie`.

## R5. Reglas de acceso (FR-010 a FR-014)

- **Decision**: `GET` y `HEAD` sobre `/lists` y `/lists/**` exigen `USER` o `ADMIN`; cualquier otro método sobre esas
  rutas, `ADMIN`; el resto, un token válido. El rol sale del dato `role` con el prefijo `ROLE_`.
- **Rationale**: lo no previsto queda cerrado, y un método de escritura nuevo nace restringido.
- **Alternatives considered**: anotaciones en el controlador (`@PreAuthorize`), que repartirían las reglas.
- **Estado**: Observado. Sin rol o con un rol desconocido, `403`; ruta inexistente, `401` sin token y `404` con él.

## R6. Errores de seguridad (FR-011, FR-016)

- **Decision**: punto de entrada, manejador de acceso denegado y manejador de ruta rechazada propios, que escriben
  `ProblemDetail`. `401` lleva solo `WWW-Authenticate: Bearer`. El estado de cada código se decide en un único sitio,
  compartido con `GlobalExceptionHandler`.
- **Rationale**: los errores de los filtros no llegan al `@RestControllerAdvice`, y las respuestas por defecto salen
  sin cuerpo o describen por qué falló el token.
- **Alternatives considered**: las respuestas por defecto.
- **Estado**: Observado que los tres puntos se pueden sustituir. Confirmado con `SecurityErrorResponseTest` que el
  cuerpo lleva `status`, `detail` y `code` con `application/problem+json`, escrito con los codificadores del servidor.

## R7. Contraseñas (FR-004, FR-007)

- **Decision**: `BCryptPasswordEncoder` en un adaptador, en `boundedElastic`. El caso de uso rechaza con `401` una
  contraseña de más de 72 bytes antes de compararla, y ante un usuario inexistente compara contra un resumen señuelo.
- **Rationale**: la comparación **acepta una contraseña recortada**, así que el límite no se puede dejar a la
  librería. El señuelo iguala el tiempo de respuesta con y sin usuario.
- **Alternatives considered**: `ReactiveAuthenticationManager` con `ReactiveUserDetailsService`, que ya hace el señuelo
  pero deja la regla de negocio en el marco; Argon2 (el ADR fija BCrypt).
- **Estado**: Observado: `encode` de 73 bytes lanza `IllegalArgumentException`, pero `matches` de 73 bytes contra el
  resumen de los primeros 72 devuelve `true`.

## R8. Configuración y secretos (FR-008, FR-009)

- **Decision**: variables `JWT_SECRET`, `DEMO_USER_PASSWORD`, `DEMO_ADMIN_PASSWORD` y, opcional, `JWT_TTL` (30 min). En
  `application.yml` se referencian con valor vacío y se validan al crear los componentes, con un mensaje que nombra la
  variable y nunca su valor. La clave son los bytes UTF-8 del texto, 32 como mínimo.
- **Rationale**: un fallo al enlazar propiedades de Spring Boot puede imprimir el valor; validar después lo evita.
- **Alternatives considered**: Bean Validation sobre `@ConfigurationProperties`; clave en Base64.
- **Estado**: Confirmado con `StartupConfigurationTest`. Por la revisión de código, los `@Value` van en atributos y la
  vigencia se valida en `TokenLifetime`.

## R9. Usuarios de demostración (FR-006)

- **Decision**: tabla `app_user` en `schema.sql`. Un `SmartInitializingSingleton` en `bootstrap` llama al caso de uso
  de registro antes de que el servidor empiece a escuchar. Los nombres (`user`, `admin`) y roles están en un enum.
- **Rationale**: con un `ApplicationRunner` habría un instante en que el servidor responde sin usuarios.
- **Alternatives considered**: insertar resúmenes fijos en SQL (las contraseñas vienen del entorno).
- **Estado**: Confirmado. Usa `block()` en el hilo de arranque, que no es del event loop. El guardado inserta o
  actualiza por `username`, porque varios contextos de prueba comparten la base en memoria de la misma JVM.

## R10. Ejecución no bloqueante (FR-019)

- **Decision**: BCrypt en `boundedElastic`; firmar y validar el token en el hilo que llama. Ampliar la prueba de
  BlockHound con el inicio de sesión, `401` y `403`.
- **Rationale**: firmar y validar HS256 es cálculo breve en memoria.
- **Estado**: Observado: la primera emisión lee de un `.jar` (carga de clases); ya caliente, 200 emisiones y una
  validación en `parallel` sin bloqueos. La prueba debe calentar antes de instalar BlockHound. Confirmado con
  `PlaylistNonBlockingTest`. El texto aleatorio del señuelo (R7) se genera en el arranque, porque leer bytes aleatorios
  puede bloquear.

## R11. Pruebas existentes

- **Decision**: las pruebas de HU-02 pasan a autenticarse con un token real de `ADMIN`; las de controlador importan
  además la configuración de seguridad. `src/test/resources` define valores de prueba claramente falsos.
- **Rationale**: así comprueban el comportamiento de HU-02 con la seguridad activa (FR-017).
- **Alternatives considered**: desactivar la seguridad en las pruebas (ocultaría regresiones).
- **Estado**: Confirmado. `@WebFluxTest` funciona importando `SecurityConfig` y `JwtConfig`. `%2F` salió de la lista
  de nombres especiales de HU-02, que ahora está en `special-names.properties`.
