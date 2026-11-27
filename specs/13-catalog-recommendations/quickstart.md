# Quickstart: validar las recomendaciones sobre el catálogo

## Requisitos

- Java 21 y Docker; `compose/.env` con las variables de HU-12 y `CATALOG_DB_PASSWORD`.
- Memoria de Docker: la de HU-12 más PostgreSQL y el contexto mayor de Ollama (se mide en el escenario 9).

Órdenes de composición desde `quipux-playlist-infra/compose`, con `DC` = `docker compose -p quipux-ia -f compose.yaml
-f compose.ai.yaml` (añade `-f compose.ai.gpu.yaml` para la GPU).

## 1. Verificación del proyecto

```bash
./mvnw verify
```

Esperado: en verde sin modelos ni clave, 80 % de cobertura por módulo. Con Docker se ejecutan las pruebas de pgvector;
sin él aparecen como omitidas.

## 2. Catálogo comprobado

```bash
MUSICBRAINZ_CONTACT=<contacto> python tools/catalog/verify_catalog.py
```

Esperado: ninguna canción sin comprobar y el informe actualizado.

## 3. Desde cero

`$DC up -d --build` con un volumen nuevo del catálogo. Esperado: `catalog-index` termina con código 0 y `added` igual
al número de canciones; `SELECT count(*) FROM catalog.song_embedding` coincide. Solo `127.0.0.1` en los puertos
publicados. De paso, comprobar la descarga de modelos en un volumen vacío (pendiente de HU-12).

## 4. Indexación repetida y con cambios

`$DC run --rm catalog-index`: `added=0`. Cambiar el año de una canción, reconstruir y repetir: `added=1`,
`removed=1`, mismo total.

## 5. Recomendaciones

Crear una lista de un estilo y pedirlas (petición en `requests.http`). Esperado: `200`, `origen` `IA`, `versionPrompt`
`v2`, 1 a 5 canciones con `idCatalogo` presentes en `songs.json` y ausentes de la lista. Repetir: misma respuesta y
registro con la caché usada.

## 6. Degradación

| Acción | Esperado |
|---|---|
| `$DC stop catalog-db`, lista nueva | `RESPALDO` en menos de 6 s; el resto de la API funciona |
| `$DC stop litellm`, lista nueva | `RESPALDO` (sin embeddings) |
| Modelo de chat inexistente (`AI_CHAT_MODEL=no-existe` en `api`) | `SIMILITUD` |
| Reiniciar `api` con `catalog-db` detenido | Arranca |
| Composición base sin `compose.ai.yaml` | `RESPALDO`; no pide `CATALOG_DB_PASSWORD` |

## 7. Peor caso de entrada

Lista de 50 canciones con campos al máximo: respuesta `IA` o `SIMILITUD` y, en el registro de `ollama`, ningún
`truncating input prompt` para el chat.

## 8. Secretos

Registros de `api`, `catalog-index`, `litellm` y `catalog-db`: sin claves, contraseñas, tokens ni prompt.

## 9. Mediciones

Anotar en `research.md`: tiempo de indexación; latencia de recuperación y de chat, en frío y en caliente, en CPU y
GPU; proporción de `IA` y `SIMILITUD` en CPU; memoria de cada servicio. Analizar la imagen de PostgreSQL con Trivy y
revisar avisos.
