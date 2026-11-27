# Quickstart: Géneros

## 1. Verificación automática

```bash
./mvnw verify
```

No necesita credenciales de Spotify ni conexión con él.

## 2. Contra el Spotify real

Con las variables de HU-03 y `SPOTIFY_CLIENT_ID` y `SPOTIFY_CLIENT_SECRET` definidas:

```bash
./mvnw package
java -jar bootstrap/target/playlist-bootstrap-0.1.0-SNAPSHOT.jar
```

`GET /genres` responde `200` con `origen` `LOCAL`, y el registro muestra `GENRE_ERROR_001` con el estado `404`.

## 3. Con Spotify simulado

Arrancar WireMock en otro puerto con las dos respuestas de [contracts/rest-api.md](contracts/rest-api.md) y definir:

```bash
export SPOTIFY_ACCOUNTS_BASE_URL=http://localhost:9090
export SPOTIFY_API_BASE_URL=http://localhost:9090
```

`GET /genres` responde con `origen` `SPOTIFY`. HU-07 dejará este simulador en Docker Compose.

## 4. Casos HTTP

[requests.http](requests.http).
