# Plan técnico de optimización integral — MiBiblioteca

Fecha de revisión: 2026-10-14
Rama base: `redisenio-biblioteca-096`
Rama de trabajo: `desarrollo/optimizacion-integral`

## Estado verificado en el código

- `MainActivity.kt` concentra unas 6.336 líneas y combina UI, almacenamiento, sincronización y portadas.
- `ReaderScreen.kt` tiene unas 1.939 líneas.
- Hay persistencia SQLite mediante `BookStore`; no conviene añadir Room sin evaluar migraciones.
- Ya existen `LruCache` para bitmaps, `Semaphore(2)` para limitar decodificaciones y `LazyColumn`/`LazyRow`.
- `FlatCover` dibuja portadas con `ContentScale.Fit`; hay que investigar por qué algunas siguen en blanco.
- El lector EPUB actual extrae texto, con limitaciones para maquetación e imágenes.
- El flujo de compilación `build-apk.yml` se activa manualmente (`workflow_dispatch`). No ejecutarlo durante esta fase.

## Orden de ejecución

### Bloque A: integridad y diagnóstico
1. Identificar los caminos de sincronización de Drive y los fallos silenciosos.
2. Trazar origen de portada (EPUB, descarga, caché), fallo y reintentos.
3. Garantizar que una actualización no borre metadatos ni progreso de lectura.
4. Añadir pruebas unitarias para funciones puras y casos límite.

### Bloque B: rendimiento
1. Revisar invalidaciones de caché y carga de portadas.
2. Examinar estabilidad de claves en listas y preservación de desplazamiento.
3. Detectar trabajo costoso en hilo principal y recomposiciones excesivas.
4. Medir antes/después en dispositivo real.

### Bloque C: experiencia de usuario
1. Corregir saltos al pulsar el selector de vistas.
2. Portadas completas, proporcionadas y con placeholder claro.
3. Contraste legible en todas las pantallas.
4. Navegación accesible y animaciones opcionales.

### Bloque D: lector y nuevas funciones
1. Posición de lectura fiable, marcadores y TTS.
2. Evaluar motor EPUB con soporte de maquetación e imágenes.
3. Estadísticas, recomendaciones locales y herramientas de mantenimiento.
4. Widgets y exportación de datos.
5. Sincronización Kobo: aplazada a petición del usuario.

## Reglas de trabajo
- No modificar `main` durante el desarrollo.
- Evitar disparar GitHub Actions y no compilar APK hasta validar el conjunto.
- No introducir migraciones destructivas.
- No almacenar secretos ni datos privados en commits.
- Cada cambio de código debe acompañarse de revisión y, cuando sea posible, pruebas.
- No afirmar que una corrección funciona en el dispositivo hasta probarla.

## Primer hallazgo de código a investigar
`decodeCover` usa una clave basada en `System.identityHashCode(book.cover)`. Esta clave depende de la identidad del array de bytes, no de su contenido. Puede perjudicar la reutilización de la caché si se recrean arrays equivalentes. Antes de cambiarla, medir la frecuencia de recreación y asegurar una invalidación correcta cuando cambia una portada.
