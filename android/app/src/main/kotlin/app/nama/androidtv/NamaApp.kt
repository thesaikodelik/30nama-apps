package app.nama.androidtv

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import app.nama.androidtv.ui.Images
import coil3.SingletonImageLoader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.MainScope
import okhttp3.OkHttpClient

class NamaApp : Application() {
  /** One connection pool for pictures and the player's helpers; the API has its own transport in core. */
  val http: OkHttpClient by lazy { OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build() }

  val model: AppModel by lazy { AppModel(this, MainScope(), ReadOnly.transport()) }

  /**
   * Whether this TV can afford motion that never ends (a drifting backdrop, a hero that turns by
   * itself). A box Android calls low on memory, or with less than 2.5 GB, is spared it.
   */
  val lively: Boolean by lazy {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    !am.isLowRamDevice && info.totalMem > 2_500L * 1024 * 1024
  }

  override fun onCreate() {
    super.onCreate()
    SingletonImageLoader.setSafe { Images.loader(this, http) }
  }
}
