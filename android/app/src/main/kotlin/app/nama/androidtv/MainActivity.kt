package app.nama.androidtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import app.nama.androidtv.ui.Nav
import app.nama.androidtv.ui.NamaRoot

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val nama = application as NamaApp
    val model = nama.model
    // Debug builds: `adb shell am start -n app.nama.androidtv/.MainActivity --es lang fa` opens in
    // that language, to look at a screen in each without going through Settings.
    if (BuildConfig.DEBUG) intent.getStringExtra("lang")?.let { l -> app.nama.core.Lang.entries.firstOrNull { it.name == l }?.let { lang -> model.update { it.copy(lang = lang) } } }
    if (savedInstanceState == null && model.phase == AppModel.Phase.Booting) model.boot()
    setContent {
      // Debug builds: `--es tab settings` opens on that tab.
      val nav = remember { Nav().apply { if (BuildConfig.DEBUG) intent.getStringExtra("tab")?.let { t -> app.nama.androidtv.ui.Tab.entries.firstOrNull { it.name == t }?.let { tab = it } } } }
      NamaRoot(model, nav, onExit = { finish() }, lively = nama.lively)
    }
  }
}
