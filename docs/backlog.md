# Backlog: API de listas de reproducción

Basado en el ADR-0001. Se prioriza la calidad sobre el plazo: las horas (16 en total) son orientativas, no un límite. Las estimaciones incluyen sus pruebas, salvo las del frontend (HU-24).

**Prioridad:** `P1` requisitos del enunciado · `P2` calidad y entrega continua · `P3` despliegue y valor añadido.

## Resumen

| ID | Historia | Fase | Prioridad | Horas | Depende de |
|---|---|---|---|---|---|
| HU-01 | Repositorios y esqueleto | 1 | P1 | 1,5 | — |
| HU-02 | Verificación de Spotify | 1 | P1 | 0,5 | — |
| HU-03 | Crear lista | 2 | P1 | 1,25 | HU-01 |
| HU-04 | Ver todas las listas | 2 | P1 | 0,75 | HU-03 |
| HU-05 | Ver una lista | 2 | P1 | 0,5 | HU-03 |
| HU-06 | Borrar una lista | 2 | P1 | 0,5 | HU-03 |
| HU-07 | Iniciar sesión | 3 | P1 | 0,5 | HU-01 |
| HU-08 | Autorización por rol | 3 | P1 | 0,5 | HU-07 |
| HU-09 | Consultar géneros | 3 | P1 | 0,5 | HU-02 |
| HU-10 | Login en la web | 4 | P1 | 0,5 | HU-07 |
| HU-11 | Ver listas en la web | 4 | P1 | 0,5 | HU-04, HU-05, HU-10 |
| HU-12 | Crear lista en la web | 4 | P1 | 0,75 | HU-03, HU-09, HU-10 |
| HU-13 | Borrar lista en la web | 4 | P1 | 0,25 | HU-06, HU-11 |
| HU-24 | Pruebas del frontend | 4 | P2 | 0,5 | HU-10 a HU-13 |
| HU-14 | Colección de Postman | 5 | P1 | 0,25 | HU-03 a HU-09 |
| HU-15 | Documentación OpenAPI | 5 | P1 | 0,25 | HU-03 a HU-09 |
| HU-16 | README y entrega | 5 | P1 | 0,5 | Fases 1 a 4 |
| HU-17 | Docker y Compose | 6 | P2 | 0,75 | Fase 5 |
| HU-21 | Integración continua | 7 | P2 | 1 | HU-01, HU-24 |
| HU-25 | Actualización de dependencias | 7 | P2 | 0,25 | HU-21 |
| HU-22 | Releases e imágenes | 8 | P2 | 0,75 | HU-17, HU-21 |
| HU-20 | Manifiestos de Kubernetes | 9 | P3 | 0,75 | HU-22 |
| HU-23 | Despliegue GitOps con Argo CD | 10 | P3 | 0,75 | HU-20, HU-22 |
| HU-18 | Caché de géneros en Redis | 11 | P3 | 0,75 | HU-09, HU-17 |
| HU-19 | Eventos en Kafka | 12 | P3 | 1 | HU-03, HU-06, HU-17 |

## Definición de terminado

- Criterios de aceptación cumplidos.
- Pruebas en verde.
- En el backend, `mvn verify` pasa con cobertura de al menos 80 % por módulo y sin violaciones de Checkstyle.
- Sin llamadas bloqueantes en el event loop.
- Respeta las dependencias de la arquitectura hexagonal.
- Sin secretos en el repositorio.
- Commit en formato Conventional Commits, en inglés y con la historia como ámbito, por ejemplo `feat(HU-03): create playlist`.
- Desde HU-21, el CI queda en verde.

## Épica 1: Base del proyecto

### HU-01: Repositorios y esqueleto
**Como** desarrollador, **quiero** los tres repositorios y los módulos Maven, **para** construir sobre una arquitectura definida.

- [ ] Existen los repositorios de backend, frontend e infra, cada uno con README y `.gitignore`.
- [ ] El backend tiene los módulos `domain`, `application`, `infrastructure` y `bootstrap`.
- [ ] `mvn verify` termina sin errores.
- [ ] JaCoCo y Checkstyle están configurados, y `mvn verify` falla si la cobertura baja del 80 % o hay violaciones.
- [ ] La aplicación arranca con WebFlux, R2DBC y H2, y crea el esquema desde `schema.sql`.
- [ ] Una prueba ArchUnit falla si `domain` depende de un framework.
- [ ] El primer commit de cada repositorio está en el remoto.

### HU-02: Verificación de Spotify
**Como** desarrollador, **quiero** comprobar qué endpoint de géneros funciona con mi cuenta, **para** no diseñar sobre uno no disponible.

- [ ] Se obtiene un token con client credentials.
- [ ] Se prueban `/recommendations/available-genre-seeds` y `/browse/categories`.
- [ ] El resultado queda anotado en el ADR.
- [ ] Si la integración real se complica, se simula Spotify con WireMock.
- [ ] Las credenciales solo están en variables de entorno.

## Épica 2: Gestión de listas

### HU-03: Crear lista
**Como** administrador, **quiero** crear una lista con sus canciones, **para** organizar la música.

- [ ] `POST /lists` válido devuelve `201`, cabecera `Location` y el contenido.
- [ ] Nombre nulo o vacío devuelve `400` con `ProblemDetail`.
- [ ] Nombre duplicado devuelve `409`.
- [ ] Si falla el guardado de una canción, no queda nada guardado.
- [ ] Se aceptan nombres con espacios y `Location` va codificada.

### HU-04: Ver todas las listas
**Como** usuario, **quiero** ver todas las listas, **para** saber cuáles existen.

- [ ] `GET /lists` devuelve `200` con las listas y sus canciones.
- [ ] Sin listas, devuelve un arreglo vacío.

### HU-05: Ver una lista
**Como** usuario, **quiero** consultar una lista por nombre, **para** ver su detalle.

- [ ] `GET /lists/{listName}` devuelve `200` con nombre, descripción y canciones.
- [ ] Si no existe, devuelve `404`.

### HU-06: Borrar una lista
**Como** administrador, **quiero** borrar una lista, **para** retirar las que no se usan.

- [ ] `DELETE /lists/{listName}` devuelve `204`.
- [ ] Si no existe, devuelve `404`.
- [ ] Sus canciones se borran en la misma transacción.

## Épica 3: Seguridad y géneros

### HU-07: Iniciar sesión
**Como** usuario, **quiero** autenticarme, **para** recibir un token de acceso.

- [ ] `POST /auth/login` con credenciales válidas devuelve un JWT con expiración.
- [ ] Con credenciales inválidas devuelve `401`.
- [ ] Las contraseñas se guardan con BCrypt.
- [ ] Se crean usuarios de demostración con rol `USER` y `ADMIN`.
- [ ] La clave de firma viene de una variable de entorno.

### HU-08: Autorización por rol
**Como** responsable del sistema, **quiero** restringir operaciones por rol, **para** proteger los datos.

- [ ] Sin token o con token inválido o vencido: `401`.
- [ ] `USER` puede consultar, y recibe `403` al crear o borrar.
- [ ] `ADMIN` puede consultar, crear y borrar.

### HU-09: Consultar géneros
**Como** usuario, **quiero** obtener los géneros disponibles, **para** clasificar canciones.

- [ ] `GET /genres` devuelve los géneros y su origen (Spotify o lista local).
- [ ] Si Spotify falla, responde con la lista local.
- [ ] La prueba usa WireMock, sin llamar a Spotify.
- [ ] Las URL base de Spotify son configurables.

## Épica 4: Aplicación web

### HU-10: Login en la web
**Como** usuario, **quiero** un formulario de acceso, **para** usar la aplicación.

- [ ] El login correcto lleva al listado.
- [ ] El error de credenciales se muestra en el formulario.
- [ ] Un interceptor añade el JWT y un `401` redirige al login.
- [ ] La web llega a la API mediante el proxy de desarrollo, sin configurar CORS.

### HU-11: Ver listas en la web
**Como** usuario, **quiero** ver las listas y su detalle, **para** explorar las canciones.

- [ ] Hay listado y vista de detalle.
- [ ] Se muestran los estados de carga, vacío y error.

### HU-12: Crear lista en la web
**Como** administrador, **quiero** un formulario para crear listas, **para** no depender de un cliente REST.

- [ ] Permite añadir y quitar canciones.
- [ ] Valida el nombre obligatorio.
- [ ] El género se elige de `GET /genres`.
- [ ] Muestra los errores `400` y `409`.
- [ ] La opción de crear no aparece para el rol `USER`, y su ruta queda bloqueada.

### HU-13: Borrar lista en la web
**Como** administrador, **quiero** borrar una lista desde la web, **para** gestionarla fácilmente.

- [ ] Pide confirmación y actualiza el listado.
- [ ] La opción no aparece para el rol `USER`.

### HU-24: Pruebas del frontend
**Como** desarrollador, **quiero** pruebas unitarias de la web, **para** cambiarla sin romper su comportamiento.

- [ ] El interceptor añade el JWT y un `401` redirige al login.
- [ ] El formulario de creación valida el nombre, añade y quita canciones y muestra `400` y `409`.
- [ ] El rol `USER` no ve las opciones de crear ni borrar.
- [ ] Los servicios se prueban con HTTP simulado.
- [ ] Las pruebas se ejecutan con un solo comando, sin modo interactivo.

## Épica 5: Entrega

### HU-14: Colección de Postman
**Como** evaluador, **quiero** un cliente configurado, **para** probar la API rápidamente.

- [ ] Incluye todos los endpoints y los casos de error principales.
- [ ] El token del login se guarda en una variable.
- [ ] No contiene secretos reales.

### HU-15: Documentación OpenAPI
**Como** evaluador, **quiero** Swagger UI, **para** conocer el contrato.

- [ ] Documenta endpoints, esquemas y códigos de respuesta.
- [ ] Permite autorizar con Bearer JWT.

### HU-16: README y entrega
**Como** evaluador, **quiero** instrucciones claras, **para** ejecutar el proyecto sin ayuda.

- [ ] Explica requisitos, variables de entorno, ejecución y pruebas.
- [ ] Justifica **R2DBC en lugar de JPA** de forma visible.
- [ ] Explica la limitación de Spotify.
- [ ] Incluye los ADR y los enlaces a los tres repositorios.

## Épica 6: Valor añadido

### HU-17: Docker y Compose
**Como** evaluador, **quiero** levantar todo con un comando, **para** evitar instalaciones manuales.

- [ ] Dockerfiles multietapa en backend y frontend; nginx redirige las llamadas a la API.
- [ ] El `docker-compose` está en el repositorio de infra y construye desde las carpetas hermanas.
- [ ] `docker compose up` deja la aplicación usable.
- [ ] WireMock se puede activar como sustituto de Spotify.

### HU-18: Caché de géneros en Redis
**Como** responsable del sistema, **quiero** cachear los géneros, **para** reducir llamadas a Spotify.

- [ ] La segunda consulta no llama a Spotify mientras dure el TTL.
- [ ] Con Redis caído, la API sigue respondiendo.
- [ ] La lista de respaldo no se cachea.
- [ ] Las pruebas con Testcontainers cubren el acierto de caché, la expiración del TTL y Redis caído.

### HU-19: Eventos en Kafka
**Como** auditor, **quiero** un evento por cada creación y borrado, **para** tener trazabilidad.

- [ ] Se publican `PlaylistCreated` y `PlaylistDeleted` después del commit.
- [ ] Un consumidor los registra en el log.
- [ ] Con Kafka caído, la petición no falla.
- [ ] Las pruebas con Testcontainers cubren la publicación, el consumo y Kafka caído.

### HU-20: Manifiestos de Kubernetes
**Como** responsable de operaciones, **quiero** manifiestos con Kustomize en el repositorio de infra, **para** desplegar de forma repetible.

- [ ] Deployments, Services, ConfigMap, Secret de ejemplo y probes accesibles sin token.
- [ ] El Secret de ejemplo no está en `kustomization.yaml` y no hay secretos reales en Git.
- [ ] Backend con una réplica.
- [ ] El CI de infra valida `kustomize build` y ejecuta Trivy de configuración y secretos.
- [ ] release-please está activo en el repositorio de infra.
- [ ] Probado en un clúster local.

## Épica 7: CI/CD y GitOps

### HU-21: Integración continua
**Como** desarrollador, **quiero** validar cada cambio automáticamente, **para** detectar errores y vulnerabilidades antes de fusionar.

- [ ] En cada PR y push a `main` se compila y se prueba backend y frontend.
- [ ] El CI del backend ejecuta `mvn verify`, con cobertura, Checkstyle y las pruebas con Testcontainers que existan.
- [ ] Qodana (`qodana-jvm-community`) analiza el backend y ESLint el frontend.
- [ ] Trivy revisa dependencias y secretos, y falla ante `HIGH` o `CRITICAL` con corrección disponible.
- [ ] Las acciones de terceros están fijadas por SHA de commit.
- [ ] Cada workflow declara permisos mínimos.
- [ ] `main` está protegida en los tres repositorios: exige PR y CI en verde.

### HU-22: Releases e imágenes
**Como** responsable del sistema, **quiero** versiones e imágenes automáticas, **para** saber qué se despliega.

- [ ] release-please abre el PR de release en backend y frontend, con el CHANGELOG.
- [ ] release-please usa un token propio del repositorio, y el CI se ejecuta en el PR de release.
- [ ] Al crearse una release, el mismo workflow construye la imagen, la analiza con Trivy y la publica en GHCR con la versión como tag.
- [ ] Las imágenes se pueden descargar desde el clúster (paquetes públicos o credencial configurada).

### HU-23: Despliegue GitOps con Argo CD
**Como** responsable de operaciones, **quiero** que Argo CD aplique lo que está en el repositorio de infra, **para** desplegar solo mediante Git.

- [ ] Argo CD está instalado en un clúster local y su `Application` está versionada en `argocd/`.
- [ ] Tras una release, se abre un PR en infra con el nuevo tag.
- [ ] El token para ese PR solo tiene permisos sobre el repositorio de infra y está como secreto.
- [ ] Al fusionar el PR, Argo CD sincroniza y la aplicación queda `Healthy`.
- [ ] El Secret real se crea en el clúster con un paso documentado.

### HU-25: Actualización de dependencias
**Como** responsable del sistema, **quiero** que las dependencias se actualicen mediante PR, **para** no acumular vulnerabilidades.

- [ ] Dependabot está configurado en los tres repositorios para los ecosistemas que usa cada uno (Maven, npm, Docker y GitHub Actions).
- [ ] Sus commits siguen Conventional Commits.
- [ ] Cada PR pasa el CI antes de fusionarse.

## Orden de entrega

- Las historias se completan por fases. Las `P1` van primero.
- Una historia a medias no se entrega.
