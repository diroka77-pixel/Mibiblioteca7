package com.diego.mibiblioteca

import org.junit.Assert.*
import org.junit.Test

class EpubMetadataTest {
    private fun parse(xml: String) = parseOpf(xml.toByteArray(Charsets.UTF_8))

    @Test fun epub2NamespacedFieldsAndCover() {
        val metadata = parse("""<?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                <dc:title>La sombra del viento [ePubLibre]</dc:title>
                <dc:creator opf:role="edt">Editor técnico</dc:creator>
                <dc:creator opf:role="aut">Carlos Ruiz Zafón</dc:creator>
                <dc:language>es</dc:language>
                <dc:identifier opf:scheme="ISBN">urn:isbn:978-84-08-04364-5</dc:identifier>
                <dc:description>&lt;p&gt;Un libro &lt;b&gt;inolvidable&lt;/b&gt;.&lt;/p&gt;</dc:description>
                <meta name="cover" content="cover-id"/>
                <meta name="calibre:series" content="El cementerio de los libros olvidados"/>
                <meta name="calibre:series_index" content="1"/>
              </metadata>
              <manifest><item id="cover-id" href="images/portada.jpg" media-type="image/jpeg"/></manifest>
            </package>""")
        assertEquals("La sombra del viento", metadata.title)
        assertEquals("Carlos Ruiz Zafón", metadata.author)
        assertEquals("9788408043645", metadata.isbn)
        assertEquals("es", metadata.language)
        assertEquals("Un libro inolvidable.", metadata.description)
        assertEquals("images/portada.jpg", metadata.coverHref)
        assertEquals("1", metadata.sagaOrder)
    }

    @Test fun epub3MainTitleAuthorRolesAndCoverImage() {
        val metadata = parse("""<opf:package xmlns:opf="http://www.idpf.org/2007/opf">
            <opf:metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
              <dc:title id="subtitle">Una historia</dc:title>
              <dc:title id="main">El camino - Regreso a casa</dc:title>
              <opf:meta refines="#main" property="title-type">main</opf:meta>
              <dc:creator id="translator">Otra persona</dc:creator>
              <opf:meta refines="#translator" property="role">trl</opf:meta>
              <dc:creator id="writer">María García</dc:creator>
              <opf:meta refines="#writer" property="role">aut</opf:meta>
              <dc:creator>Juan Pérez</dc:creator>
              <opf:meta property="belongs-to-collection">Caminos</opf:meta>
              <opf:meta property="group-position">2</opf:meta>
            </opf:metadata>
            <opf:manifest><opf:item id="front" href="Images/front.png" properties="cover-image" media-type="image/png"/></opf:manifest>
            </opf:package>""")
        assertEquals("El camino - Regreso a casa", metadata.title)
        assertEquals("María García · Juan Pérez", metadata.author)
        assertEquals("Images/front.png", metadata.coverHref)
        assertEquals("Caminos", metadata.saga)
        assertEquals("2", metadata.sagaOrder)
    }

    @Test fun guideCoverAndSourceSuffixPreserveTitle() {
        val metadata = parse("""<package><metadata><title>Mi libro epublibre.org</title>
            <creator>Unknown</creator></metadata>
            <guide><reference type="cover" href="Text/cover.xhtml"/></guide></package>""")
        assertEquals("Mi libro", metadata.title)
        assertEquals("Autor desconocido", metadata.author)
        assertEquals(listOf("Text/cover.xhtml"), metadata.coverCandidates)
        assertEquals("Título auténtico", cleanCatalogText("Título auténtico - Lecturalia - r1.2.epub"))
    }

    @Test fun unmarkedOpeningPageAndImagesRemainCoverCandidates() {
        val metadata = parse("""<package><metadata><title>Una novela</title></metadata>
            <manifest>
              <item id="opening" href="Text/001.xhtml" media-type="application/xhtml+xml"/>
              <item id="image1" href="Images/001.jpg" media-type="image/jpeg"/>
              <item id="image2" href="Images/002.jpg" media-type="image/jpeg"/>
            </manifest><spine><itemref idref="opening"/></spine></package>""")
        assertEquals(listOf("Text/001.xhtml"), metadata.coverCandidates)
        assertEquals(listOf("Images/001.jpg", "Images/002.jpg"), metadata.imageCandidates)
    }
}
