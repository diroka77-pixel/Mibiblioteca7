# Bookhollow (antes MiBiblioteca)

## Versión 1.0.82
- Escáner de ISBN nuevo (Google Play services) y búsqueda del libro escaneado. Detalles en `docs/CAMBIOS_1.0.82.md`.

## Versión 1.0.81
- App y ficha de Google Play en seis idiomas. Detalles en `docs/CAMBIOS_1.0.81.md`.

## Versión 1.0.80
- Solo fuentes abiertas y oficiales: fuera la lectura oculta de Goodreads, Casa del Libro y YouTube. Detalles en `docs/CAMBIOS_1.0.80.md`.

## Versión 1.0.79
- Icono nuevo y cinco clásicos gratuitos de dominio público. Detalles en `docs/CAMBIOS_1.0.79.md`.

## Versión 1.0.78
- Corrección del modo oscuro (fondos blancos fijos) y letra más legible en la barra inferior. Detalles en `docs/CAMBIOS_1.0.78.md`.

## Versión 1.0.77
- Modo oscuro (Automático / Claro / Oscuro) en Ajustes. Detalles en `docs/CAMBIOS_1.0.77.md`.

## Versión 1.0.76
- Preparada para Android 16 (API 36), requisito actual de Google Play. Detalles en `docs/CAMBIOS_1.0.76.md`.

## Versión 1.0.75
- Nuevo nombre comercial: Bookhollow. Detalles en `docs/CAMBIOS_1.0.75.md`.
- Sin datos personales ni contenidos fijos en el código, sin lectura oculta de buscadores, textos de privacidad corregidos, APK más ligera y paquete AAB para Google Play.

## Versión 1.0.63
- TalkBack identifica las zonas para avanzar y retroceder página, los controles de voz y los ajustes del lector.
- El progreso de lectura, las descargas, la sincronización y el mantenimiento anuncian su estado.
- Los objetivos táctiles principales del lector respetan un mínimo de 48 dp.
- La cabecera, la navegación inferior y el selector Libros/Géneros/Secciones admiten mejor el texto grande.

## Versión 1.0.62
- Pantalla de novedades al actualizar, que puede volver a abrirse desde el menú.
- Información legal y atribuciones de componentes incluidos y servicios externos.
- Avisos diferenciados para AndroidX, Gson, jsoup, ML Kit, fuentes de libros, Wikimedia, RAE y Davefx.

## Versión 1.0.61
- Nuevo centro de ayuda en castellano para configurar Drive, importar libros, usar el lector por voz y recuperar una biblioteca.
- La guía inicial puede volver a abrirse desde el menú en cualquier momento.
- Se explican dentro de la aplicación los formatos compatibles y las limitaciones de AZW3, MOBI y PDF protegidos.

## Versión 1.0.60
- Nueva pantalla «Acerca de y soporte» con versión instalada, estado de permisos opcionales y resumen técnico.
- Informe de diagnóstico compartible sin títulos, notas, rutas ni contenido de los libros.
- El diagnóstico reúne sincronización, rendimiento de la sesión y cifras generales para facilitar la resolución de incidencias.

## Versión 1.0.59
- Nuevo panel «Privacidad y datos» con información clara sobre almacenamiento, red y permisos opcionales.
- Eliminado el permiso para instalar paquetes: la descarga opcional de Davefx se abre ahora en el navegador.
- Reducida la visibilidad de aplicaciones externas y desactivado explícitamente el código de depuración en la compilación pública.
- Activada la eliminación de recursos no utilizados para reducir el tamaño final sin afectar a la biblioteca.

## Versión 1.0.58
- Estado de la biblioteca realiza una revisión incremental y no destructiva de cada referencia del catálogo.
- Detecta archivos inaccesibles, referencias repetidas, copias posibles, archivos vacíos o incompatibles y tamaños aún no confirmados por Drive.
- Comprueba portadas ausentes, dañadas o con resolución demasiado pequeña.
- Permite volver a comprobar, revisar archivos o duplicados, reparar portadas, completar fichas y buscar cambios en Drive. Nunca elimina libros automáticamente.

## Versión 1.0.34
- El escaneo usa primero el listado estándar del selector de Android y evita esperar a la consulta de Drive que se queda bloqueada en cero.

## Versión 1.0.33
- Si la consulta rápida de Google Drive falla, el escaneo cambia automáticamente al listado estándar de DocumentFile, el mismo acceso que puede leer el respaldo.
- El catálogo restaurado se conserva mientras se buscan incorporaciones nuevas.

## Versión 1.0.32
- La lectura de Drive ya no se bloquea por un falso `canRead=false` del proveedor de documentos.

## Versión 1.0.31
- Detecta permisos caducados de Drive y elimina la ruta inválida para pedir una nueva selección.

## Versión 1.0.30
- Se elimina el bloqueo que impedía volver a seleccionar la carpeta de Drive cuando había una biblioteca existente en el teléfono.
- La sincronización conserva el catálogo y los datos personales, y añade los libros que encuentre en la carpeta seleccionada.

## Versión 1.0.29
- La sincronización al abrir la aplicación hace una única pasada rápida por Drive y reutiliza los metadatos locales; ya no vuelve a buscar portadas automáticamente.
- Las portadas pendientes solo se buscan desde el botón manual del panel lateral.

## Versión 1.0.28
- La búsqueda ya no devuelve resultados por palabras comunes aisladas; exige que los términos coincidan completos en el título, autor o saga.

## Versión 1.0.27
- La consulta de Drive acumula páginas sucesivas en vez de quedarse con la última página devuelta por el proveedor.
- La biblioteca vuelve a sincronizarse automáticamente al abrirse si ya existe una carpeta guardada.

## Versión 1.0.26
- Drive ya no descarta archivos sin extensión visible cuando la carpeta contiene libros; los intenta analizar como EPUB.

## Versión 1.0.25
- La sincronización muestra un aviso con los títulos nuevos detectados en Drive.
- El botón de actualizar fuerza la lectura completa de la carpeta guardada.

## Versión 1.0.24
- La sincronización combina la consulta directa de Drive con la lista del selector de documentos para detectar archivos recién incorporados.

## Versión 1.0.23
- La aplicación ya no busca portadas automáticamente al abrirse; se mantienen en el botón manual de Ajustes.
- La sincronización reconoce EPUB y otros libros por extensión y por tipo MIME de Drive para incorporar archivos nuevos aunque Drive no muestre la extensión.

## Versión 1.0.22
- Sincronización de Drive con una segunda lectura independiente para detectar libros recién añadidos aunque el proveedor devuelva temporalmente un listado antiguo.
- Acción para añadir o quitar libros de favoritos desde el menú contextual.

# MiBiblioteca v1.0.21

## Cambios de la versión 1.0.21

- La búsqueda evita ordenar dos veces el catálogo completo y recurre a tolerancia de errores solo cuando no hay coincidencias directas.
- El índice de búsqueda normaliza una ficha solo cuando cambia y calcula los términos una sola vez por consulta.
- La sincronización consulta el listado de documentos de Drive y vuelve a consultarlo mientras el proveedor indica que está cargando.
- Se corrige el bucle de reintento para terminar tras una lectura completa y se evita volver a descargar EPUB si Drive no proporciona datos de tamaño o fecha.

## Cambios de la versión 0.96

- La caché de portadas identifica la imagen por su contenido y no por la identidad temporal del array de bytes. Se reducen recargas al cambiar de pantalla y se invalida correctamente una portada sustituida.
- Se conserva el ajuste completo de las portadas (`Fit`) en galerías y fichas para evitar recortes o deformaciones.

Proyecto Android de MiBiblioteca. Las portadas importadas se limitan antes de decodificarse y Dependabot propone actualizaciones semanales para Gradle y GitHub Actions. Los grupos se conservan al cambiar entre pantallas y cada sección mantiene su propia posición de desplazamiento. La vista de géneros conserva sus grupos al cambiar filtros, permite buscar y seleccionar géneros desde un selector compacto y carga portadas ajustadas al tamaño visible para agilizar la navegación. El filtro de géneros desplaza su propia lista y aplica la selección al terminar; los accesos externos están centrados y se reutilizan grupos y portadas en cambios de pantalla. El código fuente está en `app/`. GitHub Actions compila, verifica y guarda la APK firmada como artefacto de la ejecución.

## Cambios de la versión 0.95

- Las acciones de sección y «Quiero leer» de la ficha quedan separadas y alineadas a todo el ancho.
- El gesto lateral entre pantallas principales funciona también sobre la cabecera, el buscador y la navegación inferior.
- Los carruseles horizontales y el desplazamiento vertical mantienen la prioridad de sus propios gestos.

## Cambios de la versión 0.94

- Alturas, rellenos y esquinas de los botones principales quedan unificados.
- Goodreads y Google pasan a acciones secundarias delineadas en la ficha del libro.
- Borrar el archivo de Drive se distingue como acción de riesgo.
- El encabezado de la ficha usa el mismo marrón suave de las pantallas principales.
- Los paneles de argumento, autor y observaciones se abren con una transición suave e iconos consistentes.
- La navegación inferior ajusta las etiquetas para evitar amontonamientos.

## Cambios de la versión 0.93

- Los libros descargados muestran un distintivo de disponibilidad sin conexión en listas, galerías y carruseles.
- La ficha informa del nombre y tamaño de la copia guardada y permite borrar solo esa copia local.
- El menú de pulsación larga ofrece descargar o retirar la copia sin conexión.
- Cada cambio manual de página guarda el punto exacto para reanudar la lectura.
- La lectura por voz conserva la última palabra comunicada por Android al cerrar el lector.

## Cambios de la versión 0.92

- La pantalla sigue el dedo durante toda la anchura y la siguiente se prepara fuera de la zona visible antes de entrar.
- Los resultados anteriores permanecen dibujados mientras se recalculan filtros, evitando que las filas se contraigan y las portadas salten.
- La decodificación de portadas usa una cola pequeña para no saturar el procesador durante el desplazamiento.
- La medición interna de fotogramas incluye Géneros y Favoritos.

## Cambios de la versión 0.91

- El selector «Vista» conserva su forma y queda en una fila propia al filtrar géneros.
- El gesto lateral funciona desde las portadas de las tarjetas normales y responde antes; los carruseles horizontales conservan su desplazamiento.
- El cambio de pantalla se desvanece completamente antes de sustituir el contenido, para evitar el salto visible desde Inicio.

## Cambios de la versión 0.90

- El gesto lateral acompaña al dedo al navegar entre pantallas y respeta los carruseles de libros.
- Las tarjetas reservan una altura estable y las portadas comparten caché entre tamaños próximos.
- Goodreads y Google IA tienen botones centrados de mayor tamaño.
- Descargar guarda el libro directamente en Descargas con porcentaje en Android 10 y posteriores. El lector usa esta copia si Drive no está disponible y se ha perdido la caché temporal. En Android 8 y 9 se conserva el selector del sistema.

El lector integrado abre EPUB, PDF, TXT, Markdown, HTML, RTF, DOCX y MOBI clásico (PalmDOC sin comprimir o comprimido). Guarda la posición y el porcentaje por archivo; los datos de lectura se incluyen en el respaldo de la biblioteca. EPUB y DOCX muestran el texto extraído, por lo que imágenes, diseño complejo y algunas notas no se reproducen. Los MOBI con compresión HuffCDIC y AZW3 requieren conversión a EPUB. Los PDF protegidos con contraseña no se abren con el renderizador del sistema.

El lector respeta las zonas seguras de Android, permite alternar entre tema claro y oscuro y ajustar el brillo de lectura. Los bordes y los gestos horizontales avanzan o retroceden una pantalla de texto o una página PDF. El panel lateral incluye capítulos, páginas PDF y subrayados; la selección de texto permite resaltar, compartir una cita o consultar el diccionario del DLE/RAE con Wikcionario como alternativa. Los subrayados se guardan por archivo y se incluyen en el respaldo.

## Cambios de la versión 0.88

- Las opciones de filtro de géneros solo se calculan al entrar en esa pantalla.
- El buscador filtra la lista en cada pulsación, vuelve al inicio de resultados y permite borrar el texto con un botón.
- Añadidos controles explícitos «Marcar todos» y «Desmarcar todos», con estado para mostrar ninguno, algunos o todos los géneros.
- Se aplazó el cálculo de duplicados hasta abrir una ficha o pedir la búsqueda.
- Transiciones entre secciones más breves y menos intrusivas.
- Android versionCode 88 y versionName 0.88.0.

## Cambios de la versión 0.87

- La vista de géneros evita crear parejas de todos los libros antes de desplazarse.
- Las miniaturas se decodifican a una fracción del tamaño visible para acelerar carga y desplazamiento, y mantener más portadas en caché.
- La agrupación de géneros reduce asignaciones temporales y reutiliza las etiquetas ya normalizadas.
- Android versionCode 87 y versionName 0.87.0.

## Cambios de la versión 0.86

- Selector compacto y buscable para filtrar géneros sin una fila interminable de botones.
- Etiqueta «Vista» colocada junto a los controles para cambiar entre carrusel horizontal y lista vertical.
- Transiciones más suaves entre pestañas y secciones, con menos desplazamiento y un fundido más progresivo.
- Cálculo de géneros en una sola pasada por los libros visibles.
- Android versionCode 86 y versionName 0.86.0.

## Cambios de la versión 0.85

- Transición entre secciones más corta y ligera.
- Selector de orientación del carrusel reducido a dos botones de icono accesibles.
- Las portadas se decodifican a un tamaño más cercano al visible y se conserva una caché adaptable para reducir nuevas decodificaciones al desplazarse.
- Android versionCode 85 y versionName 0.85.0.

## Cambios de la versión 0.84

- Los gestos horizontales del carrusel de géneros ya no cambian de pantalla; la navegación global solo responde cuando ningún componente desplazable ha consumido el gesto.
- El filtrado de libros y el cálculo de géneros se procesan en segundo plano para que los botones y las transiciones respondan con mayor rapidez en bibliotecas grandes.
- Android versionCode 84 y versionName 0.84.0.

## Cambios de la versión 0.79

- Eliminada la agrupación «Otros»: etiquetas no reconocidas pasan a «Sin clasificar».
- Añadida una fila horizontal de géneros para saltar directamente a cada grupo.

## Cambios de la versión 0.78

- Agrupación de libros por género; una obra con varias etiquetas aparece en todos sus grupos.
- Búsqueda puntual en Google Libros y Open Library desde la ficha; géneros editables y guardados en el catálogo y el respaldo.
- Correcciones del lector para quitar subrayados desde el menú de selección y cerrar el panel lateral con un gesto hacia la izquierda.

## Cambios de la versión 0.76

- Reanudación del lector por voz desde la palabra actual al pausar, en lugar de reiniciar desde el principio de la página.
- Menú de selección reducido a cinco acciones: subrayar (que también quita el subrayado existente), nota, copiar, fijar y diccionario.
- Panel de diccionario actualizado con pronunciación, definiciones RAE/Wikcionario y acceso a diccionario español del sistema.
- Mantiene control de velocidad de voz, consulta de definiciones y mejoras acumuladas en versiones anteriores.
- Android versionCode 76 y versionName 0.76.0.
- Cierre del panel lateral del lector deslizando a la izquierda.
- Barra de selección con prioridad para «Subrayar/Quitar subrayado» y «Diccionario».
- Menú lateral simplificado y tarjetas de noticias más compactas, sin imagen repetida. La APK firmada está en [Releases](https://github.com/diroka77-pixel/Mibiblioteca7/releases/latest).

## Protección de archivos y biblioteca

- Copias locales de libros: máximo 256 MiB y 32 MiB de espacio libre reservado. Archivos parciales se eliminan si falla la copia.
- Lectura de EPUB/DOCX/texto: índice de 1 MiB, capítulos de 3 MiB, máximo 16 MiB de texto, 2000 capítulos y 20000 entradas ZIP.
- Respaldos JSON: máximo 32 MiB, 10000 libros, 8 niveles de JSON, campos de texto de 128000 caracteres y portadas de 1 MB. Se comprueban referencias, estados, posiciones, anotaciones y portadas antes de restaurar datos.
- Una sincronización conserva las fichas no confirmadas en Drive. La eliminación explícita dentro de la app sigue disponible; un listado incompleto no demuestra que un archivo se haya eliminado.
- Se conservan favoritos y notas cambiados durante el escaneo y la posición exacta de lectura en respaldos nuevos. Respaldos anteriores siguen siendo compatibles.
- Datos locales privados excluidos de copias automáticas y transferencias de Android. Se mantienen los respaldos explícitos y el respaldo en la carpeta seleccionada. Los JSON explícitos no están cifrados: quien tenga acceso a esa carpeta puede leerlos.
- Portadas locales se escriben con AtomicFile. Drive conserva una generación anterior en `MiBiblioteca_Diroka77.json.previous` y la usa si la principal no es válida. El proveedor de documentos no garantiza escritura atómica; el catálogo local sigue disponible ante fallos de Drive.
- Las pruebas de CI cubren compilación y validaciones automatizadas. Las pruebas del teléfono (Drive sin conexión, actualización conservando datos, pausa/reanudación de voz y falta de espacio) requieren un dispositivo real.
