# Cambios de la versión 1.0.86 (lector en modo oscuro y novedades del país)

## 0. Márgenes y espaciado del lector

Al mover los deslizadores de «Márgenes» o «Espaciado entre líneas», la hoja de ajustes se
cerraba sola. Cada pequeño movimiento volvía a paginar todo el libro, y mientras tanto las
páginas se vaciaban; la hoja dependía de ellas y desaparecía. Ahora:

- Mientras arrastras solo cambia el número mostrado; el libro se repagina **al soltar**.
- Durante la repaginación se siguen mostrando las páginas anteriores, así que nada se cierra.

## 1. Menús del lector legibles con la app en modo oscuro

El lector tiene sus propios colores (día, sepia y noche), pero sus menús heredaban la
paleta oscura de la app: textos claros sobre el panel crema, casi invisibles
(«Volver a la biblioteca», «Leer en voz alta», «Márgenes»…). Ahora el lector usa una
paleta propia, calculada a partir de su fondo y su texto, para el panel lateral, la hoja
de ajustes, los diálogos, los deslizadores y los botones. El panel lateral, que siempre
es crema, usa siempre la paleta clara. «Márgenes» y «Espaciado entre líneas» se ven con
el mismo contraste que el resto de opciones, y «Sistema»/«Siempre» ya se traducen.

## 2. Sin botón «Explorar» en Inicio

El botón junto a «Descubre tu próxima lectura» llevaba a una pantalla que no aportaba
nada nuevo y se ha quitado. Al tocar un libro recomendado se sigue abriendo su ficha.

## 3. Novedades del país, de editoriales conocidas y con portada

- Antes se pedían a Google Books «los libros de ficción más nuevos», que en la práctica
  son autopublicaciones sin portada y casi siempre en inglés (y, si fallaba, Open Library,
  todavía peor).
- Ahora se piden las últimas novedades de las **grandes editoriales del país** del
  usuario, en su idioma:
  - España: Planeta, Alfaguara, Destino, Seix Barral, Anagrama, Tusquets, Salamandra…
  - Latinoamérica: Planeta, Alfaguara, Penguin Random House, Océano…
  - EE. UU., Reino Unido, Francia, Alemania, Italia, Brasil y Portugal: sus grandes
    sellos (Knopf, Bloomsbury, Gallimard, Suhrkamp, Mondadori, Companhia das Letras,
    Porto Editora…).
- Cada día se consultan ocho editoriales, rotando, y el resultado se reparte entre ellas.
- Solo se muestran libros **con portada**, de la editorial pedida, en el idioma del país,
  y se descartan resúmenes, cuadernos, agendas y packs.
- El país sale de la tarjeta SIM o de la red móvil (sin permisos) y, si no hay, de la
  región del sistema. Antes se usaba el idioma, que al elegirlo dentro de la app podía
  quedarse sin país. En Suiza, Bélgica y Canadá decide el idioma de la app.
- Las recomendaciones de Inicio tampoco muestran tarjetas sin portada.
- Las fechas de lanzamiento se muestran en el idioma de la app.

## 3 bis. «Ver más» novedades

Junto al título «Novedades» de Inicio hay un botón **Ver más** que abre una pantalla
completa con muchas más novedades: se consultan **todas** las grandes editoriales del
país (hasta 40 libros de cada una), con filtros «Todas», «A la venta» y «Próximamente»,
y en una cuadrícula que se adapta a tablets. Se guarda 6 horas para no gastar cupo.

## 3 ter. Atribución de Google Books y clave opcional

- Las condiciones de Google Books piden atribución y un enlace a la ficha de cada libro:
  la ficha de un libro de Google Books tiene ahora «Ver en Google Books» e «Información de
  Google Books», y la lista completa también indica la fuente.
- La app admite una **clave propia de Google Books** desde el secreto de GitHub
  `GOOGLE_BOOKS_API_KEY` (opcional; sin él compila igual). Envía las cabeceras que Google
  usa para limitar la clave a esta app. Pasos en `docs/GOOGLE_BOOKS_API.md`.

## 4. APK más ligera

El escáner de ISBN alternativo usaba la versión de ML Kit que mete el modelo de códigos
de barras dentro de la app (unos 8 MB). Ahora usa la versión que lo toma de Google Play
services, con la misma función.

## Privacidad

Las políticas de privacidad (seis idiomas) y `DATA_SAFETY_ES.md` explican que la consulta
de novedades incluye el código del país.

## Pruebas nuevas

`ReleaseMarketsTest` (mercado por país, rotación de editoriales, filtros) y una prueba de
`launchNewsFromGoogleBooks` que exige portada, editorial e idioma.
