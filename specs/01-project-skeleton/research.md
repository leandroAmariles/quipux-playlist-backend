# Research: Repositorios y esqueleto del proyecto

Estado de cada decisión: **Observado** (visto en una ejecución local del 2026-11-26) o **Por verificar** (lo debe
confirmar la implementación). Tras implementar HU-01 no queda ninguna por verificar.

## R1. Versión de Spring Boot

- **Decision**: 4.1.3.
- **Rationale**: el soporte gratuito de 4.0 [termina el 2026-12-31](https://endoflife.date/spring-boot); 4.1 llega a
  julio de 2027.
- **Alternatives considered**: 4.0.10 (casi sin soporte); 4.2.0 (publicada hace una semana).
- **Estado**: Observado. Las dependencias se resuelven y los módulos compilan.

## R2. Comando único y sin instalar Maven (FR-008, SC-001)

- **Decision**: Maven Wrapper en modo `only-script`. El comando es `./mvnw verify`.
- **Rationale**: solo exige Java 21 y fija la versión de Maven. No añade binarios al repositorio.
- **Alternatives considered**: Maven instalado en el equipo (paso manual y versiones distintas).
- **Estado**: Observado. Verificado en Linux (contenedor `eclipse-temurin:21-jdk`, copia limpia); `mvnw` está en Git como `100755` y con LF.

## R3. Cobertura en módulos con código y sin pruebas (FR-009)

- **Decision**: crear un `jacoco.exec` vacío en cada módulo antes de las pruebas (`maven-antrun-plugin`).
- **Rationale**: JaCoCo omite la comprobación cuando no hay datos de ejecución, así que un módulo sin pruebas pasaría.
  Con un archivo vacío, la comprobación se ejecuta y ve 0 % de cobertura.
- **Alternatives considered**: informe agregado (cambia la regla de "por módulo" a global); revisión manual.
- **Consecuencia**: `./mvnw verify -DskipTests` falla en módulos con código. Para empaquetar sin pruebas se usa `package`.
- **Estado**: Observado. Un módulo sin pruebas falla con 0,00; el control con el truco desactivado pasa, así que es necesario.

## R4. Cobertura: motor y exclusiones

- **Decision**: JaCoCo 0.8.16, regla por módulo, líneas ≥ 80 %. Exclusiones: `dto`, `config`, `mapper`, `exception` y la
  clase de arranque.
- **Rationale**: son las de FR-009. No se excluye `model`, porque ahí vivirán reglas de dominio.
- **Alternatives considered**: las exclusiones de las guías del equipo, que incluyen `model`.
- **Estado**: Observado. Una cobertura de 0,40 hace fallar el módulo. **Ojo:** la regla es por módulo, así que las pruebas de `bootstrap` no cuentan para `infrastructure`; desde HU-02 cada módulo necesita sus propias pruebas.

## R5. Estilo (FR-010)

- **Decision**: `maven-checkstyle-plugin` 3.6.0 con Checkstyle 14.4.0, en la fase `validate`, incluyendo las pruebas.
  Reglas: `LineLength` 121, `NeedBraces`, `UnusedImports`, `RedundantImport`.
- **Rationale**: el motor que trae el plugin por defecto es antiguo para Java 21.
- **Alternatives considered**: perfiles Sun o Google (muchas reglas que nadie pidió).
- **Estado**: Observado para las cuatro reglas, en código principal y de pruebas.

## R6. Reglas de arquitectura (FR-005 a FR-007)

- **Decision**: dependencias entre módulos Maven más ArchUnit 1.5.1 en `bootstrap`. Reglas parametrizadas por paquete
  raíz para dominio y aplicación, más "infraestructura no depende del arranque".
- **Rationale**: Maven impide dependencias entre módulos; ArchUnit cubre librerías externas. Dos clases de ejemplo en
  pruebas incumplen a propósito y demuestran de forma permanente que las reglas detectan la falta (SC-003).
- **Alternatives considered**: solo Maven (no explica la regla); `maven-enforcer-plugin` (no mira clases).
- Las reglas permiten conjuntos vacíos, porque dominio y aplicación no tienen clases todavía.
- **Estado**: Observado. Las clases de ejemplo y una rotura real en `application` hacen fallar la regla y nombran la dependencia.

## R7. Esquema al arrancar (FR-011 a FR-013)

- **Decision**: `schema.sql` en `infrastructure`, con `spring.sql.init.mode=always` y la URL
  `r2dbc:h2:mem:///playlists?options=DB_CLOSE_DELAY=-1`. La columna del año se llama `release_year`.
- **Rationale**: `year` es palabra reservada. La inicialización de Spring evita código propio.
- **Alternatives considered**: Flyway o Liquibase (usan JDBC y no aportan con una base en memoria).
- **Estado**: Observado. Las 6 pruebas del esquema fallan sin `schema.sql` y pasan con él.

## R8. No bloqueante (FR-014)

- **Decision**: WebFlux y Spring Data R2DBC. Una prueba comprueba que el servidor es reactivo, que hay
  `ConnectionFactory` y que no hay `DataSource`.
- **Alternatives considered**: BlockHound (útil cuando exista lógica; ahora no hay nada que vigilar).
- **Estado**: Observado. Contexto reactivo, con `ConnectionFactory` y sin `DataSource`.
