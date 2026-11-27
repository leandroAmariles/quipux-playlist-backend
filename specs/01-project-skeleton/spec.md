# Feature Specification: Repositorios y esqueleto del proyecto

**Feature Branch**: `feature/01-project-skeleton`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "HU-01 (feature): como desarrollador, quiero los tres repositorios (backend, frontend, infra) y los módulos Maven, para construir sobre una arquitectura definida." Fuentes: `docs/backlog.md` (HU-01) y `docs/adr/0001-arquitectura-api-listas-reproduccion.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Construir y arrancar el esqueleto (Priority: P1)

Como desarrollador, clono el backend, lo verifico con un solo comando y lo arranco sin preparar servicios externos.

**Why this priority**: Sin un proyecto que compile y arranque no se puede construir ninguna historia posterior.

**Independent Test**: Clonar en limpio, ejecutar la verificación, arrancar la aplicación y comprobar que las tablas existen.

**Acceptance Scenarios**:

1. **Given** una copia limpia del backend, **When** ejecuto el comando de verificación, **Then** los cuatro módulos compilan, las pruebas pasan y el resultado es correcto.
2. **Given** que no hay ningún servicio externo en ejecución, **When** arranco la aplicación, **Then** inicia con una base de datos en memoria.
3. **Given** la aplicación arrancada, **When** consulto el esquema, **Then** existen las tablas de listas y de canciones.

---

### User Story 2 - Proteger los límites de la arquitectura (Priority: P1)

Como desarrollador, quiero que una dependencia prohibida entre capas haga fallar la verificación, para que la arquitectura hexagonal no se degrade sin que nadie lo note.

**Why this priority**: La arquitectura es una decisión del ADR. Es barato protegerla desde el inicio y caro corregirla después.

**Independent Test**: Introducir una dependencia prohibida y comprobar que la verificación falla e indica cuál es.

**Acceptance Scenarios**:

1. **Given** el dominio sin dependencias de frameworks, **When** ejecuto la verificación, **Then** las reglas de arquitectura pasan.
2. **Given** una clase de dominio que usa un framework, **When** se evalúan las reglas, **Then** la verificación falla y nombra la dependencia.
3. **Given** una clase de aplicación que usa algo distinto del dominio, la biblioteca reactiva o la plataforma base, **When** ejecuto la verificación, **Then** falla.

---

### User Story 3 - Exigir calidad desde el primer día (Priority: P2)

Como desarrollador, quiero que la cobertura y el estilo se comprueben en cada verificación, para no corregirlos todos al final.

**Why this priority**: Es una condición de la definición de terminado de todas las historias.

**Independent Test**: Provocar una violación de estilo y un código sin cubrir, y comprobar que la verificación falla en ambos casos.

**Acceptance Scenarios**:

1. **Given** un módulo con menos del 80 % de líneas cubiertas, **When** ejecuto la verificación, **Then** falla.
2. **Given** un módulo con código y ninguna prueba, **When** ejecuto la verificación, **Then** falla.
3. **Given** una línea de más de 121 caracteres, un bloque sin llaves o un import sin usar, **When** ejecuto la verificación, **Then** falla e indica el archivo y la línea.

---

### User Story 4 - Encontrar el proyecto completo (Priority: P2)

Como evaluador, quiero partir del repositorio de backend y llegar a los otros dos y a la documentación.

**Why this priority**: El proyecto está repartido en tres repositorios y necesita un punto de entrada.

**Independent Test**: Abrir el repositorio de backend sin sesión iniciada y seguir sus enlaces.

**Acceptance Scenarios**:

1. **Given** el README del backend, **When** sigo sus enlaces, **Then** llego a los repositorios de frontend e infra, al ADR y al backlog.
2. **Given** cualquiera de los tres repositorios, **When** lo consulto, **Then** tiene README, reglas de exclusión de archivos y al menos un commit en su rama principal.

---

### Edge Cases

- Un módulo que todavía no tiene código no debe hacer fallar la verificación.
- Un módulo que tiene código pero ninguna prueba no debe pasar el control de cobertura por falta de datos de ejecución.
- Una regla de arquitectura sobre un módulo vacío no debe fallar, y debe aplicarse en cuanto el módulo tenga clases.
- El código de dominio con reglas no puede quedar excluido de la cobertura.
- La verificación debe funcionar en Windows (desarrollo) y en Linux (integración continua futura).

## Requirements *(mandatory)*

### Functional Requirements

**Repositorios**

- **FR-001**: Deben existir tres repositorios públicos (backend, frontend e infra), cada uno con README y reglas que excluyan archivos generados y secretos.
- **FR-002**: Cada repositorio debe tener su primer commit en la rama principal remota.
- **FR-003**: El README del backend debe enlazar los otros dos repositorios, el ADR y el backlog.
- **FR-004**: Ningún repositorio puede contener secretos, credenciales ni datos personales.

**Estructura y arquitectura**

- **FR-005**: El backend debe dividirse en cuatro módulos: dominio, aplicación, infraestructura y arranque.
- **FR-006**: Las dependencias permitidas son: dominio, ninguna; aplicación, el dominio y la biblioteca reactiva; infraestructura, aplicación y dominio; arranque, todos.
- **FR-007**: La verificación debe fallar cuando un módulo incumpla FR-006 y debe nombrar la dependencia que lo causa.

**Calidad**

- **FR-008**: Un solo comando debe compilar, probar y aplicar los controles de calidad a todos los módulos.
- **FR-009**: La verificación debe fallar si un módulo con código tiene menos del 80 % de líneas cubiertas. Solo se excluyen objetos de transferencia, configuración, mapeadores, excepciones y la clase de arranque.
- **FR-010**: La verificación debe fallar ante líneas de más de 121 caracteres, bloques sin llaves e imports sin usar o redundantes, tanto en código como en pruebas.

**Arranque y datos**

- **FR-011**: La aplicación debe arrancar sin servicios externos, con una base de datos en memoria.
- **FR-012**: Al arrancar debe crear el esquema de listas y canciones a partir de un script versionado.
- **FR-013**: El esquema debe impedir dos listas con el mismo nombre y canciones sin una lista existente.
- **FR-014**: El servidor web y el acceso a datos deben ser no bloqueantes.

### Key Entities *(include if feature involves data)*

- **Lista**: nombre (único) y descripción. Agrupa canciones.
- **Canción**: título, artista, álbum, año (texto) y género. Pertenece a una lista.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde una copia limpia, la verificación termina correctamente con un comando y sin pasos manuales aparte de instalar los requisitos.
- **SC-002**: La aplicación queda lista para recibir peticiones en menos de 30 segundos en el equipo de desarrollo.
- **SC-003**: Las cuatro roturas provocadas (dependencia prohibida, violación de estilo, cobertura insuficiente y módulo con código sin pruebas) hacen fallar la verificación.
- **SC-004**: Los tres repositorios son accesibles sin iniciar sesión.

## Assumptions

- **Restricciones fijadas por el ADR-0001**: Java 21, Maven multimódulo (`domain`, `application`, `infrastructure`, `bootstrap`), Spring Boot 4.1 con WebFlux, R2DBC y H2, ArchUnit, JaCoCo y Checkstyle. La biblioteca reactiva de FR-006 es Reactor.
- **Fuera de alcance**: endpoints, casos de uso, reglas de negocio (HU-02 en adelante), seguridad, géneros, aplicación Angular, contenedores y pipelines.
- En esta historia, los repositorios de frontend e infra solo tienen README y reglas de exclusión.
- Los tres repositorios y sus commits iniciales se crearon el 2026-11-26, antes de esta especificación. FR-001 a FR-004 se verifican; no se vuelven a construir.
- La protección de la rama principal y la integración continua se tratan en HU-08.
- La verificación necesita acceso a internet la primera vez para descargar dependencias.
