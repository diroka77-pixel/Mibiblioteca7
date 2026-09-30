# MiBiblioteca

Proyecto Android de MiBiblioteca. El código fuente está en `app/` y GitHub Actions genera la APK de prueba.

El lector integrado abre EPUB, PDF, TXT, Markdown, HTML, RTF, DOCX y MOBI clásico (PalmDOC sin comprimir o comprimido). Guarda la posición y el porcentaje por archivo; los datos de lectura se incluyen en el respaldo de la biblioteca. EPUB y DOCX muestran el texto extraído, por lo que imágenes, diseño complejo y algunas notas no se reproducen. Los MOBI con compresión HuffCDIC y AZW3 requieren conversión a EPUB. Los PDF protegidos con contraseña no se abren con el renderizador del sistema.

El lector respeta las zonas seguras de Android, permite alternar entre tema claro y oscuro y ajustar el brillo de lectura. Los bordes y los gestos horizontales avanzan o retroceden una pantalla de texto o una página PDF. El panel lateral incluye capítulos, páginas PDF y subrayados; la selección de texto permite resaltar, compartir una cita o consultar una palabra en el DLE. Los subrayados se guardan por archivo y se incluyen en el respaldo.
