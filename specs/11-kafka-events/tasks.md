# Tasks: Eventos en Kafka (HU-11)

**Input**: Design documents from `quipux-playlist-backend/specs/11-kafka-events/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/events-contract.md](contracts/events-contract.md)

## Phase 1: Setup & Dependencies

- [x] T001 Añadir `spring-boot-starter-kafka` en `quipux-playlist-backend/infrastructure/pom.xml`
- [x] T002 Añadir `testcontainers-kafka` y la ejecución aislada `non-blocking-playlist-events-test` en `quipux-playlist-backend/bootstrap/pom.xml`

## Phase 2: Domain & Application

- [x] T003 Añadir `EVENT_BROKER_UNAVAILABLE` e `INVALID_EVENT_CONFIGURATION` en `ResponseCodeEnum.java` y los mensajes de evento en `ErrorMessageEnum.java`
- [x] T004 Crear `PlaylistEventType.java`, `PlaylistEvent.java` y `EventPublishException.java` en `domain` (sin dependencias de frameworks)
- [x] T005 Crear el puerto de salida `PlaylistEventPublisherPort.java` en `application/port/out/`
- [x] T006 Integrar publicación post-commit de mejor esfuerzo en `CreatePlaylistUseCase.java` y `DeletePlaylistUseCase.java`

## Phase 3: Infrastructure Producer, Consumer & Bootstrap Wiring

- [x] T007 Crear `PlaylistEventDto.java` y `PlaylistEventSettings.java` en `infrastructure/event/`
- [x] T008 Crear `NoOpPlaylistEventPublisherAdapter.java` y `KafkaPlaylistEventPublisherAdapter.java` (con `boundedElastic` + timeout acotado) en `infrastructure/out/event/`
- [x] T009 Crear `KafkaPlaylistAuditConsumer.java` en `infrastructure/in/event/` con registro estructurado `INFO` y descarte seguro de mensajes inválidos (`WARN`)
- [x] T010 Crear `PlaylistEventConfig.java` en `infrastructure/event/config/` con habilitación condicional de `@EnableKafka`, timeouts del productor y fábrica de listeners tolerante
- [x] T011 Actualizar `UseCaseConfig.java`, `application.yml`, `bootstrap/src/test/resources/config/application.yml` e `infrastructure/src/test/resources/application.yml`

## Phase 4: Docker Compose & Kubernetes (`quipux-playlist-infra`)

- [x] T012 Añadir el servicio `kafka` en modo KRaft (`apache/kafka-native:4.0.0`, sin ZooKeeper, sin puertos públicos, sin `depends_on` bloqueante) y variables `KAFKA_*`/`PLAYLIST_EVENTS_*` en `quipux-playlist-infra/compose/compose.yaml`
- [x] T013 Crear `k8s/kafka-deployment.yaml` (endurecido: `runAsNonRoot`, `readOnlyRootFilesystem: true`, `emptyDir`, `capabilities.drop: ["ALL"]`, `RuntimeDefault`) y `k8s/kafka-service.yaml`, y actualizar `k8s/configmap.yaml` y `k8s/kustomization.yaml`

## Phase 5: Automated Tests (Unit, Testcontainers & BlockHound)

- [x] T014 Crear `PlaylistEventTest.java` y actualizar `ErrorMessageEnumTest.java` en `domain`
- [x] T015 Actualizar `CreatePlaylistUseCaseTest.java` y `DeletePlaylistUseCaseTest.java` en `application` (éxito, cero eventos en fallo/error y mejor esfuerzo ante fallo de publicación)
- [x] T016 Crear `PlaylistEventSettingsTest.java`, `KafkaPlaylistEventPublisherAdapterTest.java` y `KafkaPlaylistAuditConsumerTest.java` en `infrastructure`
- [x] T017 Crear `PlaylistEventsKafkaIntegrationTest.java`, `PlaylistEventsKafkaDownIntegrationTest.java` y `PlaylistEventsNonBlockingTest.java` en `bootstrap`

## Phase 6: Documentation & Full Verification

- [x] T018 Actualizar `quipux-playlist-backend/README.md`, `quipux-playlist-infra/README.md`, `docs/backlog.md` y `docs/adr/0001-arquitectura-api-listas-reproduccion.md`
- [x] T019 Ejecutar `.\mvnw.cmd -B -ntp verify`, `kustomize build k8s`, `docker compose config` y `trivy` (`config` + `secret`) y marcar todas las tareas completadas
