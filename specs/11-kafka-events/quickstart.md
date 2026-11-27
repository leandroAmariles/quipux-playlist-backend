# Quickstart: Verificación de HU-11 (Eventos en Kafka)

## 1. Backend completo (unitarias, ArchUnit, Checkstyle, JaCoCo, BlockHound y Testcontainers)

Desde `quipux-playlist-backend/` (con Docker en marcha):

```powershell
.\mvnw.cmd -B -ntp verify
```

En Linux/macOS:

```bash
./mvnw -B -ntp verify
```

## 2. Escaneo de secretos y vulnerabilidades (backend)

```bash
trivy fs --scanners secret --severity HIGH,CRITICAL --exit-code 1 --skip-dirs "**/target" .
```

## 3. Infraestructura: Docker Compose, Kustomize y Trivy

Desde `quipux-playlist-infra/`:

```powershell
# Validar sintaxis de Compose
docker compose -f compose/compose.yaml --env-file compose/.env.example config

# Validar renderizado de Kubernetes y seguridad
kustomize build k8s > $env:TEMP\rendered-k8s.yaml
trivy config --severity HIGH,CRITICAL --exit-code 1 $env:TEMP\rendered-k8s.yaml
trivy config --severity HIGH,CRITICAL --exit-code 1 argocd
trivy fs --scanners secret --severity HIGH,CRITICAL --exit-code 1 .
```
