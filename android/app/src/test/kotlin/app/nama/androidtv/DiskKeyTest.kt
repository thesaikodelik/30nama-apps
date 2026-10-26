package app.nama.androidtv

import app.nama.androidtv.player.Disk
import org.junit.Assert.assertEquals
import org.junit.Test

class DiskKeyTest {
  @Test
  fun theSignatureIsLeftOut() {
    // The same segment, signed for two viewers at two times (the address, expiry and hash are made up).
    val a = "https://cdn.example.com/hls/10.0.0.1/1761400000/0123456789abcdef0123456789abcdef/film/720/seg-12.ts?t=1"
    val b = "https://cdn.example.com/hls/10.0.0.2/1761499999/fedcba9876543210fedcba9876543210/film/720/seg-12.ts"
    assertEquals(Disk.key(a), Disk.key(b))
    assertEquals("https://cdn.example.com/hls/film/720/seg-12.ts", Disk.key(a))
  }

  @Test
  fun anUnsignedAddressIsKeptAsItIs() {
    assertEquals("https://cdn.example.com/a/b.ts", Disk.key("https://cdn.example.com/a/b.ts"))
  }
}
