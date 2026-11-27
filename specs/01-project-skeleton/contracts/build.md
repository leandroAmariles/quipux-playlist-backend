# Contrato de construcción y arranque

HU-01 no expone endpoints. Su interfaz son estos comandos, que usarán las personas y, desde HU-08, el CI.

| Comando | Resultado esperado |
|---|---|
| `./mvnw verify` | Código de salida 0 si compilan los cuatro módulos, pasan las pruebas y se cumplen los controles |
| `./mvnw package` y `java -jar bootstrap/target/playlist-bootstrap-<versión>.jar` | La API escucha en el puerto 8080 |

En Windows se usa `mvnw.cmd`.

## Causas de fallo de `verify`

| Causa | Fase | Qué muestra |
|---|---|---|
| Violación de estilo | `validate` | Archivo, línea y regla |
| Prueba o regla de arquitectura fallida | `test` | Clase y dependencia prohibida |
| Cobertura de líneas < 80 % en un módulo | `verify` | Módulo y porcentaje |

Requisitos: Java 21 y acceso a internet la primera vez.
