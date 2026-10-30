# Cómo publicar Bookhollow en Google Play

Guía paso a paso de lo que queda por hacer fuera del código (versión 1.0.95). Todo se puede
hacer desde el móvil, aunque Play Console es más cómodo en un ordenador o una tablet.

## Lo que ya está preparado

- App con objetivo Android 16 (obligatorio para apps nuevas), librerías de 16 KB,
  firma de 4096 bits válida hasta 2054 y paquete AAB generado por GitHub Actions.
- Sin contraseña de acceso, sin permiso de cámara y con cámara y micrófono marcados como
  opcionales (la app se ofrece también a tablets y Chromebooks).
- Solo fuentes abiertas (Open Library, Wikipedia, Wiktionary, Project Gutenberg) con sus licencias.
- Fichas de la tienda en seis idiomas: título, descripción breve, descripción completa,
  notas de la versión, icono 512×512 y **imagen destacada 1024×500**
  (`fastlane/metadata/android/<idioma>/`).
- Política de privacidad en seis idiomas lista para publicarse (`docs/privacy/index.html`).
- Respuestas del formulario de seguridad de datos en `docs/DATA_SAFETY_ES.md`.

## 1. Publicar la política de privacidad

La política se publica desde un repositorio **público y aparte**, `bookhollow`, que solo
contiene esa página. Así el repositorio del código (Mibiblioteca7) puede ser privado: GitHub
Pages gratuito solo funciona en repositorios públicos.

1. Crea el repositorio en https://github.com/new → nombre **bookhollow**, **Public**, marca
   «Add a README file» → **Create repository**.
2. Claude copia la página (`docs/privacy/index.html`, que se genera con
   `tools/generar_privacidad.py`) a ese repositorio y activa GitHub Pages.
3. Queda en **https://diroka77-pixel.github.io/bookhollow/privacy/** (el botón «Política
   completa» de la app abre esa dirección).

Contacto público de la app: **bookhollow.app@gmail.com** (política, ficha de Play y soporte).

## 2. Cuenta de desarrollador

1. Entra en https://play.google.com/console/signup con la cuenta de Google que quieras usar.
2. Elige **cuenta personal**, paga la cuota única de **25 USD** y verifica tu identidad
   (documento oficial y teléfono). Google pide además verificar el dispositivo Android.
3. Correo de contacto para la ficha (Google lo muestra públicamente): **bookhollow.app@gmail.com**.

## 3. Crear la app en Play Console

1. **Crear app** → nombre «Bookhollow», idioma predeterminado **Español (España)**, tipo
   **Aplicación**, **Gratuita** o **De pago**.
   - **Importante:** una app publicada como gratuita **no puede pasar a ser de pago** después.
     Bookhollow se publica como **Gratuita**: el pago es Bookhollow Premium, dentro de la app.
2. Acepta las declaraciones (políticas del programa y leyes de exportación de EE. UU.).

## 3 bis. Bookhollow Premium (pago único de 1,99 €)

La app es **gratuita** y tiene una compra dentro de la app: **Bookhollow Premium**, que
desbloquea vincular y sincronizar una carpeta **en la nube** (Google Drive, Dropbox, OneDrive
u otra). Abrir y vincular libros del propio teléfono o tablet (Descargas, memoria interna o
tarjeta SD) es gratis. El código ya está listo; en Play
Console hay que crear el producto con **exactamente** este identificador:

1. **Configuración → Perfil de pagos**: créalo si no lo tienes (hace falta para cobrar).
2. **Monetizar con Play → Productos → Productos únicos → Crear producto único**:
   - ID del producto: **bookhollow_premium** (no se puede cambiar después).
   - Nombre: «Bookhollow Premium». Descripción: «Vincula y sincroniza tu carpeta de libros en la nube (Drive, Dropbox, OneDrive…)».
   - Opción de compra: compra (no alquiler), precio **1,99 EUR**; Google calcula el resto de países.
   - **Activar**.
3. Para probar la compra sin pagar: **Configuración → Probar licencias** → añade tu Gmail y
   el de tus testers. Ellos verán el pago marcado como prueba.
4. La compra solo funciona con la app instalada **desde Google Play** (prueba cerrada o
   producción); una APK instalada a mano dice que la compra no está disponible.
5. En el cuestionario de clasificación, «¿Compras digitales?»: **Sí**.

Al cobrar, Google te pedirá declararte **comerciante** (normas europeas) y mostrará en la
ficha tus datos de contacto, que pueden incluir dirección y teléfono.

## 4. «Contenido de la app» (Monitor y mejora → Contenido de la app)

| Apartado | Qué responder |
| --- | --- |
| Política de privacidad | https://diroka77-pixel.github.io/bookhollow/privacy/ |
| Anuncios | La app **no contiene anuncios** |
| Acceso a la app | **Toda la funcionalidad está disponible sin acceso especial** |
| ID de publicidad | **No** usa el ID de publicidad |
| Público objetivo | **18 años o más** (puedes añadir 16-17). No marques edades infantiles: activaría la política de Familias |
| Aplicación de noticias | No |
| Apps de salud / financieras / gubernamentales | No |
| Seguridad de los datos | Copia las respuestas de `docs/DATA_SAFETY_ES.md` |
| Clasificación de contenido | Ver apartado 5 |
| Servicios en primer plano | Ver apartado 6 |

## 5. Cuestionario de clasificación de contenido (IARC)

- Correo de contacto: el de la cuenta.
- Categoría: **Referencia, noticias o educación** (no es un juego).
- Violencia, sexo, lenguaje malsonante, drogas, apuestas: **No** (la app no incluye contenido
  propio de ese tipo; los libros los aporta el usuario o son clásicos de dominio público).
- ¿Los usuarios pueden interactuar o intercambiar contenido entre sí? **No**.
- ¿Comparte la ubicación? **No**. ¿Compras digitales? **Sí** (Bookhollow Premium).
- ¿Navegador web sin restricciones? **No** (los enlaces se abren en el navegador del teléfono).

## 6. Declaración de servicio en primer plano (lectura en voz alta)

La app usa un servicio de tipo **mediaPlayback** para seguir leyendo en voz alta con la
pantalla apagada. Google pide:

- **Tipo de tarea**: Reproducción multimedia.
- **Descripción** (puedes copiarla):
  «Bookhollow lee en voz alta el libro abierto con el sintetizador de voz del teléfono. Al
  pulsar “Leer en voz alta”, la lectura continúa con la pantalla apagada o desde otra app, y
  la notificación permite pausarla o detenerla.»
- **Impacto si se interrumpe**: «La lectura en voz alta se detendría al apagar la pantalla o
  cambiar de app, y el usuario perdería el punto de escucha.»
- **Vídeo** (sube un vídeo corto a YouTube como «No listado» y pega el enlace): graba la
  pantalla del móvil abriendo un libro → menú lateral → «Leer en voz alta» → bloquear la
  pantalla → se sigue oyendo → desbloquear y pausar desde la notificación.

### 6 bis. Segundo servicio: sincronización de datos (dataSync)

Desde la 1.0.98, mientras se sincroniza la carpeta de libros la app muestra una notificación
«Sincronizando tu biblioteca» para que Android no la congele en segundo plano. En la misma
declaración de servicios en primer plano añade el tipo **Sincronización de datos**:

- **Descripción**: «Cuando el usuario vincula o sincroniza su carpeta de libros, Bookhollow lee
  los archivos de la carpeta y crea el catálogo. El servicio mantiene esa lectura activa si el
  usuario sale de la app y muestra el progreso en una notificación; se detiene al terminar.»
- **Impacto si se interrumpe**: «La sincronización de carpetas grandes en la nube quedaría a
  medias y la biblioteca incompleta.»
- **Vídeo**: abre Bookhollow → menú → elegir carpeta → pulsa «Usar esta carpeta» → sal de la app
  con el botón de inicio → baja la cortina de notificaciones para mostrar «Sincronizando tu
  biblioteca» → vuelve a la app cuando termine.

## 7. Ficha de la tienda (Crecimiento → Presencia en Play Store → Ficha principal)

Ya preparado en `fastlane/metadata/android/<idioma>/`: título, descripción breve, descripción
completa, icono e imagen destacada (`images/featureGraphic.png`). Añade cada idioma como
traducción de la ficha y copia sus textos.

**Falta: capturas de pantalla** (mínimo 2 por idioma; recomendable 4-6, formato vertical):

1. Inicio con «Continuar leyendo», novedades y clásicos gratuitos.
2. La biblioteca en galería.
3. Una página del lector (mejor con un clásico, sin libros con derechos de autor).
4. La ficha de un libro con sinopsis y biografía.
5. Estadísticas.
6. Modo oscuro.

Cambia el idioma de la app (Ajustes → Idioma) para sacar las capturas de cada idioma. Si
puedes, añade también 2 capturas de tablet (7" y 10"), porque la app ya se adapta a tablets.
Categoría de la app: **Libros y obras de consulta**.

## 8. Prueba cerrada obligatoria (cuentas personales nuevas)

1. **Pruebas → Prueba cerrada** → crea un canal (por ejemplo «Amigos»).
2. Sube el **AAB** (artefacto *Bookhollow-v1.0.95-google-play-aab* (o el de la versión más reciente) de GitHub Actions).
   Al subirlo por primera vez, acepta la **Firma de aplicaciones de Google Play**: Google
   guarda la clave de firma definitiva y tu clave actual queda como clave de subida.
3. Añade **al menos 12 testers** (sus correos de Google, o un Grupo de Google) y comparte el
   enlace de participación. Deben **seguir dentro 14 días seguidos**.
4. Pídeles que la usen de verdad y te comenten fallos: al pedir producción, Google pregunta
   cómo fue la prueba y qué cambiaste.
5. Pasados los 14 días: **Panel → Solicitar acceso a producción** y responde el cuestionario.
   La revisión tarda hasta 7 días.

## 9. Verificación de desarrollador de Android

Google exige registrar el nombre del paquete (`com.diego.mibiblioteca`) y su clave de firma
en la cuenta. Ya se aplica en Brasil, Indonesia, Singapur y Tailandia y llegará a todo el
mundo en 2027. Desde Play Console se hace con tu cuenta: sigue los avisos que aparezcan en el
Panel.

## 10. Antes de pulsar «Publicar»

- Prueba la versión del AAB en tu Samsung y, si puedes, en un Android antiguo: abrir libros,
  pasar páginas, leer en voz alta con la pantalla apagada, escanear un ISBN, sincronizar con
  Drive, cambiar de idioma y modo oscuro.
- Comprueba que la política de privacidad abre bien desde la app.
- Si cobras: crea el **perfil de pagos** en Play Console y fija el precio por país.
