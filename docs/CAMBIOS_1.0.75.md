# Cambios de la versión 1.0.75 (preparación comercial)

## Hecho en esta versión

- **Nombre en un solo sitio.** El nombre de la app está en `AppBrand.NAME`
  (`app/src/main/java/com/diego/mibiblioteca/AppBrand.kt`) y en `app_name`
  (`app/src/main/res/values/strings.xml`, más dos accesos directos en ese mismo
  archivo). El icono del móvil y el widget ya muestran `app_name`, no «Mi Biblioteca».
  Desaparece la mezcla de «BookNook» y «MiBiblioteca» en las pantallas.
- **Sin datos personales a la vista.** Los libros sin autor muestran
  «Autor desconocido» en lugar de «Biblioteca de Diroka77». Los datos guardados
  por versiones anteriores se siguen reconociendo.
- **Sin apaños para libros concretos.** Se eliminan la ficha fija de
  *Hija del cielo* (Rodrigo Cortés) y la conversión «Antonia Scott + Jon
  Gutiérrez» → «Reina Roja».
- **Sin noticias fijas caducadas.** Se elimina la lista de 3 artículos de
  septiembre de 2026, que además ya no se usaba.
- **Sin leer buscadores por detrás.** La búsqueda de portadas ya no descarga en
  segundo plano resultados de Google, Bing, DuckDuckGo ni la búsqueda de
  Goodreads. Las portadas siguen llegando de Open Library y Google Books, y de la
  página de Goodreads que el usuario vincule. Los botones que *abren* Google en
  el navegador siguen igual.
- **Identificación de red coherente.** Todas las conexiones usan
  `AppBrand.API_USER_AGENT` o `AppBrand.WEB_USER_AGENT`, en lugar de cinco
  versiones distintas (0.39, 0.51, 0.72…).
- **Davefx sin enlace a .apk.** Se abre la página del proyecto sherpa-onnx en
  lugar de descargar directamente un .apk de HuggingFace (Google Play lo penaliza).
- **Privacidad honesta.** Los textos ya no dicen «sin seguimiento». Ahora explican
  que Google ML Kit puede enviar a Google datos técnicos de uso. También se
  actualizan `PRIVACIDAD.md` y `DATA_SAFETY_ES.md`.
- **Menos tamaño.** Solo se incluyen los procesadores de móviles (ARM). La APK
  baja de ~94 MB a unos 45-50 MB.
- **Paquete para Google Play.** GitHub Actions genera ahora también el `.aab`
  (artefacto «…-google-play-aab»), que es el formato que pide Play.

## Pendiente (requiere más trabajo)

1. Elegir un nombre definitivo distinto de «BookNook» (ya existen apps con ese nombre).
2. Pasar los textos de las pantallas a `strings.xml` para poder traducir la app (inglés primero).
3. Permitir probar la app sin conectar Google Drive (libros de ejemplo de dominio público).
4. Revisar las otras fuentes que se leen como páginas web (Casa del Libro, Goodreads,
   editoriales) y sustituirlas por fuentes con API o enlaces que abra el usuario.
5. Dividir `MainActivity.kt` (unas 8.000 líneas) en archivos por pantalla.
