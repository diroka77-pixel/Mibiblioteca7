# Cambios de la versión 1.0.98

- **Sincronización en segundo plano**: nuevo `LibrarySyncService` (servicio en primer plano de
  tipo `dataSync`). Mientras se sincroniza la carpeta, muestra la notificación «Sincronizando tu
  biblioteca · N archivos revisados» y Android no congela la app al salir de ella. Se cierra solo
  al terminar (y en Android 15, si se agota el tiempo que permite el sistema).
- Manifiesto: permiso `FOREGROUND_SERVICE_DATA_SYNC` y declaración del servicio.
- En Play Console hay que declarar también este tipo de servicio (ver
  `PUBLICAR_EN_GOOGLE_PLAY.md`, apartado 6 bis).
