package com.diego.mibiblioteca

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReaderChaptersTest {
    @Test fun navigationIndexExcludesFrontMatterAndKeepsRealChapters() {
        val epub = File.createTempFile("reader-navigation", ".epub")
        try {
            ZipOutputStream(epub.outputStream()).use { zip ->
                fun add(path: String, contents: String) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(contents.toByteArray())
                    zip.closeEntry()
                }
                add("META-INF/container.xml", """<container><rootfiles>
                    <rootfile full-path="OPS/content.opf"/></rootfiles></container>""")
                add("OPS/content.opf", """<package><manifest>
                    <item id="nav" href="nav.xhtml" properties="nav"/>
                    <item id="cover" href="cover.xhtml"/>
                    <item id="credits" href="credits.xhtml"/>
                    <item id="one" href="chapter01.xhtml"/>
                    </manifest><spine><itemref idref="cover"/><itemref idref="credits"/>
                    <itemref idref="one"/></spine></package>""")
                add("OPS/nav.xhtml", """<html><body><nav><ol>
                    <li><a href="cover.xhtml">Portada</a></li>
                    <li><a href="credits.xhtml">Créditos</a></li>
                    <li><a href="chapter01.xhtml">Capítulo 1</a></li>
                    </ol></nav></body></html>""")
                add("OPS/cover.xhtml", "<html><body><p>Alicia en el País de las Maravillas</p></body></html>")
                add("OPS/credits.xhtml", "<html><body><h2>Lewis Carroll</h2><p>Datos editoriales</p></body></html>")
                add("OPS/chapter01.xhtml", "<html><body><p>Alicia empezaba a cansarse.</p></body></html>")
            }
            val document = readEpubText(epub) as ReadingDocument.TextDocument
            val starts = document.paragraphs.filter { it.chapterStart }.map { it.text }
            assertEquals(listOf("Capítulo 1"), starts)
        } finally { epub.delete() }
    }

    @Test fun spineFilesAndHeadingsMarkNewPages() {
        val epub = File.createTempFile("reader-chapters", ".epub")
        try {
            ZipOutputStream(epub.outputStream()).use { zip ->
                fun add(path: String, contents: String) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(contents.toByteArray())
                    zip.closeEntry()
                }
                add("META-INF/container.xml", """<container><rootfiles>
                    <rootfile full-path="OPS/content.opf"/></rootfiles></container>""")
                add("OPS/content.opf", """<package><manifest>
                    <item id="one" href="one.xhtml"/><item id="two" href="two.xhtml"/>
                    </manifest><spine><itemref idref="one"/><itemref idref="two"/></spine></package>""")
                add("OPS/one.xhtml", """<html><head><title>Capítulo uno</title></head>
                    <body><p>Final del primero.</p></body></html>""")
                add("OPS/two.xhtml", """<html><body><h1>Capítulo dos</h1>
                    <p>Comienzo del segundo.</p><h1>Capítulo tres</h1>
                    <p>Comienzo del tercero.</p><p>Parte II</p>
                    <p>Comienzo de la segunda parte.</p></body></html>""")
            }
            val document = readEpubText(epub) as ReadingDocument.TextDocument
            val starts = document.paragraphs.filter { it.chapterStart }.map { it.text }
            assertEquals(listOf("Capítulo uno", "Capítulo dos", "Capítulo tres", "Parte II"), starts)
            assertTrue(document.paragraphs.first { it.text == "Parte II" }.partHeading)
            assertTrue(document.paragraphs.first { it.text == "Comienzo del segundo." }.chapterStart.not())
        } finally { epub.delete() }
    }
}
