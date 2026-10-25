# Cambios de la versión 1.0.81 (seis idiomas)

## Idiomas

La app se muestra en **español, inglés, portugués (Brasil), francés, alemán e italiano**,
según el idioma del teléfono. En Android 13 o posterior también se puede elegir un idioma
solo para la app en Ajustes del sistema → Aplicaciones → Bookhollow → Idioma. Si el
teléfono está en otro idioma, la app aparece en inglés.

### Cómo funciona

- Los textos se siguen escribiendo en español en el código y pasan por `tr("…")`.
- Las traducciones están en una sola tabla:
  `app/src/main/resources/i18n/translations.tsv` (una fila por texto; columnas es, en, pt,
  fr, de, it). Para corregir una traducción basta con editar esa tabla.
- Los textos con datos usan marcadores: `trf("Página {0} de {1}", …)`; `{app}` es el
  nombre de la aplicación.
- Las palabras con dos significados usan contexto: `tr("Inicio", "nav")` (pestaña) frente
  a `tr("Inicio")` (fecha de inicio).
- El nombre bajo el icono y los accesos directos usan `res/values-xx/strings.xml`;
  el idioma por defecto (`values/`) es ahora el inglés.
- Pruebas automáticas (`I18nTest`): la tabla carga, ninguna traducción está vacía y los
  marcadores coinciden en todos los idiomas.

### Traducido

Unos 550 textos: navegación, inicio, biblioteca, ficha, estadísticas, ajustes, avisos,
lector, ayuda, privacidad, información legal y novedades de la versión.

### Todavía en español (siguiente fase)

- Nombres de géneros, estados de lectura en algunos sitios y nombres de fondos.
- Algunos textos secundarios con datos que aún no usan marcadores.
- Fuentes: Wikipedia y el diccionario (Wikcionario y RAE) siguen en español; la función
  «Traducción» traduce al español; los cinco clásicos gratuitos son en español.
- Las novedades de Google Books ya siguen el idioma y el país del teléfono.

## Ficha de Google Play

- Título, descripción corta, descripción completa, novedades e icono en
  `fastlane/metadata/android/` para es-ES, en-US, pt-BR, fr-FR, de-DE e it-IT.
- Política de privacidad actualizada en los seis idiomas (`docs/PRIVACIDAD.md`,
  `PRIVACY_EN.md`, `PRIVACIDADE_PT.md`, `CONFIDENTIALITE_FR.md`, `DATENSCHUTZ_DE.md`,
  `PRIVACY_IT.md`).

Conviene que una persona nativa revise al menos la ficha en inglés antes de publicar.
