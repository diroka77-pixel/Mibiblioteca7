# Cambios de la versión 1.0.76 (requisito de Google Play)

## Hecho en esta versión

- **Android 16 (API 36).** Desde el 31 de agosto de 2026 Google Play solo acepta apps
  nuevas y actualizaciones que apunten a API 36. Se sube `compileSdk` y `targetSdk`
  a 36.
- **Herramientas actualizadas.** Plugin de Android 8.7.3 → 8.11.0 (el primero con
  soporte completo de API 36 verificado) y Gradle 8.9 → 8.13 en GitHub Actions, que
  ahora instala la plataforma `android-36`.
- **Lint no bloquea la compilación.** Las comprobaciones nuevas de Android 16 se siguen
  revisando y guardando en un informe, pero un aviso ya no impide generar la APK.

## Qué probar en el móvil con esta versión

1. Que ningún botón ni título quede tapado por la barra de estado (arriba) o la de
   navegación (abajo). Android 15 y 16 dibujan la app hasta los bordes.
2. El gesto o botón «atrás» en la ficha de un libro, en el lector y en los menús.
   Android 16 activa la animación de «atrás predictivo».
3. En tablets, «Bloquear orientación» no tiene efecto: Android 16 lo ignora en
   pantallas grandes. En móviles sigue funcionando.

## Si GitHub Actions falla

Copia el mensaje de error del paso que aparezca en rojo y compártelo: lo más probable
es un ajuste de versión de una herramienta, fácil de corregir.
