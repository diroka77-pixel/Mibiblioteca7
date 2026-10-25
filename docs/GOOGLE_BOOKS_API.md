# Clave gratuita de Google Books

La app funciona sin clave, pero entonces comparte con todo el mundo un cupo anónimo
pequeño y, con muchos usuarios, las novedades y fichas de Google Books pueden fallar.
Con una clave propia, el proyecto tiene su propio cupo gratuito diario.

## 1. Crear la clave (gratis, sin tarjeta)

1. Entra en https://console.cloud.google.com/ con tu cuenta de Google.
2. Crea un proyecto nuevo (por ejemplo, «Bookhollow»).
3. Activa la API: https://console.cloud.google.com/apis/library/books.googleapis.com → «Habilitar».
4. Ve a https://console.cloud.google.com/apis/credentials → «Crear credenciales» → «Clave de API».

La Books API no necesita activar la facturación ni añadir una tarjeta.

## 2. Restringir la clave (importante)

En la clave recién creada → «Editar clave de API»:

- **Restricciones de API:** «Restringir clave» y marca solo **Books API**.
- **Restricciones de aplicación:** «Aplicaciones de Android» → «Añadir» con:
  - Nombre del paquete: `com.diego.mibiblioteca`
  - Huella SHA-1 del certificado con el que se firma la APK. Sale en GitHub, en el paso
    «Verificar firma y alineación de la APK» de la compilación (línea `SHA-1 digest`).
  - Cuando publiques en Google Play con la firma de apps de Google Play, añade también la
    SHA-1 de la «clave de firma de apps» (Play Console → Configuración → Integridad de la app).

La app envía las cabeceras `X-Android-Package` y `X-Android-Cert` que Google usa para
comprobar esta restricción.

## 3. Guardarla en GitHub (no en el código)

Repositorio → Settings → Secrets and variables → Actions → «New repository secret»:

- Name: `GOOGLE_BOOKS_API_KEY`
- Secret: la clave

La próxima compilación la incluirá. Si el secreto no existe, la app compila igual, sin clave.

## Coste y condiciones

- La Books API es gratuita. Si se supera el cupo diario, Google responde con un error
  temporal (la app lo detecta y espera una hora); no se cobra nada.
- Condiciones: https://developers.google.com/books/terms y las condiciones generales de
  las API de Google. En resumen: mostrar la atribución a Google Books, no guardar
  copias masivas de su catálogo y respetar los límites de uso.
