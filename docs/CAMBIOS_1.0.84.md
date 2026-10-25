# Cambios de la versión 1.0.84 (tres correcciones)

## 1. La traducción automática sigue el idioma de la app

Antes, cuando una ficha, una biografía, un resumen o una frase del lector llegaban en
otro idioma, la app los traducía siempre al **español**, también para quien la usa en
inglés o francés. Ahora se traducen al **idioma de la app** (en el teléfono, con Google
ML Kit). Los textos que decían «en castellano» se han ajustado en los demás idiomas.

## 2. Sin reseñas inventadas

- La ficha pública ya no muestra una «reseña» redactada con una plantilla a partir de la
  sinopsis. La sección solo aparece si hay una reseña real.
- El botón «Obtener reseña del libro» pasa a ser **«Resumen del libro»** y muestra el
  resumen de fuentes públicas tal cual, sin frases añadidas.

## 3. Más textos traducidos

- Estados de lectura (Pendiente, Leyendo, Leído), también en TalkBack y en el CSV exportado.
- Nombres de géneros y de fondos de pantalla (se traducen al mostrarlos; internamente
  siguen en español para no afectar a los datos guardados).
- Nombres de los meses en Estadísticas.
- Textos con datos que faltaban: «Mejor mes», «Relecturas», «X libros», descargas,
  saga, estado, avisos de la biblioteca vacía…
- Pruebas nuevas: todos los géneros y estados tienen traducción.
