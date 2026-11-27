# Contrato: colección de Postman

Archivo: `postman/quipux-playlist.postman_collection.json` (Collection v2.1). Variables en el
[modelo](../data-model.md#variables-de-la-colección).

Las peticiones se ejecutan en este orden y cada una comprueba su código.

| Carpeta | Petición | Token | Espera |
|---|---|---|---|
| 1. Sesión | Iniciar sesión como `user` | — | `200`; guarda `userToken` |
| | Iniciar sesión como `admin` | — | `200`; guarda `adminToken` |
| | Contraseña incorrecta | — | `401` |
| 2. Preparación | Borrar la lista de prueba si existe | admin | `204` o `404` |
| 3. Flujo | Consultar géneros | user | `200`; guarda `genre` |
| | Crear la lista | admin | `201` con `Location` |
| | Consultar la lista | user | `200` con el mismo nombre |
| | Ver todas las listas | user | `200` e incluye la lista |
| 4. Errores | Nombre repetido | admin | `409` |
| | Nombre vacío | admin | `400` |
| | Género fuera del catálogo | admin | `400` |
| | Sin token | — | `401` |
| | `user` intenta crear | user | `403` |
| | `user` intenta borrar | user | `403` |
| | Consultar una lista que no existe | user | `404` |
| | Borrar una lista que no existe | admin | `404` |
| 5. Limpieza | Borrar la lista | admin | `204` |
| | Consultar la lista borrada | user | `404` |

- El nombre de la lista tiene espacios y va codificado en la ruta.
- La contraseña incorrecta es un texto ficticio escrito en la petición; las correctas solo salen de las variables.
- La descripción de la colección explica dónde escribir las contraseñas y que el token dura 30 minutos.
