# Cambios de la versión 1.0.91 (lista para Google Play)

## Bloqueos de publicación resueltos

- **Sin contraseña de acceso.** Se quita la pantalla «MATRIX» añadida en GitHub después de la
  1.0.90: el revisor de Google rechaza apps bloqueadas y la contraseña era visible en el
  repositorio público.
- **Sin permiso de cámara.** El escáner ISBN es el de Google Play services, que no lo necesita.
  Se elimina la alternativa antigua (foto en miniatura de baja calidad); si el escáner de
  Google no está disponible, la app pide escribir el ISBN en el buscador.
- **Cámara y micrófono opcionales** (`uses-feature … required="false"`): sin esto Google Play
  ocultaba la app en tablets y Chromebooks sin cámara o micrófono.

## Privacidad

- Política de privacidad publicable en GitHub Pages: `docs/privacy/index.html`, en seis
  idiomas, con selector y modo oscuro (se genera con `tools/generar_privacidad.py`).
- Botón **«Política completa»** en «Privacidad y datos», que abre la política en el idioma de la app.
- Las políticas añaden los apartados de menores, cambios y contacto, y explican que la app no
  pide el permiso de cámara.
- Respuestas concretas del formulario de seguridad de datos en `docs/DATA_SAFETY_ES.md`
  (según la página oficial de divulgación de ML Kit).
- El diagnóstico de soporte ya no muestra el permiso de cámara.

## Traducciones

- Menú al seleccionar texto en el lector (Subrayar, Diccionario, Nota, Copiar, Fijar).
- Notificación de lectura en voz alta (Retroceder, Pausar, Reproducir, Avanzar) y su canal.
- Widget de la pantalla de inicio.
- Descripciones de iconos para TalkBack (todas pasan por la traducción).
- Unos 40 avisos que quedaban solo en español (portadas, sagas, respaldos, voz, filtros…).

## Limpieza

- Se borra `CoverSources.kt`, código sin uso que leía webs de librerías (Casa del Libro,
  Goodreads…), y su prueba.

## Ficha de Google Play

- **Imagen destacada 1024×500** en los seis idiomas (`fastlane/metadata/android/<idioma>/images/featureGraphic.png`),
  generada con `tools/generar_imagen_destacada.js`.
- Guía completa de publicación: `docs/PUBLICAR_EN_GOOGLE_PLAY.md`.

## Versión Android

- `versionCode 191`.
- `versionName 1.0.91`.
