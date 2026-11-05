# Cambios de la versión 1.0.102 (lector de PDF)

- Corrige la 1.0.101: en horizontal la página se recortaba arriba y abajo y no se podía desplazar.
  La imagen se limitaba al alto de la pantalla y se centraba; ahora mide su alto real
  (`wrapContentHeight(unbounded = true)`) y empieza arriba.
- El arrastre con un dedo se calcula con la posición anterior del dedo, sin depender de que otro
  elemento haya marcado el gesto como usado.
