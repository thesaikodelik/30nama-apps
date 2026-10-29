package app.nama.androidtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import app.nama.androidtv.ui.Nav
import app.nama.androidtv.ui.NamaRoot

class MainActivity : ComponentActivity() {
  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    fromLauncher(intent)
  }

  /** A row of the TV's home screen opens its title, or plays it on, over home (so Back lands there). */
  private fun fromLauncher(intent: android.content.Intent) {
    val id = intent.getIntExtra(WatchNext.OPEN, 0).takeIf { it > 0 } ?: return
    (application as NamaApp).model.openFromLauncher(id, intent.getBooleanExtra(WatchNext.PLAY, false))
  }

  /**
   * Escape is Back: a box driven by a keyboard or an air mouse sends it, and so does a computer's
   * keyboard on the emulator. Android's own fallback from Escape to Back never came (Compose takes
   * the key first), so every card and sheet, which close on Back, stayed open.
   */
  override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
    if (event.action == android.view.KeyEvent.ACTION_DOWN) (application as NamaApp).model.lastKey = System.currentTimeMillis()
    if (event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
      if (event.action == android.view.KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
      return true
    }
    val handled = super.dispatchKeyEvent(event)
    // An arrow Compose found nowhere to go with (the last item of a page, its first row) stays
    // here. Left unhandled, Android's own view focus search took it and put focus back into Compose
    // from the top of its tree, wherever that landed.
    return handled || event.keyCode in arrows
  }

  private val arrows = setOf(
      android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
      android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
  )

  override fun onResume() {
    super.onResume()
    (application as NamaApp).model.updater.resumed()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val nama = application as NamaApp
    val model = nama.model
    // Debug builds: `adb shell am start -n app.nama.androidtv/.MainActivity --es lang fa` opens in
    // that language, to look at a screen in each without going through Settings.
    if (BuildConfig.DEBUG) intent.getStringExtra("lang")?.let { l -> app.nama.core.Lang.entries.firstOrNull { it.name == l }?.let { lang -> model.update { it.copy(lang = lang) } } }
    // Booting is the model's own state, not the activity's: after Android killed the app in the
    // background (low memory, or its install permission changed) the activity comes back with saved
    // state over a new model, which was never booted and held the splash for good.
    model.bootOnce()
    if (savedInstanceState == null) fromLauncher(intent)
    model.updater.start()
    if (BuildConfig.DEBUG && intent.getBooleanExtra("watchnext_demo", false)) model.demoWatchNext()
    setContent {
      // Debug builds: `--es tab settings` opens on that tab.
      val nav = remember { Nav().apply { if (BuildConfig.DEBUG) intent.getStringExtra("tab")?.let { t -> app.nama.androidtv.ui.Tab.entries.firstOrNull { it.name == t }?.let { tab = it } } } }
      NamaRoot(model, nav, onExit = { finish() }, lively = nama.lively)
    }
  }
}
