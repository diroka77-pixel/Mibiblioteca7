# Cambios de la versión 1.0.90 (paso de página sin líneas repetidas)

## El problema

Al pasar de página, las dos últimas líneas de la página anterior podían volver a
aparecer arriba (o quedar cortadas abajo). Había varias causas que se sumaban:

1. **El alto de la página cambiaba mientras se leía.** La línea con el nombre del
   capítulo, bajo el texto, aparecía o desaparecía según el capítulo. Cada cambio de alto
   volvía a paginar el libro y la app buscaba «la página que contiene la posición
   guardada», que con el nuevo reparto empezaba unas líneas antes.
2. **El texto dibujado no cortaba las líneas igual que el cálculo de páginas.** El TextView
   de Android usa por defecto otro algoritmo de corte, guiones y alto extra para letras
   de fuentes de respaldo; a veces un párrafo ocupaba una línea más de lo calculado.
3. **Espacios sin contar:** los 2 dp de margen arriba y abajo de la página y el espacio de
   28 dp sobre los capítulos (se reservaban 12).
4. Los títulos usaban un tamaño fijo (+4/+8) al dibujarlos y uno proporcional al calcular.

## La solución

- La línea del capítulo ocupa siempre su sitio, aunque esté vacía: el alto ya no cambia.
- Al paginar se fuerza un salto de página en la línea que se está leyendo, así que, si hay
  que volver a paginar (girar el móvil, cambiar la letra o los márgenes), la página actual
  empieza exactamente donde estaba y no se repite nada.
- El texto se dibuja con el mismo corte de líneas que el cálculo (sin guiones ni alto de
  respaldo).
- Se descuentan los márgenes de la página y se reserva el espacio real de los capítulos.
- Títulos con el mismo tamaño en el cálculo y en pantalla.

## Versión Android

- `versionCode 190`.
- `versionName 1.0.90`.
