package com.diego.mibiblioteca

internal data class LibraryHelpSection(val title: String, val body: String)

internal fun libraryHelpSections(): List<LibraryHelpSection> = listOf(
    LibraryHelpSection(
        "Añadir tu biblioteca",
        "Elige la carpeta de libros desde Ajustes. Android te pedirá confirmar «Usar esta carpeta». ${AppBrand.NAME} conservará el acceso y podrás buscar cambios con el botón de sincronización."
    ),
    LibraryHelpSection(
        "Formatos compatibles",
        "Puedes catalogar y abrir EPUB, PDF, MOBI clásico, AZW y FB2. Los AZW3 y algunos MOBI con compresión Kindle necesitan convertirse a EPUB. Los PDF protegidos con contraseña no pueden abrirse."
    ),
    LibraryHelpSection(
        "Importar un solo libro",
        "Usa «Importar un libro» o abre el archivo con ${AppBrand.NAME} desde Android. El catálogo existente se conserva y el libro se añade como copia local."
    ),
    LibraryHelpSection(
        "Lectura en voz alta",
        "El lector utiliza las voces instaladas en Android. Si una voz deja de responder, elige otra voz sin conexión y vuelve a iniciar la reproducción. Davefx es opcional y se descarga desde el navegador."
    ),
    LibraryHelpSection(
        "Copias y recuperación",
        "Desde Opciones de biblioteca puedes exportar y restaurar un respaldo. Los archivos originales permanecen en la carpeta elegida; el respaldo guarda catálogo, progreso, notas y preferencias."
    ),
    LibraryHelpSection(
        "Si algo no aparece",
        "Pulsa sincronizar y revisa Estado de la biblioteca. Si Drive perdió la autorización, vuelve a elegir la misma carpeta. Tus datos guardados se conservan mientras recuperas el acceso."
    ),
    LibraryHelpSection(
        "Privacidad",
        "Los permisos de cámara y micrófono solo se solicitan al usar el escáner ISBN o la búsqueda por voz. La aplicación no incluye anuncios ni seguimiento de uso."
    )
)
