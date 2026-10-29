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

    /**
     * Correo público de contacto y soporte: aparece en la ficha de Google Play, en la
     * política de privacidad y en «Acerca de y soporte». También identifica la app ante
     * los servicios públicos (Wikipedia y Open Library piden un contacto).
     */
    const val SUPPORT_EMAIL = "bookhollow.app@gmail.com"

    /**
     * Política de privacidad pública (GitHub Pages del repositorio público «bookhollow», que solo
     * contiene esa página, para que el repositorio del código pueda ser privado). Es la misma
     * dirección que hay que poner en Google Play Console. El idioma va tras la almohadilla.
     */
    const val PRIVACY_URL = "https://diroka77-pixel.github.io/bookhollow/privacy/"

    /** Identificación para servicios con API pública: piden nombre y contacto. */
    const val API_USER_AGENT = "$NAME/1.0 (Android; $SUPPORT_EMAIL)"

    /** Identificación para páginas web que esperan un navegador. */
    const val WEB_USER_AGENT = "Mozilla/5.0 (Android; $NAME)"
}
