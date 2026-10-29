package com.diego.mibiblioteca

/**
 * Nombre comercial de la aplicación en un único sitio.
 *
 * Para cambiar el nombre de la app basta con modificar NAME aquí y
 * `app_name` en res/values/strings.xml (el nombre que aparece bajo el icono).
 * El identificador interno `com.diego.mibiblioteca` no depende de este valor.
 */
internal object AppBrand {
    const val NAME = "Bookhollow"

    /** Página de contacto que se envía a los servicios públicos (Wikipedia, Open Library). */
    const val SUPPORT_URL = "https://github.com/diroka77-pixel/Mibiblioteca7"

    /**
     * Política de privacidad pública (GitHub Pages, carpeta docs/privacy). Es la misma
     * dirección que hay que poner en Google Play Console. El idioma va tras la almohadilla.
     */
    const val PRIVACY_URL = "https://diroka77-pixel.github.io/Mibiblioteca7/privacy/"

    /** Identificación para servicios con API pública: piden nombre y contacto. */
    const val API_USER_AGENT = "$NAME/1.0 (Android; $SUPPORT_URL)"

    /** Identificación para páginas web que esperan un navegador. */
    const val WEB_USER_AGENT = "Mozilla/5.0 (Android; $NAME)"
}
