# Quickstart: validar HU-02

## Requisitos

Java 21. En Windows se usa `mvnw.cmd` en lugar de `./mvnw`; los ejemplos usan una consola Bash.

## 1. Verificación automática

```bash
./mvnw verify
```

Resultado esperado: éxito, con las pruebas en verde, cobertura de al menos 80 % por módulo y sin violaciones.

## 2. Recorrido manual

```bash
./mvnw package
java -jar bootstrap/target/playlist-bootstrap-0.1.0-SNAPSHOT.jar
```

En otra consola, en este orden:

```bash
BASE=http://localhost:8080
BODY='{"nombre":"Mis favoritas","descripcion":"Demo","canciones":[{"titulo":"Uno"},{"titulo":"Dos"}]}'

curl -i -X POST $BASE/lists -H 'Content-Type: application/json' -d "$BODY"   # 201 y Location
curl -i -X POST $BASE/lists -H 'Content-Type: application/json' -d "$BODY"   # 409
curl -i -X POST $BASE/lists -H 'Content-Type: application/json' -d '{"nombre":"  "}'   # 400
curl -i $BASE/lists                                                          # 200, una lista, "Uno" antes que "Dos"
curl -i $BASE/lists/Mis%20favoritas                                          # 200
curl -i -X DELETE $BASE/lists/Mis%20favoritas                                # 204
curl -i $BASE/lists/Mis%20favoritas                                          # 404
curl -i -X DELETE $BASE/lists/Mis%20favoritas                                # 404
curl -i $BASE/lists                                                          # 200 y []
```

Los cuerpos y códigos de error esperados están en [contracts/rest-api.md](contracts/rest-api.md).

## 3. Lo que solo comprueban las pruebas

| Comportamiento | Motivo |
|---|---|
| Reversión al fallar una canción o el borrado (FR-008, FR-016) | Hay que provocar el fallo en la base de datos |
| Creación simultánea del mismo nombre | Necesita concurrencia controlada |
| Ausencia de bloqueos (FR-017) | Necesita BlockHound |
| Tiempos con 100 listas de 20 canciones (SC-006) | Necesita preparar los datos |
