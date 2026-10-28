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
  val http: OkHttpClient by lazy {
    OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .apply { if (Filtered.on) addInterceptor(Filtered) }
        .build()
  }

  val model: AppModel by lazy { AppModel(this, MainScope(), ReadOnly.transport(), http = http) }

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

/**
 * Debug builds, `adb shell setprop debug.nama.filtered 1`: the app as it is on an Iranian network
 * that drops what it filters, as Irancell does (the Mac's `NAMA_FILTERED`). A request to a filtered
 * host (core's FilteredHosts) gets no answer and fails once its time is up.
 */
object Filtered : okhttp3.Interceptor {
  val on: Boolean by lazy {
    BuildConfig.DEBUG && runCatching {
      Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, "debug.nama.filtered") == "1"
    }.getOrDefault(false)
  }

  override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
    if (!app.nama.core.FilteredHosts.contains(chain.request().url.host)) return chain.proceed(chain.request())
    Thread.sleep(chain.readTimeoutMillis().toLong())
    throw java.io.IOException("filtered: ${chain.request().url.host}")
  }
}
