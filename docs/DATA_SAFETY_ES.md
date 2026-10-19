# Declaración de seguridad de datos — borrador

Este documento sirve para completar el formulario de seguridad de datos de una tienda Android. Debe revisarse contra el formulario vigente en el momento de publicar.

## Tratamiento general

- MiBiblioteca no incorpora publicidad ni analítica de uso.
- No requiere una cuenta propia.
- El catálogo, el progreso, las notas, los subrayados y las preferencias se guardan en el dispositivo.
- El usuario elige la carpeta a la que concede acceso mediante el selector de documentos de Android.
- La aplicación no envía la biblioteca personal a un servidor propio.
- Las copias automáticas de Android están desactivadas.

## Datos y finalidad

| Dato o acceso | Finalidad | Inicio |
| --- | --- | --- |
| Archivos de libros elegidos | Crear el catálogo y abrir el lector | Elección explícita de carpeta o archivo |
| Cámara | Leer un código ISBN | Al abrir el escáner |
| Micrófono | Convertir una búsqueda hablada en texto | Al pulsar búsqueda por voz |
| Notificaciones | Mostrar controles del lector TTS | Al usar lectura en voz alta |
| Conexión a Internet | Sincronización y consultas públicas solicitadas | Según la función utilizada |
| Progreso, notas y preferencias | Mantener la experiencia personal | Uso normal de la aplicación |

## Servicios externos

Las búsquedas de metadatos, portadas, noticias, diccionario o traducción pueden enviar los términos necesarios al proveedor consultado. No deben enviarse notas, subrayados ni el contenido completo de la biblioteca. Los proveedores se identifican en «Información legal» dentro de la aplicación.

## Respuestas previstas para la ficha pública

- Recopilación por un servidor propio de MiBiblioteca: no.
- Venta de datos: no.
- Publicidad personalizada: no.
- Analítica de uso: no.
- Eliminación de datos locales: disponible desde los ajustes de Android y mediante las funciones de gestión de la aplicación.
- Cifrado en tránsito: las consultas de red configuradas utilizan HTTPS; el tráfico sin cifrar está desactivado en el manifiesto.

## Verificación antes de enviar

1. Revisar las definiciones actuales de «recopilado», «compartido» y «procesamiento efímero» de la tienda.
2. Confirmar las declaraciones exigidas para ML Kit y cada proveedor externo.
3. Comprobar que la versión publicada mantiene los mismos permisos del manifiesto revisado.
4. Publicar una URL estable para la política de privacidad.
