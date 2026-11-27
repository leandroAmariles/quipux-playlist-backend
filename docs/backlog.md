# Backlog: API de listas de reproducción

Basado en el ADR-0001. Se prioriza la calidad sobre el plazo: las horas (16 en total) son orientativas, no un límite. Las estimaciones incluyen sus pruebas.

**Prioridad:** `P1` requisitos del enunciado · `P2` calidad y entrega continua · `P3` despliegue y valor añadido.

## Resumen

| ID | Historia | Fase | Prioridad | Horas | Depende de |
|---|---|---|---|---|---|
| HU-01 | Repositorios y esqueleto | 1 | P1 | 1,5 | — |
| HU-02 | Gestión de listas | 2 | P1 | 3 | HU-01 |
| HU-03 | Autenticación y autorización | 3 | P1 | 1 | HU-02 |
| HU-04 | Géneros | 3 | P1 | 1 | HU-01 |
| HU-05 | Aplicación web | 4 | P1 | 2,5 | HU-02, HU-03, HU-04 |
| HU-06 | Postman, OpenAPI y README | 5 | P1 | 1 | HU-02 a HU-05 |
| HU-07 | Docker y Compose | 6 | P2 | 0,75 | HU-06 |
| HU-08 | CI, dependencias y releases | 7 y 8 | P2 | 2 | HU-05, HU-07 |
| HU-09 | Kubernetes y Argo CD | 9 y 10 | P3 | 1,5 | HU-08 |
| HU-10 | Caché de géneros en Redis | 11 | P3 | 0,75 | HU-04, HU-07 |
| HU-11 | Eventos en Kafka | 12 | P3 | 1 | HU-02, HU-07 |

## Definición de terminado

- Criterios de aceptación cumplidos.
- Pruebas en verde.
- En el backend, `mvn verify` pasa con cobertura de al menos 80 % por módulo y sin violaciones de Checkstyle.
- Sin llamadas bloqueantes en el event loop.
- Respeta las dependencias de la arquitectura hexagonal.
- Sin secretos en el repositorio.
- Commit en formato Conventional Commits, en inglés y con la historia como ámbito, por ejemplo `feat(HU-02): create playlist`.
- Desde HU-08, el CI queda en verde.

## Épica 1: Base del proyecto

### HU-01: Repositorios y esqueleto
**Como** desarrollador, **quiero** los tres repositorios y los módulos Maven, **para** construir sobre una arquitectura definida.

- [x] Existen los repositorios de backend, frontend e infra, cada uno con README y `.gitignore`.
- [x] El backend tiene los módulos `domain`, `application`, `infrastructure` y `bootstrap`.
- [x] `mvn verify` termina sin errores.
- [x] JaCoCo y Checkstyle están configurados, y `mvn verify` falla si la cobertura baja del 80 % o hay violaciones.
- [x] La aplicación arranca con WebFlux, R2DBC y H2, y crea el esquema desde `schema.sql`.
- [x] Una prueba ArchUnit falla si `domain` depende de un framework.
- [x] El primer commit de cada repositorio está en el remoto.

## Épica 2: API

### HU-02: Gestión de listas
**Como** usuario, **quiero** consultar las listas, y como administrador crearlas y borrarlas, **para** organizar la música.

**Crear**
- [x] `POST /lists` válido devuelve `201`, cabecera `Location` y el contenido.
- [x] Nombre nulo o vacío devuelve `400` con `ProblemDetail`.
- [x] Nombre duplicado devuelve `409`.
- [x] Si falla el guardado de una canción, no queda nada guardado.
- [x] Se aceptan nombres con espacios y `Location` va codificada.

**Ver todas**
- [x] `GET /lists` devuelve `200` con las listas y sus canciones.
- [x] Sin listas, devuelve un arreglo vacío.

**Ver una**
- [x] `GET /lists/{listName}` devuelve `200` con nombre, descripción y canciones.
- [x] Si no existe, devuelve `404`.

**Borrar**
- [x] `DELETE /lists/{listName}` devuelve `204`.
- [x] Si no existe, devuelve `404`.
- [x] Sus canciones se borran en la misma transacción.

### HU-03: Autenticación y autorización
**Como** usuario, **quiero** autenticarme y operar según mi rol, **para** usar el sistema con los datos protegidos.

**Inicio de sesión**
- [x] `POST /auth/login` con credenciales válidas devuelve un JWT con expiración.
- [x] Con credenciales inválidas devuelve `401`.
- [x] Las contraseñas se guardan con BCrypt.
- [x] Se crean usuarios de demostración con rol `USER` y `ADMIN`.
- [x] La clave de firma viene de una variable de entorno.

**Autorización por rol**
- [x] Sin token o con token inválido o vencido: `401`.
- [x] `USER` puede consultar, y recibe `403` al crear o borrar.
- [x] `ADMIN` puede consultar, crear y borrar.

### HU-04: Géneros
**Como** usuario, **quiero** obtener los géneros disponibles, **para** clasificar canciones.

**Verificación de Spotify (primer paso)**
- [ ] Se obtiene un token con client credentials.
- [ ] Se prueban `/recommendations/available-genre-seeds` y `/browse/categories`.
- [ ] El resultado queda anotado en el ADR.
- [ ] Si la integración real se complica, se simula Spotify con WireMock.
- [ ] Las credenciales solo están en variables de entorno.

**Consulta de géneros**
- [ ] `GET /genres` devuelve los géneros y su origen (Spotify o lista local).
- [ ] Si Spotify falla, responde con la lista local.
- [ ] La prueba usa WireMock, sin llamar a Spotify.
- [ ] Las URL base de Spotify son configurables.

## Épica 3: Aplicación web

### HU-05: Aplicación web
**Como** usuario, **quiero** una web para acceder, explorar las listas y, como administrador, gestionarlas, **para** no depender de un cliente REST.

**Login**
- [ ] El login correcto lleva al listado.
- [ ] El error de credenciales se muestra en el formulario.
- [ ] Un interceptor añade el JWT y un `401` redirige al login.
- [ ] La web llega a la API mediante el proxy de desarrollo, sin configurar CORS.

**Ver listas**
- [ ] Hay listado y vista de detalle.
- [ ] Se muestran los estados de carga, vacío y error.

**Crear lista**
- [ ] Permite añadir y quitar canciones.
- [ ] Valida el nombre obligatorio.
- [ ] El género se elige de `GET /genres`.
- [ ] Muestra los errores `400` y `409`.
- [ ] La opción de crear no aparece para el rol `USER`, y su ruta queda bloqueada.

**Borrar lista**
- [ ] Pide confirmación y actualiza el listado.
- [ ] La opción no aparece para el rol `USER`.

**Pruebas unitarias**
- [ ] El interceptor añade el JWT y un `401` redirige al login.
- [ ] El formulario de creación valida el nombre, añade y quita canciones y muestra `400` y `409`.
- [ ] El rol `USER` no ve las opciones de crear ni borrar.
- [ ] Los servicios se prueban con HTTP simulado.
- [ ] Las pruebas se ejecutan con un solo comando, sin modo interactivo.

## Épica 4: Entrega

### HU-06: Postman, OpenAPI y README
**Como** evaluador, **quiero** un cliente configurado, el contrato documentado e instrucciones claras, **para** probar el proyecto sin ayuda.

**Colección de Postman**
- [ ] Incluye todos los endpoints y los casos de error principales.
- [ ] El token del login se guarda en una variable.
- [ ] No contiene secretos reales.

**OpenAPI**
- [ ] Documenta endpoints, esquemas y códigos de respuesta.
- [ ] Permite autorizar con Bearer JWT.

**README**
- [ ] Explica requisitos, variables de entorno, ejecución y pruebas.
- [ ] Justifica **R2DBC en lugar de JPA** de forma visible.
- [ ] Explica la limitación de Spotify.
- [ ] Incluye los ADR y los enlaces a los tres repositorios.

## Épica 5: Contenedores, CI/CD y GitOps

### HU-07: Docker y Compose
**Como** evaluador, **quiero** levantar todo con un comando, **para** evitar instalaciones manuales.

- [ ] Dockerfiles multietapa en backend y frontend; nginx redirige las llamadas a la API.
- [ ] El `docker-compose` está en el repositorio de infra y construye desde las carpetas hermanas.
- [ ] `docker compose up` deja la aplicación usable.
- [ ] WireMock se puede activar como sustituto de Spotify.

### HU-08: CI, dependencias y releases
**Como** responsable del sistema, **quiero** validar cada cambio y generar versiones automáticamente, **para** detectar errores y vulnerabilidades y saber qué se despliega.

**Integración continua**
- [ ] En cada PR y push a `main` se compila y se prueba backend y frontend.
- [ ] El CI del backend ejecuta `mvn verify`, con cobertura, Checkstyle y las pruebas con Testcontainers que existan.
- [ ] Qodana (`qodana-jvm-community`) analiza el backend y ESLint el frontend.
- [ ] Trivy revisa dependencias y secretos, y falla ante `HIGH` o `CRITICAL` con corrección disponible.
- [ ] Las acciones de terceros están fijadas por SHA de commit.
- [ ] Cada workflow declara permisos mínimos.
- [ ] `main` está protegida en los tres repositorios y exige PR; en backend y frontend exige además el CI en verde.

**Actualización de dependencias**
- [ ] Dependabot está configurado en los tres repositorios para los ecosistemas que usa cada uno (Maven, npm, Docker y GitHub Actions).
- [ ] Sus commits siguen Conventional Commits.
- [ ] Cada PR pasa el CI antes de fusionarse (en infra, desde HU-09).

**Releases e imágenes**
- [ ] release-please abre el PR de release en backend y frontend, con el CHANGELOG.
- [ ] release-please usa un token propio del repositorio, y el CI se ejecuta en el PR de release.
- [ ] Al crearse una release, el mismo workflow construye la imagen, la analiza con Trivy y la publica en GHCR con la versión como tag.
- [ ] Las imágenes se pueden descargar desde el clúster (paquetes públicos o credencial configurada).

### HU-09: Kubernetes y Argo CD
**Como** responsable de operaciones, **quiero** manifiestos con Kustomize en el repositorio de infra aplicados por Argo CD, **para** desplegar de forma repetible y solo mediante Git.

**Manifiestos**
- [ ] Deployments, Services, ConfigMap, Secret de ejemplo y probes accesibles sin token.
- [ ] El Secret de ejemplo no está en `kustomization.yaml` y no hay secretos reales en Git.
- [ ] Backend con una réplica.
- [ ] El CI de infra valida `kustomize build` y ejecuta Trivy de configuración y secretos.
- [ ] `main` de infra exige además el CI en verde.
- [ ] release-please está activo en el repositorio de infra.
- [ ] Probado en un clúster local.

**Despliegue GitOps**
- [ ] Argo CD está instalado en un clúster local y su `Application` está versionada en `argocd/`.
- [ ] Tras una release, se abre un PR en infra con el nuevo tag.
- [ ] El token para ese PR solo tiene permisos sobre el repositorio de infra y está como secreto.
- [ ] Al fusionar el PR, Argo CD sincroniza y la aplicación queda `Healthy`.
- [ ] El Secret real se crea en el clúster con un paso documentado.

## Épica 6: Valor añadido

### HU-10: Caché de géneros en Redis
**Como** responsable del sistema, **quiero** cachear los géneros, **para** reducir llamadas a Spotify.

- [ ] La segunda consulta no llama a Spotify mientras dure el TTL.
- [ ] Con Redis caído, la API sigue respondiendo.
- [ ] La lista de respaldo no se cachea.
- [ ] Las pruebas con Testcontainers cubren el acierto de caché, la expiración del TTL y Redis caído.

### HU-11: Eventos en Kafka
**Como** auditor, **quiero** un evento por cada creación y borrado, **para** tener trazabilidad.

- [ ] Se publican `PlaylistCreated` y `PlaylistDeleted` después del commit.
- [ ] Un consumidor los registra en el log.
- [ ] Con Kafka caído, la petición no falla.
- [ ] Las pruebas con Testcontainers cubren la publicación, el consumo y Kafka caído.

## Orden de entrega

- Las historias se completan por fases. Las `P1` van primero.
- Una historia a medias no se entrega.
