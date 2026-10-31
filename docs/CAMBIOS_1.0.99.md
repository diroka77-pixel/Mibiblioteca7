# Cambios de la versión 1.0.99

- **Últimos añadidos**: nueva estantería en Inicio (entre «Continuar leyendo» y los clásicos) con
  los cinco libros más recientes. Al tocar uno se abre su ficha; pulsación larga, menú del libro.
- Cómo se decide el orden (`RecentlyAdded.kt`): la app anota cuándo ve cada libro por primera vez
  (al sincronizar la carpeta o al importar un libro o un clásico). Para los libros que ya estaban, o
  los que entraron juntos en la primera sincronización, ordena por la fecha del archivo en la carpeta.
- Las fechas se guardan en el teléfono (`added_dates`) y solo las de libros que siguen en la biblioteca.
