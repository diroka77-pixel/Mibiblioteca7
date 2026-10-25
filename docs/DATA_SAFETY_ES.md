# Declaración de seguridad de datos — borrador

Este documento sirve para completar el formulario de seguridad de datos de una tienda Android. Debe revisarse contra el formulario vigente en el momento de publicar.

## Tratamiento general

- Bookhollow no incorpora publicidad ni analítica propia (ver la sección de Google ML Kit).
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

Desde la 1.0.87 la app no usa Google Books ni la «RAE API comunitaria». Las novedades se piden a Open Library con los nombres de las grandes editoriales y el idioma; el país del dispositivo (tarjeta SIM o región del sistema) solo se usa en el teléfono para elegir esas editoriales y **no se envía**. No se usa la ubicación ni el permiso de ubicación, así que no hay que declarar «Ubicación».

## Google ML Kit

La app incluye Google ML Kit para el escáner ISBN, la traducción y la identificación de idioma. Según la documentación de Google, estas bibliotecas pueden enviar a Google datos técnicos (información del dispositivo y de la app, métricas de rendimiento y uso de la API) para su mantenimiento. No envían el contenido de los libros ni las notas. Antes de publicar, consulta la página de divulgación de datos de ML Kit y decláralo en el formulario («Información y rendimiento de la app» / «Diagnóstico»). Por este motivo los textos de la app ya no dicen «sin seguimiento», sino «sin publicidad ni analítica propia».

## Respuestas previstas para la ficha pública

- Recopilación por un servidor propio de Bookhollow: no.
- Venta de datos: no.
- Publicidad personalizada: no.
- Analítica de uso propia: no. Diagnóstico técnico de Google ML Kit: declarar según su documentación.
- Eliminación de datos locales: disponible desde los ajustes de Android y mediante las funciones de gestión de la aplicación.
- Cifrado en tránsito: las consultas de red configuradas utilizan HTTPS; el tráfico sin cifrar está desactivado en el manifiesto.

## Verificación antes de enviar

1. Revisar las definiciones actuales de «recopilado», «compartido» y «procesamiento efímero» de la tienda.
2. Confirmar las declaraciones exigidas para ML Kit y cada proveedor externo.
3. Comprobar que la versión publicada mantiene los mismos permisos del manifiesto revisado.
4. Publicar una URL estable para la política de privacidad.
