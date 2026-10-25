# Cambios de la versión 1.0.77 (modo oscuro)

## Hecho en esta versión

- **Modo oscuro en toda la biblioteca.** Nuevo ajuste en el menú → Ajustes →
  «Tema». Cada pulsación cambia entre **Automático** (sigue el modo oscuro del
  móvil), **Claro** y **Oscuro**. Se recuerda al cerrar la app.
- Las tarjetas, fichas, menús, diálogos, estadísticas, buscadores y portadas sin
  imagen usan tonos marrón oscuro cálidos, en línea con los fondos ilustrados.
- Los textos de aviso (sagas incompletas) y el botón de borrar mantienen buen
  contraste en los dos modos.
- **El modo claro no cambia:** usa exactamente los mismos colores que antes.
- El lector conserva sus propios temas (Día, Sepia, Noche).
- Pruebas automáticas nuevas para el selector de tema (`AppThemeTest`).

## Qué revisar en el móvil

1. Menú → Ajustes → Tema → Oscuro, y recorrer Inicio, Biblioteca, Secciones,
   Favoritos, Estadísticas, la ficha de un libro y los menús.
2. Si algún texto se lee mal (oscuro sobre oscuro o claro sobre claro), hacer una
   captura: cada color está en un solo sitio (principio de `MainActivity.kt`,
   bloque «Paleta de la biblioteca») y se corrige en una línea.
