# Quickstart: Autenticación y autorización

Valida la historia de principio a fin. Respuestas esperadas en [contracts/rest-api.md](contracts/rest-api.md).

## Requisitos

Java 21. Los comandos son de Git Bash; en PowerShell se usa `$env:NOMBRE = "valor"` y `curl.exe`.

## Pruebas

```bash
./mvnw verify
```

Esperado: sin fallos, sin violaciones de Checkstyle y con al menos 80 % de cobertura por módulo. No hace falta definir
variables: las pruebas traen valores falsos propios.

## Arranque

```bash
./mvnw package
export JWT_SECRET="$(openssl rand -base64 48)"
export DEMO_USER_PASSWORD="<elige una>"
export DEMO_ADMIN_PASSWORD="<elige otra>"
java -jar bootstrap/target/playlist-bootstrap-0.1.0-SNAPSHOT.jar
```

Sin alguna de las tres variables, o con `JWT_SECRET` de menos de 32 bytes, el arranque falla y nombra la variable.

## Escenarios

En otra terminal, con las mismas contraseñas:

```bash
login() {
  curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | sed 's/.*"accessToken":"\([^"]*\)".*/\1/'
}
ADMIN=$(login admin "$DEMO_ADMIN_PASSWORD")
USER_TOKEN=$(login user "$DEMO_USER_PASSWORD")
```

| # | Comando | Esperado |
|---|---|---|
| 1 | `curl -i http://localhost:8080/lists` | `401`, `WWW-Authenticate: Bearer` |
| 2 | `curl -i http://localhost:8080/lists -H "Authorization: Bearer $USER_TOKEN"` | `200` |
| 3 | `curl -i -X DELETE http://localhost:8080/lists/Rock -H "Authorization: Bearer $USER_TOKEN"` | `403` |
| 4 | `curl -i -X POST http://localhost:8080/lists -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"nombre":"a;b"}'` | `201`, `Location: /lists/a%3Bb` |
| 5 | `curl -i http://localhost:8080/lists/a%3Bb -H "Authorization: Bearer $USER_TOKEN"` | `200` |
| 6 | `curl -i -X DELETE http://localhost:8080/lists/a%3Bb -H "Authorization: Bearer $ADMIN"` | `204` |
| 7 | Como el 4, con `{"nombre":"%2F"}` | `400` |
| 8 | Login de `admin` con otra contraseña | `401` |

Para ver el vencimiento, arrancar con `JWT_TTL=5s`, iniciar sesión, esperar y repetir el escenario 2: `401`.
