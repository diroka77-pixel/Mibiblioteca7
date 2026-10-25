# Cambios de la versión 1.0.85 (fuentes en tu idioma y tablets)

## 1. Wikipedia en el idioma de la app

Biografías de autores, argumentos y resúmenes se buscaban siempre en la Wikipedia en
español. Ahora se consulta la Wikipedia del **idioma de la app** (en, pt, fr, de, it, es)
y, si no hay artículo, la inglesa como respaldo. También se adaptan al idioma las palabras
de búsqueda («writer author», «Buch Roman»…), los títulos de la sección de argumento
(«Plot», «Handlung», «Trama»…) y la detección de páginas de desambiguación.
Las sagas de Wikidata usan también las etiquetas en el idioma de la app.

## 2. Diccionario del lector

- En español: primero el DLE de la RAE y después el Wikcionario, como hasta ahora.
- En los demás idiomas: el Wiktionary de ese idioma (Wiktionary, Wiktionnaire,
  Wikcionário, Wikizionario). El botón «Consultar RAE» solo aparece en español; en los
  demás idiomas abre el Wiktionary correspondiente.
- La caché de definiciones se guarda por idioma.

## 3. Clásicos gratuitos por idioma

«Añadir clásicos gratuitos» descarga cinco clásicos de dominio público de Project
Gutenberg en el idioma de la app. Cada número se ha comprobado en gutenberg.org:

| Idioma | Libros |
|---|---|
| es | Don Quijote, Lazarillo de Tormes, Marianela, Pepita Jiménez, Niebla |
| en | Pride and Prejudice, Frankenstein, Alice's Adventures in Wonderland, The Adventures of Sherlock Holmes, Dracula |
| pt | Dom Casmurro, Memórias Póstumas de Brás Cubas, Iracema, A Cidade e as Serras, Os Lusíadas |
| fr | Madame Bovary, Les Trois Mousquetaires, Notre-Dame de Paris, Le Tour du monde en quatre-vingts jours, Candide |
| de | Die Verwandlung, Faust, Die Leiden des jungen Werther, Effi Briest, Der Schimmelreiter |
| it | I promessi sposi, Le avventure di Pinocchio, La Divina Commedia, Novelle (De Amicis), I Robinson italiani |

Todos los autores murieron hace más de 80 años (dominio público también en la UE).

## 4. Google Books según el idioma y el país

Las recomendaciones y la búsqueda de datos de Google Books ya no se limitan a libros en
español ni al catálogo de España: usan el idioma de la app y el país del dispositivo.

## 5. Diseño para tablets

La galería de la biblioteca muestra más columnas según el ancho de pantalla: 2 en
móvil, 3 en tablet pequeña o móvil en horizontal, 4 en tablet y 5 en pantallas muy
anchas. El carrusel «Continuar leyendo» se adapta igual.

## Pruebas nuevas

`WikiLanguageTest`, `SampleBooksTest` (cinco clásicos en cada idioma, sin repetidos) y
columnas de la galería en `AppThemeTest`.
