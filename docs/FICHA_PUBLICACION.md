# Ficha de publicación — MiBiblioteca 1.0.61

## Nombre

Mi Biblioteca

## Descripción breve

Organiza, lee y cuida tu biblioteca digital desde un único espacio privado.

## Descripción completa

Mi Biblioteca reúne tus libros EPUB, PDF, MOBI, AZW y FB2 en un catálogo personal. Permite leer, escuchar libros mediante las voces instaladas en Android, guardar notas y subrayados, organizar sagas, géneros y secciones, revisar estadísticas y mantener la salud de los archivos y portadas.

La sincronización utiliza la carpeta que el usuario elige mediante el selector de documentos de Android. La aplicación no incluye anuncios, no exige una cuenta propia y muestra dentro de la app cómo utiliza los datos y permisos.

## Explicación de permisos

| Permiso | Motivo visible para el usuario |
| --- | --- |
| Internet | Sincronización y consultas públicas solicitadas |
| Cámara | Escáner ISBN opcional |
| Micrófono | Búsqueda por voz opcional |
| Notificaciones | Controles del lector en voz alta |
| Servicio multimedia | Reproducción TTS mientras la app no está en primer plano |

La aplicación no solicita acceso general al almacenamiento ni permiso para instalar paquetes.

## Lista de comprobación antes de publicar

- Ejecutar pruebas unitarias y compilación `release`.
- Verificar firma, alineación y actualización desde la versión anterior.
- Probar importación y apertura de cada formato compatible.
- Probar selección, revocación y nueva selección de carpeta del proveedor.
- Denegar cámara, micrófono y notificaciones para comprobar la degradación segura.
- Verificar lector, TTS, respaldo, restauración y pantalla Privacidad y datos.
- Comprobar la guía de primera ejecución y volver a abrirla desde «Ayuda y guía rápida».
- Abrir «Acerca de y soporte» y comprobar que el diagnóstico no muestra títulos, notas ni rutas.
- Compartir el diagnóstico con cámara, micrófono y notificaciones concedidos y denegados.
- Adjuntar esta política de privacidad y completar la declaración de seguridad de datos de la tienda.
