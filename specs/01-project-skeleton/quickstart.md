# Quickstart: validar HU-01

Requisitos: Java 21 y Git. Docker solo para el paso 5. Comandos desde la raíz del backend.

## 1. Verificación (US1, SC-001)

```bash
./mvnw clean verify
```

Esperado: `BUILD SUCCESS` en el padre y los cuatro módulos.

## 2. Arranque (US1, SC-002)

```bash
./mvnw package
java -jar bootstrap/target/playlist-bootstrap-0.1.0-SNAPSHOT.jar
```

Esperado: arranca en el puerto 8080 sin servicios externos y la línea `Started PlaylistApplication in N seconds`
muestra menos de 30. El esquema ([data-model.md](data-model.md)) se comprueba en las pruebas del paso 1. Si el 8080
está ocupado, añadir `--server.port=18080`.

## 3. Roturas provocadas (US2, US3, SC-003)

Cada cambio es temporal. Ejecutar `./mvnw clean verify` (la 1 con `-Djacoco.line.minimum=0`), confirmar el fallo y
deshacerlo.

| # | Cambio temporal | Fallo esperado |
|---|---|---|
| 1 | Clase en `application` que use un tipo de una librería no permitida¹ | Regla de arquitectura, nombra la dependencia |
| 2 | Línea de 122 caracteres, `if` sin llaves o import sin usar (uno por vez) | Checkstyle, con archivo y línea |
| 3 | Clase en `infrastructure` con varios métodos y una prueba que cubra menos del 80 % | Cobertura del módulo |
| 4 | Clase en `infrastructure` con un método y **ninguna** prueba | Cobertura del módulo (0 %) |

¹ `application` solo tiene Reactor en su classpath; para esta rotura hay que añadir temporalmente la dependencia al
`pom.xml`. Sin `-Djacoco.line.minimum=0`, la cobertura de la clase sin pruebas corta el build antes de llegar a la regla
de arquitectura. Las reglas de dominio y aplicación se demuestran además de forma permanente con clases de ejemplo en las
pruebas.

Para la rotura 3, `infrastructure` no tiene todavía dependencias de prueba: hay que añadir temporalmente
`junit-jupiter` con alcance `test`.

## 4. Repositorios (US4, SC-004, FR-001 a FR-004)

```bash
gh repo view leandroAmariles/quipux-playlist-backend --json visibility,url
gh repo view leandroAmariles/quipux-playlist-frontend --json visibility,url
gh repo view leandroAmariles/quipux-playlist-infra --json visibility,url
git grep -nIiE "client_secret|password *[:=]|ghp_|gho_"
```

Esperado: los tres `PUBLIC`; el README del backend enlaza los otros dos, el ADR y el backlog; la búsqueda no devuelve
secretos (revisar a mano cualquier coincidencia en documentación). Repetir la búsqueda en frontend e infra.

## 5. Linux (caso límite)

En PowerShell:

```powershell
docker run --rm -v "${PWD}:/src:ro" eclipse-temurin:21-jdk sh -c "cp -r /src /work && cd /work && ./mvnw -B -ntp clean verify"
```

Esperado: `BUILD SUCCESS`.
