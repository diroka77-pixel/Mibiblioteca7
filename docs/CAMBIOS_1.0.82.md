# Cambios de la versión 1.0.82 (escáner de ISBN)

## Problema

El escáner no leía los códigos de barras de los libros por dos motivos:

1. Usaba una foto rápida de la cámara (`TakePicturePreview`), que Android devuelve como
   miniatura de muy baja resolución. Las barras finas de un EAN-13 no se distinguían.
2. Cuando sí lo leía, escribía el ISBN en el buscador y después cambiaba de pantalla, y
   el cambio de pantalla vaciaba el buscador. El resultado desaparecía.

## Solución

- Nuevo escáner de Google Play services (`play-services-code-scanner`): vídeo en directo
  a pantalla completa, enfoque y zoom automáticos. No necesita el permiso de cámara.
- Solo acepta códigos de libro (EAN-13 que empiezan por 978 o 979, o ISBN-10) y avisa si
  se escanea otro código.
- Si el libro ya está en la biblioteca, se abre su ficha (reconoce el mismo libro aunque
  esté guardado con ISBN-10 y se escanee el de 13 cifras). Si no está, se busca en
  Descubrir con su información pública.
- Si el móvil no tiene Google Play services, se usa el método anterior como alternativa.
- Pruebas automáticas nuevas (`IsbnScannerTest`).

## Qué probar

Menú → «Escanear ISBN» con un libro físico: con uno que tengas en la biblioteca y con
otro que no. La primera vez Google Play services puede tardar unos segundos en preparar
el escáner.
