---
description: "Tareas de HU-01: repositorios y esqueleto del proyecto"
---

# Tasks: Repositorios y esqueleto del proyecto

**Input**: documentos de diseño en `specs/01-project-skeleton/`

**Tests**: incluidas. La especificación exige comprobaciones automáticas (FR-007, SC-003).

**Rutas**: relativas a la raíz del repositorio `quipux-playlist-backend`, salvo que se indique otra.

**Código previo**: hay archivos sin commit escritos antes de la especificación. "Crear o revisar" significa dejar el
archivo como dicen los documentos de diseño, aunque ya exista. Ninguna tarea se marca sin comprobarla.

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Crear o revisar `pom.xml`: padre `spring-boot-starter-parent` 4.1.3, Java 21, módulos `domain`,
      `application`, `infrastructure`, `bootstrap` y gestión de versiones de los módulos internos
- [X] T002 [P] Crear o revisar `domain/pom.xml`, `application/pom.xml`, `infrastructure/pom.xml` y `bootstrap/pom.xml`
      con las dependencias de FR-006: `domain` sin dependencias de compilación; `application` solo `domain` y
      `reactor-core`
- [X] T003 [P] Crear o revisar el Maven Wrapper `only-script` (`mvnw`, `mvnw.cmd`,
      `.mvn/wrapper/maven-wrapper.properties`) y registrar el permiso de ejecución con
      `git update-index --add --chmod=+x mvnw`
- [X] T004 [P] Crear `package-info.java` en `domain/src/main/java/com/quipux/playlist/domain/`,
      `application/src/main/java/com/quipux/playlist/application/` e
      `infrastructure/src/main/java/com/quipux/playlist/infrastructure/`
- [X] T005 Eliminar el código de HU-02: `Playlist.java`, `Song.java`, `InvalidPlaylistException.java` y
      `PlaylistTest.java` bajo `domain/src/`

---

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T006 Crear o revisar `bootstrap/src/main/java/com/quipux/playlist/PlaylistApplication.java`
- [X] T007 Crear o revisar `bootstrap/src/main/resources/application.yml` según R7 de `research.md`, con puerto 8080

**Checkpoint**: `./mvnw compile` termina correctamente.

---

## Phase 3: User Story 1 - Construir y arrancar el esqueleto (Priority: P1) 🎯 MVP

**Goal**: el backend se verifica con un comando, arranca sin servicios externos y crea el esquema.

**Independent Test**: pasos 1 y 2 de `quickstart.md`.

### Tests for User Story 1

- [X] T008 [P] [US1] Crear o revisar `bootstrap/src/test/java/com/quipux/playlist/PlaylistApplicationTest.java`:
      el contexto arranca con puerto aleatorio, la aplicación web es reactiva, existe un `ConnectionFactory` y no
      existe ningún `DataSource` (FR-011, FR-014)
- [X] T009 [P] [US1] Crear `bootstrap/src/test/java/com/quipux/playlist/SchemaTest.java` con `StepVerifier`: existen
      las dos tablas; se rechazan un `playlist.name` repetido, un `playlist.name` nulo, un `song.playlist_id` nulo y
      una canción de una lista inexistente (restricciones en `data-model.md`). Cada prueba usa nombres propios, porque
      comparten la base en memoria

### Implementation for User Story 1

- [X] T010 [US1] Confirmar que `SchemaTest` falla sin esquema y crear o revisar
      `infrastructure/src/main/resources/schema.sql` según `data-model.md`: `playlist.id`, `playlist.name`,
      `playlist.description`, `song.id`, `song.playlist_id`, `song.title`, `song.artist`, `song.album`,
      `song.release_year` y `song.genre`, sin borrado en cascada
- [X] T011 [US1] Ejecutar los pasos 1 y 2 de `specs/01-project-skeleton/quickstart.md` y anotar el tiempo de arranque
      (SC-002)

**Checkpoint**: el backend compila, prueba y arranca.

---

## Phase 4: User Story 2 - Proteger los límites de la arquitectura (Priority: P1)

**Goal**: una dependencia prohibida hace fallar la verificación y se nombra.

**Independent Test**: las pruebas con clases de ejemplo y la rotura 1 de `quickstart.md`.

### Tests for User Story 2

- [X] T012 [P] [US2] Crear o revisar
      `bootstrap/src/test/java/com/quipux/playlist/archfixture/domain/FrameworkDependentFixture.java`, que usa un tipo
      de Spring a propósito
- [X] T013 [P] [US2] Crear
      `bootstrap/src/test/java/com/quipux/playlist/archfixture/application/ForbiddenDependencyFixture.java`, que usa a
      propósito un tipo no permitido en aplicación

### Implementation for User Story 2

- [X] T014 [US2] Crear o revisar `bootstrap/src/test/java/com/quipux/playlist/ArchitectureTest.java`: reglas
      parametrizadas para dominio y aplicación (FR-006) e "infraestructura no depende del arranque", todas con
      conjuntos vacíos permitidos; dos pruebas que verifican que las clases de ejemplo hacen fallar su regla y que el
      mensaje nombra la dependencia (FR-007)
- [X] T015 [US2] Ejecutar la rotura 1 de `specs/01-project-skeleton/quickstart.md`, confirmar el fallo y deshacer el
      cambio

**Checkpoint**: la arquitectura queda protegida.

---

## Phase 5: User Story 3 - Exigir calidad desde el primer día (Priority: P2)

**Goal**: estilo y cobertura se comprueban en cada verificación.

**Independent Test**: roturas 2, 3 y 4 de `quickstart.md`.

### Implementation for User Story 3

- [X] T016 [P] [US3] Crear o revisar `config/checkstyle/checkstyle.xml` con las reglas de FR-010
- [X] T017 [US3] Crear o revisar en `pom.xml` el `maven-checkstyle-plugin` según R5 de `research.md`
- [X] T018 [US3] Crear o revisar en `pom.xml` JaCoCo según R4 de `research.md`, con las exclusiones de FR-009
- [X] T019 [US3] Crear o revisar en `pom.xml` el `jacoco.exec` vacío previo a las pruebas (R3 de `research.md`)
- [X] T020 [US3] Ejecutar las roturas 2 (las tres violaciones, y además un import redundante), 3 y 4 de
      `specs/01-project-skeleton/quickstart.md`, confirmar cada fallo y deshacer los cambios

**Checkpoint**: las cuatro roturas de SC-003 fallan y el proyecto limpio pasa.

---

## Phase 6: User Story 4 - Encontrar el proyecto completo (Priority: P2)

**Goal**: desde el backend se llega a todo el proyecto.

**Independent Test**: paso 4 de `quickstart.md`.

- [X] T021 [P] [US4] Verificar que `quipux-playlist-backend`, `quipux-playlist-frontend` y `quipux-playlist-infra` son
      públicos y tienen README, `.gitignore` y commit en `main` (FR-001, FR-002); no se vuelven a crear
- [X] T022 [US4] Actualizar `README.md` con los requisitos y comandos de `specs/01-project-skeleton/contracts/build.md`
      y comprobar que enlaza los otros repositorios, el ADR y el backlog (FR-003)
- [X] T023 [P] [US4] Buscar secretos y datos personales en los tres repositorios, incluido lo que está sin commit
      (FR-004)

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T024 Ejecutar el paso 5 de `specs/01-project-skeleton/quickstart.md` (verificación en Linux)
- [X] T025 Actualizar el estado de cada decisión en `specs/01-project-skeleton/research.md` con el resultado real
- [X] T026 Marcar en `docs/backlog.md` los criterios de HU-01 que quedaron comprobados
- [X] T027 Ejecutar `./mvnw clean verify` y revisar `git status` y el diff completo: sin archivos temporales ni de las
      roturas provocadas
- [X] T028 Con la aprobación del usuario: commits atómicos en formato Conventional Commits en
      `feature/01-project-skeleton`, push y PR hacia `main`

---

## Dependencies & Execution Order

- Setup (T001 a T005) → Foundational (T006, T007) → historias.
- US1 va primero: sus pruebas necesitan la aplicación y el esquema.
- US2 depende solo de Foundational.
- US3 depende de US1, para medir con pruebas ya existentes. T017 a T019 editan `pom.xml` y van en orden.
- US4 depende solo de que existan los repositorios; T022 conviene después de US1.
- Polish va al final. T028 es la única tarea que publica algo.

## Parallel Opportunities

- Setup: T002, T003, T004
- US1: T008, T009
- US2: T012, T013
- US4: T021, T023

## Implementation Strategy

**MVP**: US1 y US2, las dos P1. Después US3 y US4, y al final la revisión. En cada checkpoint se valida antes de seguir.
