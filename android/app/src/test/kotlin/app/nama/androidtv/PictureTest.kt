package app.nama.androidtv

import app.nama.androidtv.player.EdgeLight
import app.nama.androidtv.player.Trim
import app.nama.androidtv.player.filmShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictureTest {
  @Test
  fun aWideFilmTheDecoderCallsSixteenNineIsShownWide() {
    assertEquals(2.39f, filmShape(1920f to 1080f, 1920f to 803f)!!, 0.01f)
  }

  @Test
  fun aPlaylistThatCallsEverythingSixteenNineIsNotBelieved() {
    assertEquals(2.39f, filmShape(1920f to 803f, 1920f to 1080f)!!, 0.01f)
  }

  @Test
  fun whenBothAgreeTheDecoderIsUsed() {
    assertEquals(16f / 9f, filmShape(1920f to 1080f, 1280f to 720f)!!, 0.001f)
  }

  @Test
  fun eitherAloneIsUsed() {
    assertEquals(4f / 3f, filmShape(null, 640f to 480f)!!, 0.001f)
    assertEquals(4f / 3f, filmShape(640f to 480f, null)!!, 0.001f)
    assertNull(filmShape(null, 0f to 0f))
  }

  /** A frame red on its left half and blue on its right, inside black bars above and below. */
  private fun frame(): IntArray {
    val w = 64
    val h = 36
    return IntArray(w * h) { i ->
      val x = i % w
      val y = i / w
      if (y < 6 || y >= 30) 0xFF000000.toInt() else if (x < 32) 0xFFC80000.toInt() else 0xFF0000C8.toInt()
    }
  }

  @Test
  fun eachEdgeTakesTheColoursAlongIt() {
    val light = EdgeLight.of(frame(), 64, 36, Trim(top = 6f / 36, bottom = 6f / 36))!!
    assertEquals(EdgeLight.ZONES, light.top.size)
    // Red at the start of the top edge, blue at its end; the bars cut away are not read.
    // 200 made more vivid and brighter: full, its other channels pushed down to 0.
    assertEquals(255, (light.top.first() shr 16) and 0xFF)
    assertEquals(0, light.top.first() and 0xFF)
    assertEquals(255, light.top.last() and 0xFF)
    assertEquals(light.top.toList(), light.bottom.toList())
    // The left side all red, the right all blue.
    assert(light.left.all { (it shr 16) and 0xFF > 0 && it and 0xFF == 0 })
    assert(light.right.all { it and 0xFF > 0 && (it shr 16) and 0xFF == 0 })
  }

  @Test
  fun withTheBarsReadTheTopEdgeIsBlack() {
    val light = EdgeLight.of(frame(), 64, 36)!!
    assertEquals(0xFF000000.toInt(), light.top.first())
  }

  @Test
  fun theLightGoesPartWayToTheNewColours() {
    val black = EdgeLight(IntArray(2) { 0xFF000000.toInt() }, IntArray(2), IntArray(1), IntArray(1))
    val white = EdgeLight(IntArray(2) { 0xFFFFFFFF.toInt() }, IntArray(2), IntArray(1), IntArray(1))
    assertEquals(127, black.toward(white, 0.5f).top[0] and 0xFF)
  }

  @Test
  fun aFrameTooSmallGivesNoLight() {
    assertNull(EdgeLight.of(IntArray(16), 4, 4))
  }
}
