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
| Cámara | No se pide el permiso: el escáner ISBN es el de Google Play services | — |
| Micrófono | Convertir una búsqueda hablada en texto | Al pulsar búsqueda por voz |
| Notificaciones | Mostrar controles del lector TTS | Al usar lectura en voz alta |
| Conexión a Internet | Sincronización y consultas públicas solicitadas | Según la función utilizada |
| Progreso, notas y preferencias | Mantener la experiencia personal | Uso normal de la aplicación |

## Servicios externos

Las búsquedas de metadatos, portadas, noticias, diccionario o traducción pueden enviar los términos necesarios al proveedor consultado. No deben enviarse notas, subrayados ni el contenido completo de la biblioteca. Los proveedores se identifican en «Información legal» dentro de la aplicación.

Desde la 1.0.87 la app no usa Google Books ni la «RAE API comunitaria». Las novedades se piden a Open Library con los nombres de las grandes editoriales y el idioma; el país del dispositivo (tarjeta SIM o región del sistema) solo se usa en el teléfono para elegir esas editoriales y **no se envía**. No se usa la ubicación ni el permiso de ubicación, así que no hay que declarar «Ubicación».

## Google ML Kit

La app incluye Google ML Kit para el escáner ISBN, la traducción y la identificación de idioma. Según la documentación de Google, estas bibliotecas pueden enviar a Google datos técnicos (información del dispositivo y de la app, métricas de rendimiento y uso de la API) para su mantenimiento. No envían el contenido de los libros ni las notas. Antes de publicar, consulta la página de divulgación de datos de ML Kit y decláralo en el formulario («Información y rendimiento de la app» / «Diagnóstico»). Por este motivo los textos de la app ya no dicen «sin seguimiento», sino «sin publicidad ni analítica propia».

## Respuestas para el formulario de Google Play (1.0.91)

Según la página de divulgación de datos de ML Kit (https://developers.google.com/ml-kit/android-data-disclosure),
ML Kit envía a Google datos técnicos para «diagnóstico y analítica de uso», cifrados con HTTPS
y sin cederlos a terceros. Por eso:

1. **¿Recopila o comparte la app algún tipo de dato de usuario?** Sí.
2. **¿Se cifran los datos en tránsito?** Sí.
3. **¿Los usuarios pueden pedir que se eliminen sus datos?** No hay cuenta ni servidor propio:
   indica que los datos se guardan en el dispositivo y se borran desinstalando la app o desde
   Ajustes de Android.
4. Tipos de datos a marcar:

| Categoría en Play Console | Tipo | Recopilado | Compartido | Efímero | Obligatorio | Finalidad |
| --- | --- | --- | --- | --- | --- | --- |
| Información y rendimiento de la app | Diagnóstico | Sí (ML Kit) | No | No | Sí | Analítica |
| Identificadores del dispositivo u otros | Identificadores del dispositivo u otros | Sí (ML Kit) | No | No | Sí | Analítica |
| Audio | Grabaciones de voz o sonido | Sí (búsqueda por voz, reconocimiento de Android) | No | Sí | No (opcional) | Funcionalidad de la app |

- La búsqueda por voz usa el reconocimiento de voz de Android, que en el teléfono o en los
  servidores de Google convierte la voz en texto; la app no guarda el audio. Declararlo como
  «efímero y opcional» es la opción prudente.
- **No** marques: ubicación, información personal, mensajes, fotos y vídeos, archivos y
  documentos (los libros nunca salen del teléfono hacia un servidor de la app), contactos,
  calendario, información financiera, salud, actividad en la app ni navegación web.
- Publicidad: no. ID de publicidad: no (la app no declara `AD_ID`).

## Verificación antes de enviar

1. Revisar las definiciones actuales de «recopilado», «compartido» y «procesamiento efímero» de la tienda.
2. Confirmar las declaraciones exigidas para ML Kit y cada proveedor externo.
3. Comprobar que la versión publicada mantiene los mismos permisos del manifiesto revisado.
4. URL de la política de privacidad: https://diroka77-pixel.github.io/Mibiblioteca7/privacy/ (activar GitHub Pages, ver `docs/PUBLICAR_EN_GOOGLE_PLAY.md`).
