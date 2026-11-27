# Implementation Plan: Eventos en Kafka

**Branch**: `feature/11-kafka-events` | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `quipux-playlist-backend/specs/11-kafka-events/spec.md`

## Summary

Publicar en Kafka los eventos de dominio `PlaylistCreated` y `PlaylistDeleted` estrictamente después de confirmarse la persistencia en base de datos, mediante el puerto de salida `PlaylistEventPublisherPort` en `application` y un adaptador productor en `infrastructure` sobre `spring-boot-starter-kafka` (`KafkaTemplate`), junto con un consumidor dedicado de auditoría (`KafkaPlaylistAuditConsumer`) que registra cada evento en el log estructurado de la aplicación. La publicación es de mejor esfuerzo con tiempo máximo acotado (`2s` por defecto) y aislamiento completo frente a caídas de Kafka (sin afectar el arranque, `POST /lists`, `DELETE /lists/{listName}` ni las sondas `/actuator/health/liveness` y `/readiness`), desplegando Kafka en modo KRaft en Docker Compose y Kubernetes/Kustomize en `quipux-playlist-infra`. Detalle de decisiones en [research.md](research.md).

## Technical Context

**Language/Version**: Java 21 (backend), YAML (Docker Compose / Kubernetes Kustomize)

**Primary Dependencies**: Spring Boot `4.1.3` (WebFlux, Actuator, R2DBC), `spring-boot-starter-kafka` (`spring-kafka:4.1.3`, `kafka-clients:4.2.2`), Reactor, Jackson 3 (`tools.jackson.databind.ObjectMapper`). Solo en pruebas: `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-kafka` (`2.0.5`), `blockhound` (`1.0.19.RELEASE`), `archunit-junit5` (`1.5.1`)

**Storage**: Apache Kafka en modo KRaft (`apache/kafka-native:4.0.0`) como broker efímero sin ZooKeeper para el tópico `playlist.events` (clave de partición `playlistName`); sin cambios en el esquema R2DBC/H2

**Testing**: JUnit 5, AssertJ, Mockito, `StepVerifier`, Testcontainers (`KafkaContainer` real en KRaft), BlockHound (ejecución aislada), ArchUnit, JaCoCo (≥ 80 % de líneas por módulo), Checkstyle; `kustomize build`, `docker compose config` y `trivy` (`config` + `secret`) en infra

**Target Platform**: Windows (desarrollo local) y Linux (`ubuntu-24.04` en GitHub Actions, contenedores Docker y clúster Kubernetes/Argo CD)

**Project Type**: web-service (dos repositorios hermanos afectados: `quipux-playlist-backend` y `quipux-playlist-infra`)

**Performance Goals**: publicación y registro de auditoría en `< 5s` con Kafka disponible (`SC-001`); timeout acotado de publicación de `2s` por defecto (`PLAYLIST_EVENTS_PUBLISH_TIMEOUT`) para responder `POST /lists` (`201`) y `DELETE /lists/{listName}` (`204`) en `< 3s` con Kafka caído o lento (`SC-003`)

**Constraints**: sin llamadas bloqueantes en el event loop (`BlockHound`); `domain` sin frameworks (`java.*` únicamente); `application` solo depende de `domain` y Reactor (`ArchUnit`); cobertura ≥ 80 % por módulo en JaCoCo; cero violaciones Checkstyle (líneas ≤ 121, llaves obligatorias, sin imports sin usar); sin secretos en Git ni logs; `api` en `k8s/` con exactamente `1` réplica; cero hallazgos `HIGH`/`CRITICAL` en Qodana y Trivy

**Scale/Scope**: 3 artefactos nuevos en `domain`, 1 puerto y 2 casos de uso actualizados en `application`, 6 artefactos nuevos en `infrastructure`, wiring/config en `bootstrap`, 1 servicio nuevo en `compose.yaml`, 2 manifiestos nuevos en `k8s/`, pruebas unitarias/Testcontainers/BlockHound y actualización documental

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

La constitución base sigue con la plantilla sin rellenar; se usan como puertas el `ADR-0001` y las reglas de `CLAUDE.md`.

| Puerta | Antes de Phase 0 | Tras Phase 1 |
|---|---|---|
| Arquitectura hexagonal en cuatro módulos (`domain`, `application`, `infrastructure`, `bootstrap`) | Cumple | Cumple ([research.md](research.md) R1-R3) |
| Identificadores en inglés; contrato HTTP y DTOs externos sin cambios | Cumple | Cumple ([contracts/events-contract.md](contracts/events-contract.md)) |
| Puertos reactivos con `Mono`; sin bloqueo en event loop (`BlockHound`) | Cumple | Cumple (`boundedElastic` + `Mono.fromFuture`; R2) |
| Errores tipados en `ErrorMessageEnum` / `ResponseCodeEnum` sin exponer secretos | Cumple | Cumple (`EVENT_ERROR_001` / `EVENT_ERROR_002` en [data-model.md](data-model.md)) |
| Publicación post-commit, mejor esfuerzo, consumidor tolerante y sondas `200` con Kafka caído | Cumple | Cumple (R3, R4, R5) |
| Cobertura ≥ 80 % por módulo, Checkstyle, ArchUnit y Testcontainers en `mvn verify` | Cumple | Cumple (R6) |
| Infra endurecida (`readOnlyRootFilesystem: true`, no-root, sin secretos en Git, `api` `replicas: 1`) y Trivy limpio | Cumple | Cumple (R6, [contracts/events-contract.md](contracts/events-contract.md)) |

## Project Structure

### Documentation (this feature)

```text
quipux-playlist-backend/specs/11-kafka-events/
├── spec.md                         # Especificación funcional (HU-11)
├── checklists/
│   └── requirements.md             # Validación de calidad de la spec
├── plan.md                         # Este archivo
├── research.md                     # Phase 0: Spring Kafka, BlockHound, post-commit, KRaft y resiliencia
├── data-model.md                   # Phase 1: PlaylistEvent, PlaylistEventDto, códigos y propiedades
├── contracts/
│   └── events-contract.md          # Phase 1: contrato de puerto, JSON, tópico, consumidor e infra
├── quickstart.md                   # Phase 1: guía de verificación local y CI
└── tasks.md                        # Phase 2: lista ordenada de tareas
```

### Source Code (repository root)

```text
quipux-playlist-backend/
├── domain/src/main/java/com/quipux/playlist/domain/
│   ├── exception/EventPublishException.java
│   └── model/
│       ├── PlaylistEvent.java
│       └── enums/{PlaylistEventType,ErrorMessageEnum,ResponseCodeEnum}.java
├── application/src/main/java/com/quipux/playlist/application/
│   ├── port/out/PlaylistEventPublisherPort.java
│   └── usecase/{CreatePlaylistUseCase,DeletePlaylistUseCase}.java
├── infrastructure/src/main/java/com/quipux/playlist/infrastructure/
│   ├── event/{PlaylistEventSettings,dto/PlaylistEventDto,config/PlaylistEventConfig}.java
│   ├── in/event/KafkaPlaylistAuditConsumer.java
│   └── out/event/{KafkaPlaylistEventPublisherAdapter,NoOpPlaylistEventPublisherAdapter}.java
└── bootstrap/
    ├── pom.xml
    ├── src/main/java/com/quipux/playlist/config/UseCaseConfig.java
    ├── src/main/resources/application.yml
    └── src/test/java/com/quipux/playlist/
        ├── PlaylistEventsKafkaIntegrationTest.java
        ├── PlaylistEventsKafkaDownIntegrationTest.java
        └── PlaylistEventsNonBlockingTest.java

quipux-playlist-infra/
├── compose/compose.yaml
└── k8s/
    ├── configmap.yaml
    ├── kafka-deployment.yaml
    ├── kafka-service.yaml
    └── kustomization.yaml
```
