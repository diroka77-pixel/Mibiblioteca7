# MiBiblioteca v0.74.0

Proyecto Android de MiBiblioteca. El código fuente está en `app/` y GitHub Actions genera la APK de prueba.

El lector integrado abre EPUB, PDF, TXT, Markdown, HTML, RTF, DOCX y MOBI clásico (PalmDOC sin comprimir o comprimido). Guarda la posición y el porcentaje por archivo; los datos de lectura se incluyen en el respaldo de la biblioteca. EPUB y DOCX muestran el texto extraído, por lo que imágenes, diseño complejo y algunas notas no se reproducen. Los MOBI con compresión HuffCDIC y AZW3 requieren conversión a EPUB. Los PDF protegidos con contraseña no se abren con el renderizador del sistema.

El lector respeta las zonas seguras de Android, permite alternar entre tema claro y oscuro y ajustar el brillo de lectura. Los bordes y los gestos horizontales avanzan o retroceden una pantalla de texto o una página PDF. El panel lateral incluye capítulos, páginas PDF y subrayados; la selección de texto permite resaltar, compartir una cita o consultar el diccionario del DLE/RAE con Wikcionario como alternativa. Los subrayados se guardan por archivo y se incluyen en el respaldo.

## Cambios de esta versión

- Diccionario integrado con definiciones RAE mediante una API comunitaria y Wikcionario como alternativa, caché local de 30 días y enlace directo a la entrada oficial. El servicio comunitario limita el uso gratuito anónimo a 100 consultas diarias.
- Reanudación desde el último carácter notificado por la voz. Los callbacks se invalidan al pausar o cambiar la velocidad, y las actualizaciones visuales y de almacenamiento se limitan para reducir tirones.
- Menú de selección simplificado a cinco acciones: subrayar, diccionario, quitar subrayado, nota y más opciones. Las acciones secundarias siguen disponibles en «Más opciones».
- Control de velocidad de voz ajustable entre 0,5× y 2,0×, guardado entre sesiones y aplicable durante la lectura.
- Android versionCode 74 y versionName 0.74.0. GitHub Actions genera el artefacto `MiBiblioteca-v0.74-firmada`.
- La locución se encola palabra por palabra. Antes de reproducir cada palabra se guarda su posición exacta; pausar y continuar vuelve a iniciar esa palabra, incluso si el motor no informa rangos de voz.

## Protección de archivos y biblioteca

- Copias locales de libros: máximo 256 MiB y 32 MiB de espacio libre reservado. Archivos parciales se eliminan si falla la copia.
- Lectura de EPUB/DOCX/texto: índice de 1 MiB, capítulos de 3 MiB, máximo 16 MiB de texto, 2000 capítulos y 20000 entradas ZIP.
- Respaldos JSON: máximo 32 MiB, 10000 libros, 8 niveles de JSON, campos de texto de 128000 caracteres y portadas de 1 MB. Se comprueban referencias, estados, posiciones, anotaciones y portadas antes de restaurar datos.
- Una sincronización conserva las fichas no confirmadas en Drive. La eliminación explícita dentro de la app sigue disponible; un listado incompleto no demuestra que un archivo se haya eliminado.
- Se conservan favoritos y notas cambiados durante el escaneo y la posición exacta de lectura en respaldos nuevos. Respaldos anteriores siguen siendo compatibles.
- Datos locales privados excluidos de copias automáticas y transferencias de Android. Se mantienen los respaldos explícitos y el respaldo en la carpeta seleccionada. Los JSON explícitos no están cifrados: quien tenga acceso a esa carpeta puede leerlos.
- Portadas locales se escriben con AtomicFile. Drive conserva una generación anterior en `MiBiblioteca_Diroka77.json.previous` y la usa si la principal no es válida. El proveedor de documentos no garantiza escritura atómica; el catálogo local sigue disponible ante fallos de Drive.
- Validación: workflow `Validar seguridad y biblioteca`, sin claves de firma ni publicación de releases. Las pruebas del teléfono (Drive sin conexión, actualización conservando datos y falta de espacio) requieren un dispositivo real.
