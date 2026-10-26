package app.nama.androidtv

import app.nama.androidtv.ui.TV
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {
  /** Every string the Android app adds to core's is there in all three languages. */
  @Test
  fun everyStringIsTranslated() {
    for (s in TV.entries) {
      assertTrue("$s has no English", s.en.isNotBlank())
      assertTrue("$s has no Persian", s.fa.isNotBlank())
      assertTrue("$s has no Arabic", s.ar.isNotBlank())
    }
  }
}
