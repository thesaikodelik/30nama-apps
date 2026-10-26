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
    if (savedInstanceState == null && model.phase == AppModel.Phase.Booting) model.boot()
    setContent {
      val nav = remember { Nav() }
      NamaRoot(model, nav, onExit = { finish() }, lively = nama.lively)
    }
  }
}
