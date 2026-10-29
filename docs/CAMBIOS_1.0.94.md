# Cambios de la versión 1.0.94 (Bookhollow Premium)

Bookhollow pasa a ser **gratuita con un pago único**: **Bookhollow Premium (1,99 €)**
desbloquea **vincular y sincronizar una carpeta de libros** (Google Drive u otra). Todo lo
demás sigue siendo gratis: abrir libros sueltos, leer, escuchar, diccionario, novedades,
estadísticas y clásicos gratuitos.

## Cómo funciona

- Nuevo `Premium.kt` con la librería de pagos de Google Play 8.3.0 (Google exige la 8 o
  posterior). Producto de compra única: **bookhollow_premium** (hay que crearlo en Play
  Console con ese ID; ver `docs/PUBLICAR_EN_GOOGLE_PLAY.md`).
- Al abrir la app se comprueba la compra con Google Play (se respetan los reembolsos) y el
  estado se guarda en el teléfono para funcionar sin conexión.
- Las compras se reconocen al momento (si no, Google las devuelve a los 3 días) y se
  contemplan los pagos pendientes.
- Quien ya tenía una carpeta vinculada antes de esta versión la conserva sin pagar.
- Pantalla **Bookhollow Premium** (menú lateral y al intentar elegir carpeta): qué incluye,
  precio que da Google Play en la moneda del usuario, «Desbloquear», «Restaurar compra» y
  «Ahora no». Fuera de Google Play (APK instalada a mano) avisa de que la compra no está
  disponible.
- Elegir carpeta (guía inicial, ayuda, menú, biblioteca vacía…) abre Premium si no se ha
  comprado; la sincronización también lo comprueba.

## Textos

- Políticas de privacidad (6 idiomas): nuevo apartado «Compras» (Google procesa el pago; la
  app no recibe datos de pago).
- Fichas de Google Play: aviso de que Premium es un pago único; se corrige la frase que
  decía que se pedía la cámara.
- Guía de publicación: cómo crear el producto, el perfil de pagos y las cuentas de prueba.

## Versión Android

- `versionCode 194`.
- `versionName 1.0.94`.
