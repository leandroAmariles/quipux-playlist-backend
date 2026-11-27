# Quickstart: validar la HU-06

## Requisitos

- Java 21. Node solo para el paso 4.
- Puerto 8080 libre (o `SERVER_PORT`).

## 1. Pruebas

```bash
./mvnw verify
```

Espera: `BUILD SUCCESS`, con las pruebas del contrato, de la colección y de llamadas bloqueantes.

## 2. Arrancar

```bash
./mvnw package -DskipTests
export JWT_SECRET="$(openssl rand -base64 48)"
export DEMO_USER_PASSWORD="<elige una>"
export DEMO_ADMIN_PASSWORD="<elige otra>"
java -jar bootstrap/target/playlist-bootstrap-0.1.0-SNAPSHOT.jar
```

## 3. Documentación interactiva

1. Abre `http://localhost:8080/swagger-ui.html` sin sesión. Espera: carga la interfaz con las seis operaciones.
2. Ejecuta `GET /lists` sin autorizar. Espera: `401`.
3. Ejecuta `POST /auth/login` con `admin`, copia `accessToken`, pulsa **Authorize** y pégalo.
4. Ejecuta `GET /lists`. Espera: `200`.
5. Ejecuta `POST /lists` con el ejemplo propuesto. Espera: `201`.

Esto además comprueba el ejecutable empaquetado, que las pruebas automáticas no usan. Los códigos y esquemas se
comparan con el [contrato](contracts/openapi.md).

## 4. Colección de Postman

Con la API recién arrancada, ejecuta **dos veces**:

```bash
npx --yes newman@6.2.3 run postman/quipux-playlist.postman_collection.json \
  --env-var userPassword="$DEMO_USER_PASSWORD" --env-var adminPassword="$DEMO_ADMIN_PASSWORD"
```

Espera: 0 fallos en las dos. En Postman: importar el archivo, escribir las dos contraseñas en las variables de la
colección y pulsar **Run collection**. Orden y códigos en el [contrato](contracts/postman-collection.md).

## 5. README

- Sigue el README desde una copia limpia (`git clone`) hasta crear una lista, y anota el tiempo (SC-001: menos de 15
  minutos sin contar descargas).
- Comprueba las secciones de R2DBC y de Spotify, y que cada enlace abre (SC-005).
- Comprueba que no quedan secretos (SC-004):

```bash
git grep --untracked -nE "eyJ[A-Za-z0-9_-]{20,}|client_secret=[A-Za-z0-9]{16,}" -- . ':!specs'
```

Espera: sin resultados.

## Resultados (2026-11-26)

| Paso | Resultado |
|---|---|
| 1. `./mvnw verify` | `BUILD SUCCESS` |
| 3. Swagger UI sobre el JAR, en Edge sin ventana | seis operaciones; `401` sin autorizar; login `200`; autorizado `200`; crear con el ejemplo `201` |
| 4. `newman` contra la API recién arrancada | dos ejecuciones: 18 peticiones, 21 comprobaciones, 0 fallos |
| 4. Tras una ejecución interrumpida | 0 fallos; la preparación borra la lista sobrante |
| 5. README desde una copia sin `target/` | arranque, primera lista, documentación, colección y Spotify simulado, en Git Bash; compilar, arrancar e iniciar sesión, también en PowerShell |
| 5. Enlaces del README (SC-005) | los 12 abren |

La colección se probó con `newman`; falta importarla en la aplicación de Postman. El tiempo de SC-001 (una persona ajena
crea su primera lista en menos de 15 minutos) queda pendiente de quien evalúe.
