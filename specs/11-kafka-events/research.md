# Research: Eventos en Kafka (HU-11)

## R1. Librería cliente de Kafka en Spring Boot 4.1.3
- **Decisión**: usar `spring-boot-starter-kafka` (gestionado por `spring-boot-starter-parent:4.1.3`, que resuelve `spring-kafka:4.1.3` y `kafka-clients:4.2.2`).
- **Motivo**: cuenta con soporte oficial y alineación directa de versiones con Spring Boot 4.1.3 y Jackson 3 (`tools.jackson.databind.ObjectMapper`), evitando desajustes de binarios con `reactor-kafka`.
- **Alternativa descartada**: `reactor-kafka` directo, por menor integración con el BOM de Boot 4.1.3.

## R2. Ejecución no bloqueante bajo BlockHound
- **Decisión**: envolver la serialización JSON y el envío `kafkaTemplate.send(topic, key, payload)` dentro de `Mono.fromCallable(...).flatMap(payload -> Mono.fromFuture(() -> kafkaTemplate.send(...))).subscribeOn(Schedulers.boundedElastic()).timeout(publishTimeout)`.
- **Motivo**: el envío inicial de `KafkaProducer` resuelve metadatos del clúster y puede realizar operaciones de red/sincronización bloqueantes que dispararían `BlockHound` si ocurren en un hilo `parallel` o `epoll`/`nio` del event loop. Aislarlo en `Schedulers.boundedElastic()` y acotar tanto Reactor (`.timeout`) como las propiedades del productor (`max.block.ms`, `request.timeout.ms`, `delivery.timeout.ms` alineadas a `publishTimeout`, `linger.ms=0`, `retries=0`) garantiza respuesta en `< publishTimeout` sin bloquear jamás el event loop.

## R3. Garantía de publicación estrictamente post-commit
- **Decisión**: invocar `PlaylistEventPublisherPort.publish(...)` dentro de `CreatePlaylistUseCase` y `DeletePlaylistUseCase` mediante `.flatMap` inmediatamente después de que `PlaylistRepositoryPort.save(...)` o `deleteByName(...)` emita su resultado exitoso.
- **Motivo**: `PlaylistPersistenceAdapter` ejecuta `save` y `deleteByName` dentro de `TransactionalOperator.transactional(...)`, por lo que el `Mono` del repositorio solo completa con valor tras el commit en H2/R2DBC. Si hay validación fallida (`400`), conflicto (`409`), inexistencia (`404`) o reversión transaccional, el flujo termina en error antes de llegar al `.flatMap` de publicación y emite `0` eventos.

## R4. Consumidor de auditoría y tolerancia a mensajes corruptos
- **Decisión**: crear `KafkaPlaylistAuditConsumer` en `infrastructure/in/event/` con `@KafkaListener` vinculado a un `ConcurrentKafkaListenerContainerFactory` propio (`playlistAuditListenerContainerFactory`) configurado dentro de una subconfiguración condicional (`@ConditionalOnProperty(prefix = "playlist.events", name = "enabled", havingValue = "true", matchIfMissing = true)`), junto con `@EnableKafka`.
- **Motivo**: al condicionar `@EnableKafka` y la fábrica de listeners al flag `playlist.events.enabled`, los tests existentes con `playlist.events.enabled=false` no intentan abrir consumidores contra `localhost:9092`. Dentro de `onMessage(String)`, cualquier mensaje en blanco, JSON inválido o tipo desconocido captura `JacksonException`/`IllegalArgumentException`, emite `LOG.warn` y retorna sin lanzar excepción para no atorar la partición ni reiniciar el consumidor.

## R5. Aislamiento en arranque y sondas de salud
- **Decisión**: configurar `spring.kafka.admin.auto-create=false` y `missingTopicsFatal=false` en el listener; no declarar `depends_on` hacia `kafka` en `compose.yaml`; y comprobar que `/actuator/health/liveness` y `/actuator/health/readiness` solo dependen de `livenessState` y `readinessState` (sin indicador de Kafka bloqueante).
- **Motivo**: si Kafka está caído al arrancar o durante la ejecución, la API arranca con normalidad, `POST /lists` y `DELETE /lists/{listName}` completan gracias a `.onErrorResume` en los casos de uso, y las sondas siguen devolviendo `200 UP`.

## R6. Imagen Kafka KRaft en Testcontainers, Compose y Kubernetes
- **Decisión**: usar `apache/kafka-native:4.0.0` (imagen oficial Apache compilada nativamente para modo KRaft sin ZooKeeper, arranque rápido y bajo consumo de memoria), compatible con `org.testcontainers.kafka.KafkaContainer` (`testcontainers-kafka:2.0.5`).
- **Motivo**: cumple `ADR-0001` sección 7 (Kafka en modo KRaft sin ZooKeeper), etiqueta fija sin `latest`, ejecución sin root (`UID 1000`) y soporte para `readOnlyRootFilesystem: true` en Kubernetes montando `emptyDir` en `/tmp`, `/var/lib/kafka/data`, `/opt/kafka/logs` y `/etc/kafka/docker` (si es necesario para scripts de arranque) o usando `KAFKA_LOG_DIRS=/var/lib/kafka/data`.
