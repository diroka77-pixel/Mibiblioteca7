# Validación antes de generar una APK

La rama `desarrollo/optimizacion-integral` es experimental. No fusionar con `main` ni publicar APK hasta completar esta lista.

## Cambios ya incorporados (pendientes de ejecutar y verificar)
- Se eliminó el desplazamiento forzado al inicio al cambiar Galería/Lista.
- Se rechazan dimensiones inválidas al decodificar portadas.
- El fondo que muestra una portada utiliza `FlatCover` en vez del libro 3D.
- La transición de apertura del lector pasó de 1180+340 ms a 540+180 ms.
- El resumen de sincronización incluye el número de libros sin portada.
- La navegación inferior toma colores de `LibraryVisualTokens`.
- Se añadieron reglas de proporción y cuatro pruebas unitarias.
- TalkBack recibe etiquetas y estados para paginación, voz, progreso, descarga, sincronización y mantenimiento.
- Las zonas táctiles principales del lector tienen al menos 48 dp y los controles principales admiten texto grande.

## Validación estática / compilación (sin publicar APK)
1. Ejecutar `gradle :app:testDebugUnitTest --no-daemon` en el entorno con SDK Android 35.
2. Revisar errores y advertencias Kotlin/Compose.
3. Verificar que las pruebas existentes y `CoverPresentationTest` pasan.
4. Revisar cambios contra `redisenio-biblioteca-096` y confirmar que no hay migraciones destructivas.

## Pruebas manuales imprescindibles
1. Galería ↔ Lista: pulsar a derecha e izquierda, al inicio y en mitad de una biblioteca extensa. No debe saltar inesperadamente.
2. Portadas: retrato, apaisado, EPUB sin portada, portada dañada, portada personalizada y cambios repetidos de vista.
3. Fondo de portada: imagen plana y completa; transición de apertura conservada.
4. Abrir lector repetidamente: comprobar fluidez, estado de lectura, reanudación exacta y ausencia de pantalla blanca.
5. Sincronización: añadir EPUB nuevo en Drive, pulsar actualizar y comprobar que aparece, incluso con caché previa.
6. Sincronización: renombrar, modificar y retirar archivos; conservar notas, progreso y portadas manuales.
7. Modo sin conexión: abrir libros descargados, reiniciar app, volver a conectar.
8. TTS: detener a mitad de párrafo, cerrar y volver a abrir; verificar punto de reanudación.
9. Accesibilidad: TalkBack, tamaño de fuente grande, contraste y navegación por gestos.
10. Dispositivos: Samsung principal y realme X2 Pro (Android 11), si está disponible.

## Puertas de calidad
- Ningún dato de usuario perdido o sobrescrito.
- Ninguna regresión grave de lectura, sincronización o navegación.
- Ninguna acción de borrado de archivos sin confirmación.
- No ejecutar GitHub Actions sin necesidad; preferir compilación local.
- No publicar APK firmada ni reemplazar la instalada sin validar firma y compatibilidad.

## Limitación actual
La edición remota permite revisar y modificar fuentes, pero sin SDK y Gradle ejecutables en este entorno no se puede confirmar que las modificaciones compilen ni medir fotogramas o tiempos en el dispositivo. Esa verificación corresponde a Work/Android Studio o al ordenador del usuario.
