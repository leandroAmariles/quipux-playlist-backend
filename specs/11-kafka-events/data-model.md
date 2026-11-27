# Data Model: Eventos en Kafka (HU-11)

## 1. Modelos de dominio (`domain`)

### `PlaylistEventType` (`domain/model/enums/PlaylistEventType.java`)
| Constante | Valor externo (`externalName`) | Uso |
|---|---|---|
| `PLAYLIST_CREATED` | `PlaylistCreated` | Emitido tras `POST /lists` confirmado |
| `PLAYLIST_DELETED` | `PlaylistDeleted` | Emitido tras `DELETE /lists/{listName}` confirmado |

Incluye `fromExternalName(String)` que retorna `Optional<PlaylistEventType>`.

### `PlaylistEvent` (`domain/model/PlaylistEvent.java`)
Record inmutable de dominio sin dependencias externas:
- `UUID eventId`: identificador único generado al crear el evento.
- `PlaylistEventType eventType`: tipo de evento (`PLAYLIST_CREATED` o `PLAYLIST_DELETED`).
- `String playlistName`: nombre exacto de la lista (no nulo ni en blanco).
- `Integer songCount`: cantidad de canciones (`>= 0`) obligatorio en `PLAYLIST_CREATED`; `null` en `PLAYLIST_DELETED`.
- `Instant occurredAt`: marca temporal UTC de emisión.

Métodos de fábrica:
- `PlaylistEvent.created(Playlist playlist)`
- `PlaylistEvent.deleted(String playlistName)`

### Códigos y mensajes (`ResponseCodeEnum` y `ErrorMessageEnum`)
| `ResponseCodeEnum` | Código | Descripción |
|---|---|---|
| `EVENT_BROKER_UNAVAILABLE` | `EVENT_ERROR_001` | Fallos de conexión, timeout o serialización/deserialización de eventos |
| `INVALID_EVENT_CONFIGURATION` | `EVENT_ERROR_002` | Configuración inválida al arrancar con eventos habilitados |

Mensajes añadidos en `ErrorMessageEnum`:
- `EVENT_PUBLISH_UNAVAILABLE`
- `EVENT_PUBLISH_TIMEOUT`
- `EVENT_SERIALIZATION_FAILED`
- `EVENT_PUBLISH_IGNORED`
- `EVENT_MESSAGE_UNREADABLE`
- `INVALID_EVENT_DURATION`
- `INVALID_EVENT_SETTING`

### Excepción tipada (`domain/exception/EventPublishException.java`)
Extiende `BusinessException` para representar fallos de publicación en el adaptador de infraestructura sin exponer detalles técnicos ni credenciales.

## 2. Contrato de transferencia e infraestructura (`infrastructure`)

### `PlaylistEventDto` (`infrastructure/event/dto/PlaylistEventDto.java`)
```json
{
  "eventId": "UUID",
  "eventType": "PlaylistCreated | PlaylistDeleted",
  "playlistName": "Nombre exacto",
  "songCount": 2,
  "occurredAt": "2026-11-27T10:00:00Z"
}
```
(`songCount` lleva `@JsonInclude(JsonInclude.Include.NON_NULL)` para omitirse en `PlaylistDeleted`).

### `PlaylistEventSettings` (`infrastructure/event/PlaylistEventSettings.java`)
- `String topic` (no nulo ni en blanco)
- `String consumerGroup` (no nulo ni en blanco)
- `Duration publishTimeout` (no nulo, `> 0`)

## 3. Propiedades de configuración (`bootstrap/src/main/resources/application.yml`)

| Propiedad | Variable de entorno | Valor por defecto |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.admin.auto-create` | — | `false` |
| `spring.kafka.listener.missing-topics-fatal` | — | `false` |
| `spring.kafka.listener.auto-startup` | — | `${playlist.events.enabled:true}` |
| `playlist.events.enabled` | `PLAYLIST_EVENTS_ENABLED` | `true` |
| `playlist.events.topic` | `PLAYLIST_EVENTS_TOPIC` | `playlist.events` |
| `playlist.events.consumer-group` | `PLAYLIST_EVENTS_CONSUMER_GROUP` | `playlist-audit` |
| `playlist.events.publish-timeout` | `PLAYLIST_EVENTS_PUBLISH_TIMEOUT` | `2s` |
