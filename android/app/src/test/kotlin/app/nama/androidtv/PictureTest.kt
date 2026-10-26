package app.nama.androidtv

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
}
