package com.diego.mibiblioteca

internal data class PrivacyDisclosure(val title: String, val detail: String)

internal fun privacyDisclosures(): List<PrivacyDisclosure> = listOf(
    PrivacyDisclosure("Datos bajo tu control", "El catálogo, el progreso, las notas y las preferencias se guardan en este dispositivo y en el respaldo de Drive que tú elijas."),
    PrivacyDisclosure("Sin anuncios ni cuentas", "{app} no incluye publicidad ni analítica propia y no exige crear una cuenta. Las funciones de Google ML Kit (escáner ISBN, traducción e identificación de idioma) pueden enviar a Google datos técnicos de uso y rendimiento, nunca el contenido de tus libros."),
    PrivacyDisclosure("Acceso a Internet", "Se usa para sincronizar con el proveedor de archivos elegido y consultar portadas, fichas, noticias, diccionario o traducción cuando solicitas esas funciones."),
    PrivacyDisclosure("Permisos opcionales", "El micrófono solo se solicita al usar la búsqueda por voz y las notificaciones, para mantener los controles de lectura en voz alta. El escáner ISBN usa el de Google Play services y no necesita permiso de cámara."),
    PrivacyDisclosure("Copias de Android desactivadas", "La aplicación impide que Android copie automáticamente sus datos. Tú decides cuándo exportar o restaurar un respaldo.")
)
