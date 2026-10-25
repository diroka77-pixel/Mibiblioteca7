# Cambios de la versión 1.0.80 (solo fuentes abiertas y oficiales)

La app ya no descarga páginas web de terceros para extraer datos. Solo usa APIs
públicas y oficiales; las demás webs se abren únicamente cuando el usuario pulsa un
botón, como haría con el navegador.

## Fuentes que se usan

- **Open Library**: portadas, fichas, autores y sagas.
- **Wikipedia, Wikidata y Wikcionario** (API oficial): biografías, argumentos y definiciones.
- **Google Books** (API oficial): datos de libros y, ahora, las novedades y próximos lanzamientos.
- **Project Gutenberg**: clásicos gratuitos de dominio público.

## Lo que se ha quitado

- **Goodreads**: ya no se busca el libro ni se lee el argumento o la portada de sus
  páginas. El botón «Goodreads» abre el libro en su app o en el navegador (por el
  enlace guardado, por ISBN o con su buscador).
- **Casa del Libro**: «Próximos lanzamientos» ya no lee su web. Ahora la sección se
  llama «Novedades y próximos lanzamientos» y sale de la API de Google Books
  (ficción en español publicada en los últimos cuatro meses o anunciada para el
  próximo año). El botón para abrir Casa del Libro se mantiene.
- **YouTube**: ya no se lee su página de resultados para incrustar un vídeo. La ficha
  tiene un botón «Buscar tráileres y presentaciones en YouTube» que abre la búsqueda.
- Código antiguo sin uso que leía Bing Noticias y webs de librerías y editoriales.

## Efecto para el usuario

Algunos libros encontrarán menos argumentos o portadas automáticas que antes, porque
Goodreads tenía muchos datos. Siempre se pueden completar a mano con «Editar datos».
