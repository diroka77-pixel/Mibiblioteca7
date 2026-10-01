package com.diego.mibiblioteca

import org.junit.Assert.*
import org.junit.Test

class CoverSourcesTest {
    @Test fun accentsInitialsAndReorderedAuthors() {
        assertTrue(coverTitleMatches("La sombra del viento", "LA SOMBRA DEL VIENTO (edición ilustrada)"))
        assertTrue(coverAuthorMatches("Carlos Ruiz Zafón", "Ruiz Zafon, Carlos"))
        assertTrue(coverAuthorMatches("Stephen King", "S. King"))
        assertFalse(coverAuthorMatches("Stephen King", "Stephen Hawking"))
        assertFalse(coverTitleMatches("La sombra del viento", "La ciudad de vapor"))
    }
    @Test fun redirectsAndSearchResultsOnlyUseBookPages() {
        val html = """<a href="/url?q=https%3A%2F%2Fwww.planetadelibros.com%2Flibro-prueba%2F123&amp;sa=U">Libro</a>
          <a href="https://www.goodreads.com/author/show/123">Autor</a>
          <a href="https://evil.example/book/show/123">Falso</a>"""
        assertEquals(listOf("https://www.planetadelibros.com/libro-prueba/123"),
            coverSearchLinks(html, "https://www.google.com/search"))
        assertEquals(listOf("https://www.casadellibro.com/libro-prueba/9781234567890/1"),
            coverSearchLinks("""<rss><channel><item><link>https://www.casadellibro.com/libro-prueba/9781234567890/1</link></item></channel></rss>""", "https://www.bing.com/search"))
    }
    @Test fun productImagesRejectRecommendationsAndWrongAuthor() {
        val html = """<script type="application/ld+json">{"@graph":[
          {"@type":"Book","name":"La sombra del viento","author":{"name":"Carlos Ruiz Zafón"},"image":{"url":"/correcta.jpg"}},
          {"@type":"Book","name":"La ciudad de vapor","author":{"name":"Carlos Ruiz Zafón"},"image":"/otra.jpg"},
          {"@type":"Book","name":"La sombra del viento","author":{"name":"Otro Autor"},"image":"/autor-equivocado.jpg"}
        ]}</script>"""
        assertEquals(listOf("https://www.planetadelibros.com/correcta.jpg"),
            publicCoverImages(html, "https://www.planetadelibros.com/libro-prueba/123",
                "La sombra del viento", "Carlos Ruiz Zafón", ""))
    }
    @Test fun openGraphCoverRequiresTitleAndAuthor() {
        val html = """<title>La sombra del viento - Carlos Ruiz Zafón | Editorial</title>
          <h1>La sombra del viento</h1><meta property="og:image" content="/portada.jpg">"""
        assertEquals(listOf("https://www.planetadelibros.com/portada.jpg"),
            publicCoverImages(html, "https://www.planetadelibros.com/libro-prueba/123",
                "La sombra del viento", "Carlos Ruiz Zafón", ""))
        assertTrue(publicCoverImages(html, "https://www.planetadelibros.com/libro-prueba/123",
            "Otro título", "Carlos Ruiz Zafón", "").isEmpty())
    }
    @Test fun publisherLogoAndBackCoverAreExcluded() {
        val html = """<title>La Sombra del Viento - Carlos Ruiz Zafón | PlanetadeLibros</title>
          <h1>La Sombra del Viento</h1>
          <meta property="og:image" content="https://www.planetadelibros.com/assets/images/logos/planeta-de-libros-desktop.svg">
          <img alt="Portada La Sombra del Viento" src="https://cdn.example/portada.webp">
          <img alt="Miniatura contraportada La Sombra del Viento" src="https://cdn.example/contra.webp">"""
        assertEquals(listOf("https://cdn.example/portada.webp"),
            publicCoverImages(html, "https://www.planetadelibros.com/libro-prueba/123",
                "La sombra del viento (serie 1)", "Carlos Ruiz Zafón", ""))
    }
}
