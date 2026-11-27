# Quickstart: validar las recomendaciones

Contratos: [REST](contracts/rest-api.md), [proveedor](contracts/model-provider.md),
[configuración](contracts/configuration.md) y [composición](contracts/ai-compose.md).

## Requisitos

- Java 21 y el *wrapper* de Maven. Para los escenarios 5 a 8, Docker con unos 15 GiB para el motor y espacio para las
  imágenes y los modelos.
- Variables de HU-03 (`JWT_SECRET`, `DEMO_USER_PASSWORD`, `DEMO_ADMIN_PASSWORD`).

## 1. Verificación sin modelos ni claves

En `quipux-playlist-backend`, sin `AI_API_KEY`, `AI_PROVIDER` ni Ollama:

```bash
./mvnw verify
```

Esperado: pruebas en verde, cobertura de al menos 80 % por módulo y sin violaciones de Checkstyle. Usar `verify`, no
`install`.

## 2. IA desactivada (por defecto)

Arrancar la API, iniciar sesión, crear una lista y pedir `GET /lists/{listName}/recommendations`.

Esperado: `200`, `origen` `RESPALDO`, `versionPrompt` vacío, hasta 5 recomendaciones, en menos de 1 s.

## 3. Errores

| Petición | Esperado |
|---|---|
| Sin token | `401` |
| Lista inexistente | `404` |
| Undécima en un minuto | `429` con `Retry-After` |
| La misma tras esperar `Retry-After` | `200` |

## 4. IA activada sin clave

Arrancar con `AI_PROVIDER=openai` y sin `AI_API_KEY`.

Esperado: arranca, avisa en el registro y responde `RESPALDO`.

## 5. Imágenes (antes de levantar nada)

Revisar avisos nuevos, verificar la firma de LiteLLM con `cosign` (orden del ADR-0002, sección 1, ejecutada desde la
imagen de `cosign`) y comparar los digests con [research.md](research.md), R14.

## 6. Pila completa

```bash
cd quipux-playlist-infra/compose
docker compose -p quipux-ia -f compose.yaml -f compose.ai.yaml up -d --build
docker compose -p quipux-ia logs ollama-init
docker compose -p quipux-ia ps
```

| Comprobación | Esperado |
|---|---|
| `GET http://127.0.0.1:4000/v1/models` con la clave | Los dos alias |
| `POST /v1/chat/completions` a `recomendador-chat` | Responde; con `max_tokens` pequeño la respuesta se corta |
| `POST /v1/embeddings` a `recomendador-embeddings` | Vector de 768 dimensiones |
| Recomendaciones de una lista con canciones | `origen` `IA`, `versionPrompt` `v1` |
| La misma petición otra vez | Misma respuesta, registro con uso de caché |
| `docker compose ps` | `ollama` sin puertos; el resto en `127.0.0.1` |
| Segundo `up` | `ollama-init` no descarga nada |
| Registros de `api` y `litellm` | Sin clave ni prompt |

Anotar en el README la latencia en frío (primer arranque) y en caliente, y la memoria usada.

## 7. Una sola petición al modelo

Con la pila levantada, detener `ollama`, pedir una recomendación de una lista no guardada y revisar el registro de
LiteLLM.

Esperado: `200` `RESPALDO`, **un** intento en LiteLLM, y el resto de la API responde.

## 8. GPU

```bash
docker compose -p quipux-ia -f compose.yaml -f compose.ai.yaml -f compose.ai.gpu.yaml up -d
docker compose -p quipux-ia exec ollama ollama ps
```

Esperado: `PROCESSOR` indica GPU. Sin ese archivo, CPU.

## Limpieza

```bash
docker compose -p quipux-ia -f compose.yaml -f compose.ai.yaml down
```

## Resultado de la validación (2026-11-27)

T088 queda **abierta**: faltan los puntos marcados como no ejecutados.

| Escenario | Resultado | Cómo se comprobó |
|---|---|---|
| 1. Sin modelos ni claves | Cumple | `./mvnw clean verify` tras integrar `origin/main`: 611 pruebas, cobertura de 96 % a 100 % por módulo, ArchUnit y Checkstyle |
| 2. IA desactivada | Cumple | Composición base y la parte A de `requests.http`: 24 peticiones con el estado esperado |
| 3. Errores | Cumple | Las mismas 24 peticiones (`401`, `404`, `429`); servir de nuevo tras `Retry-After`, solo con `SlidingWindowRequestQuotaAdapterTest` |
| 4. IA activada sin clave | Cumple en pruebas | `RecommendationsWithoutKeyApiTest`. **No se repitió con el contenedor** |
| 5. Imágenes | Cumple | research.md, R14 |
| 6. Pila completa | Cumple, salvo la primera descarga | Alias, `max_tokens`, modo JSON, 768 dimensiones, `IA` con el modelo real, caché, puertos y registros. Los modelos se copiaron de un volumen existente: **la descarga en un volumen vacío no se ejecutó**; sí que un segundo arranque no descarga |
| 7. Una sola petición | Cumple | Un doble de Ollama recibió 1 petición ante `500`, `503` y `408`; con Ollama parado, `RESPALDO` a los 30 s y el resto de la API disponible |
| 8. GPU | Cumple | `ollama ps`: `100% GPU` con el archivo y `100% CPU` sin él |

**No ejecutado**: la colección de Postman con `newman` (su contenido lo comprueban `PostmanCollectionTest` y
`OpenApiContractTest`) y la parte B de `requests.http` (el comportamiento con el modelo real se comprobó en el
escenario 6 con otras listas).

Además: Trivy 0.76.2, con las opciones del CI, no encontró vulnerabilidades altas ni críticas con corrección en el JAR
(el escaneo de secretos del CI no se reprodujo con Trivy); una búsqueda propia no encontró secretos en los archivos ni
en el historial de backend e infra; los registros de `api` y `litellm` no contenían claves, tokens ni el prompt.
Qodana no se ejecutó.

**Dependabot (FR-046)**: HU-08 añadió su configuración a `main` después de esta validación; al integrarla se ignoran
Spring AI 2.1 o superior y Spring Boot 4.2 o superior. Solo se comprobó la sintaxis del archivo: el efecto se verá en
la siguiente ejecución de Dependabot. Por indicación de la persona usuaria, `./mvnw verify` no se repitió tras esta
integración.

