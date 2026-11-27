# Contracts: Eventos en Kafka (HU-11)

## 1. Puerto de salida (`application`)

```java
public interface PlaylistEventPublisherPort {
    Mono<Void> publish(PlaylistEvent event);
}
```

- Invocado por `CreatePlaylistUseCase` y `DeletePlaylistUseCase` únicamente después de completar con éxito `PlaylistRepositoryPort.save` o `deleteByName`.
- Los casos de uso aplican `.onErrorResume(...)` con log `WARN` (`EVENT_PUBLISH_IGNORED`) para garantizar publicación de mejor esfuerzo sin revertir ni fallar las peticiones HTTP.

## 2. Contrato de mensajería en Kafka

- **Tópico**: `${PLAYLIST_EVENTS_TOPIC:playlist.events}`
- **Clave de partición**: `playlistName` (UTF-8 String, sin codificación URL). Garantiza orden causal por nombre de lista.
- **Formato del valor**: JSON UTF-8.

### `PlaylistCreated`
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "PlaylistCreated",
  "playlistName": "Mis favoritas",
  "songCount": 2,
  "occurredAt": "2026-11-27T10:00:00.123456Z"
}
```

### `PlaylistDeleted`
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "eventType": "PlaylistDeleted",
  "playlistName": "Mis favoritas",
  "occurredAt": "2026-11-27T10:05:00.123456Z"
}
```

## 3. Consumidor de auditoría (`infrastructure/in/event`)

- **Grupo**: `${PLAYLIST_EVENTS_CONSUMER_GROUP:playlist-audit}`
- **Formato de log (`INFO`)**:
  - Creación: `Playlist audit event: eventType=PlaylistCreated playlistName=<nombre> songCount=<n> eventId=<uuid> occurredAt=<iso>`
  - Borrado: `Playlist audit event: eventType=PlaylistDeleted playlistName=<nombre> eventId=<uuid> occurredAt=<iso>`
- **Mensajes inválidos / desconocidos**: registra `WARN` (`EVENT_MESSAGE_UNREADABLE`) y descarta el mensaje sin lanzar excepción ni detener el consumo.

## 4. Matriz de escenarios y resiliencia

| Escenario | Resultado HTTP | Evento en Kafka | Log de aplicación |
|---|---|---|---|
| `POST /lists` válido con Kafka arriba | `201 Created` | `PlaylistCreated` | `INFO` en consumidor |
| `DELETE /lists/{name}` válido con Kafka arriba | `204 No Content` | `PlaylistDeleted` | `INFO` en consumidor |
| `400` / `401` / `403` / `404` / `409` / rollback DB | Código de error correspondiente | Ninguno | Sin evento de auditoría |
| `GET /lists`, `GET /lists/{name}`, `GET /genres`, recomendaciones | `200` | Ninguno | Sin evento de auditoría |
| Kafka caído, apagado o lento (`> publish-timeout`) | `201` / `204` sin error `5xx` | No entregado | `WARN` (`EVENT_PUBLISH_IGNORED`) |
| Sondas `/actuator/health/liveness` y `/readiness` con Kafka caído | `200 {"status":"UP"}` | — | Sin degradación |

## 5. Contrato de infraestructura (`quipux-playlist-infra`)

- **`compose/compose.yaml`**: servicio `kafka` (`apache/kafka-native:4.0.0`, modo KRaft, sin ZooKeeper, sin puertos públicos, sin `depends_on` bloqueante en `api`) + variables `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` y `PLAYLIST_EVENTS_*` en `api`.
- **`k8s/`**: `kafka-deployment.yaml` (KRaft, `replicas: 1`, `runAsNonRoot: true`, `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `capabilities.drop: ["ALL"]`, `seccompProfile: RuntimeDefault`, volúmenes `emptyDir`, límites de recursos), `kafka-service.yaml` (`ClusterIP:9092`), `configmap.yaml` actualizado y `kustomization.yaml` actualizado manteniendo `api` en `replicas: 1` y sin secretos en Git.
