# Cambios de la versión 1.0.87 (sin Google Books ni RAE API, lista para poder cobrar)

Objetivo: que la app pueda venderse o tener versión premium. Las condiciones de la API de
Google Books dicen «You may not charge users any fee for the use of your application» sin
un acuerdo con Google, y la «RAE API comunitaria» era un servicio no oficial hecho a partir
de la web de la RAE. Se han quitado las dos.

## Google Books → Open Library

| Función | Antes | Ahora |
|---|---|---|
| Novedades de Inicio y «Ver más» | Google Books por editorial | Open Library: `publisher:"…" language:spa first_publish_year:[año-1 TO año+1]`, ordenado por lo más nuevo |
| Recomendaciones | Google Books por género | Open Library por tema (`subject:"fantasy"`…), en el idioma de la app, mejor valorados de los últimos 25 años |
| Búsqueda en Descubrir | Google Books + Open Library | Open Library (prioriza ediciones en el idioma de la app con `lang=`) |
| Ficha pública: ediciones, páginas, editorial | Google Books | Ediciones de la obra en Open Library (`/works/…/editions.json`), primero las del idioma de la app |
| Autor y argumento de un libro propio | Google Books | Open Library (descripción de la obra, traducida al idioma de la app) |
| Portadas que faltan | Open Library + Google Books | Open Library |
| Géneros automáticos | Google Books + Open Library | Open Library |
| Número de saga | Open Library + Google Books + Wikidata | Open Library + Wikidata |

- Se quitan la clave opcional de Google Books (secreto `GOOGLE_BOOKS_API_KEY`), sus
  cabeceras y la espera de una hora por cupo agotado.
- El país del dispositivo **ya no sale del teléfono**: solo se usa para elegir las
  editoriales; a Open Library se envían sus nombres y el idioma.
- La ficha de cada libro de Open Library tiene «Ver en Open Library».
- Las novedades guardadas de Google Books se descartan y se vuelven a pedir.

Las novedades de Open Library son menos completas que las de Google Books para libros
muy recientes, sobre todo en español; se siguen mostrando solo con portada.

## Diccionario sin la RAE API

- Las definiciones salen solo de Wiktionary en el idioma de la app.
- En español se mantiene el botón «Consultar RAE», que abre la web oficial (dle.rae.es).
- La caché de definiciones se renueva para no mostrar las antiguas de la RAE API.

## Licencias de Wikipedia y Wiktionary (CC BY-SA 4.0)

Su licencia permite el uso comercial si se cita la fuente y se enlaza:
- Argumentos y biografías de Wikipedia muestran «Fuente: Wikipedia · CC BY-SA 4.0 ↗» y
  abren la Wikipedia al tocarlo.
- El diccionario muestra «Definiciones de Wikcionario · CC BY-SA 4.0», que abre la entrada.
- Información legal, políticas de privacidad (6 idiomas), seguridad de datos, avisos de
  terceros y fichas de Google Play actualizados.

## Diseño

- Tarjetas de la galería y de «Continuar leyendo» con esquinas redondeadas y sombra suave.
- Las portadas de Novedades ocupan toda la parte superior de la tarjeta, como en una librería.

## Pruebas

Nuevas pruebas de fechas de Open Library, novedades (portada, editorial y año), consulta por
editorial y géneros → temas de Open Library. Se retiran las de Google Books.
