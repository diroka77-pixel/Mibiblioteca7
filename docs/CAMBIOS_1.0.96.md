# Cambios de la versión 1.0.96

- **Cambio de carpeta**: al vincular otra carpeta (por ejemplo, pasar de Drive a Dropbox), la
  biblioteca muestra solo los libros de la carpeta nueva, además de los libros importados sueltos
  y los clásicos. Antes se conservaban también todos los de la carpeta anterior, porque la app
  protegía el catálogo frente a listados incompletos de Drive.
  - Los datos de la carpeta anterior (favoritos, progreso, notas) no se borran: quedan en el
    teléfono y en el respaldo de esa carpeta, y vuelven si se elige otra vez.
  - Al volver a la carpeta anterior, la primera sincronización tarda más porque se catalogan de nuevo.
  - La protección frente a listados incompletos sigue igual dentro de la misma carpeta.
- **Bienvenida, biblioteca vacía y ayuda** explican que las carpetas del teléfono son gratis y las
  de la nube, Premium.
- **Compra Premium más protegida**: el estado ya no se guarda como «sí/no» sino como una firma
  ligada al dispositivo (copiar o editar los ajustes no activa Premium). Se sigue confirmando con
  Google Play en cada arranque. El código ya se ofusca con R8 en la versión de publicación.
- Comprobado por el usuario: Dropbox permite elegir una carpeta entera.
