# Línea base

Ejecución del 2026-11-27 (`20261127-061249`), 18 casos, todos con origen `IA`, en 7 min 21 s.

| Versión | Valor |
|---|---|
| Prompt | `v2-lf1` (el texto de `v2.txt`, commit `311f7ac`) |
| Chat y juez | `llama3.2:3b` (`recomendador-chat`) |
| Embeddings | `nomic-embed-text` |
| Catálogo | `4656265ce94f` |

## Promedios

| Métrica | Promedio | Casos |
|---|---|---|
| faithfulness | 0,56 | 18 |
| answer_relevancy | 0,49 | 18 |
| context_precision | 0,59 | 17 (uno con salida incompleta del juez) |
| context_recall | 0,77 | 18 |
| aciertos_sobre_esperadas | 0,61 | 18 |

Las seis comprobaciones deterministas se superaron en los 18 casos.

Es **una sola ejecución**, sin umbral. No tomes diferencias pequeñas como mejoras: `faithfulness` de `rock-clasico`
dio 0,90, 0,20 y 0,90 en tres ejecuciones.

## Revisión de una muestra

**La hizo el asistente de IA, leyendo `report.json`. Falta la revisión de una persona.**
Casos: `grunge`, `thrash`, `jazz-modal`, `reggae`, `house` y `soul`.

### Motivos falsos

El prompt pide motivos basados solo en los datos entregados y el modelo no lo cumple:

| Caso | Sugerencia | Motivo del modelo | Realidad según los datos |
|---|---|---|---|
| `jazz-modal` | Goodbye Pork Pie Hat | mismo álbum y año que *So What* | mismo año, otro álbum |
| `jazz-modal` | Giant Steps | mismo artista que *Take Five* | John Coltrane, no The Dave Brubeck Quartet |
| `jazz-modal` | Purple Haze y dos de The Beatles | rock con el mismo año que las de la lista | 1967 y 1969; la lista es de 1959 |
| `soul` | Purple Haze, Jamming, Here Comes the Sun | comparten el género soul | son psych-rock, reggae y rock |

Las validaciones del backend no lo detectan: comprueban que la canción exista, no que el motivo sea cierto.

### Elección de canciones

- Solo 24 de 58 sugerencias comparten género con la lista.
- `thrash` no sugirió ningún metal y `soul` ninguna de soul, aunque había esperadas entre las candidatas (3 de 4 y 2
  de 3).
- `reggae` y `house` devolvieron una sola sugerencia, correcta.

### Fiabilidad del juez

Calculado sin juez, las canciones esperadas estaban entre las candidatas en un 0,87 de media (el juez dio 0,77):

| Caso | Esperadas entre las candidatas | context_recall del juez |
|---|---|---|
| `reggae` | 3 de 3 | 0,14 |
| `reggaeton` | 3 de 3 | 0,60 |
| `soul` | 2 de 3 | 0,17 |
| `house` | 2 de 4 | 1,00 |

`answer_relevancy` dio 0,00 en `grunge` y `pop-actual` sin que sus respuestas se distingan de las demás.

## Qué se desprende

1. La recuperación funciona razonablemente; lo débil es la elección y, sobre todo, los motivos de `llama3.2:3b`.
2. El mismo modelo pequeño como juez no es fiable caso a caso. Antes de decidir con estas notas, usa un juez mayor.
3. Candidatos a comparar contra esta base: un modelo de chat mayor (por ejemplo `qwen2.5:7b`) y una versión del prompt
   que limite los motivos a género, artista o época comprobables.
