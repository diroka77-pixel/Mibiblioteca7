# Cambios de la versión 1.0.83 (idioma y novedades)

## Selector de idioma dentro de la app

- Menú → Ajustes → **Idioma**: Automático (idioma del teléfono), Español, English,
  Português, Français, Deutsch o Italiano. Se recuerda al cerrar la app.
- En Android 13 o posterior el cambio se comunica también al sistema.
- Antes solo existía el selector de Android (Ajustes del sistema → Aplicaciones →
  Bookhollow → Idioma), que no aparece en todos los móviles.

## Novedades y próximos lanzamientos

Problema: no aparecía ninguna. Google Books, sin clave propia, tiene un cupo diario de
consultas gratuito que la app ya agota con las fichas de los libros; además, el filtro
descartaba los libros sin portada o con solo el año de publicación, y un intento fallido
no se repetía hasta pasadas 12 horas.

Solución:
- Se aceptan libros sin portada y con solo el año (se muestra el año).
- Si Google Books no responde o trae pocas, se completa con **Open Library** (abierta y
  sin cupo diario), en el idioma de la app.
- Si no llega nada, se vuelve a intentar la próxima vez que se abra la app.
- Hasta 12 novedades en lugar de 8.

## Otros

- Tamaños de texto (Normal, Grande, Muy grande) y el aviso de eliminar sección, traducidos.
