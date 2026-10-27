package app.nama.androidtv

import app.nama.androidtv.data.Lively
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivelyTest {
  private fun picture(vararg parts: Pair<Int, Int>): IntArray = parts.flatMap { (argb, n) -> List(n) { argb } }.toIntArray()

  @Test
  fun theStrongestBrightHueWins() {
    // Mostly dark, some blue sky, a little orange: the sky.
    val hsb = Lively.of(picture(0xFF101010.toInt() to 400, 0xFF2060E0.toInt() to 120, 0xFFE08020.toInt() to 56))!!
    assertTrue("blue, ${hsb.hue}", hsb.hue in 0.55f..0.70f)
    assertTrue(hsb.saturation in 0.6f..0.95f)
    assertTrue(hsb.brightness >= 0.85f)
  }

  @Test
  fun greysAndShadowHaveNoColour() {
    assertNull(Lively.of(picture(0xFF808080.toInt() to 300, 0xFF101010.toInt() to 276)))
    assertNull("too little colour to count", Lively.of(picture(0xFF000000.toInt() to 575, 0xFFFF0000.toInt() to 1)))
  }

  @Test
  fun aDullColourIsMadeVivid() {
    val hsb = Lively.of(picture(0xFF6B4A3A.toInt() to 576))!!
    // Saturation 0.46 by itself, ×1.4.
    assertEquals(0.644f, hsb.saturation, 0.01f)
    assertTrue(hsb.brightness >= 0.85f)
  }
}
