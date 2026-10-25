package app.nama.quest.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.Loc
import app.nama.core.S
import app.nama.quest.AppModel
import app.nama.quest.BuildConfig
import app.nama.quest.R
import app.nama.quest.ui.Ink
import app.nama.quest.ui.Type
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fadeBottom
import app.nama.quest.ui.screens.settings.AboutGroup
import app.nama.quest.ui.screens.settings.AccountGroup
import app.nama.quest.ui.screens.settings.ComfortGroup
import app.nama.quest.ui.screens.settings.LanguageGroup
import app.nama.quest.ui.screens.settings.PlaybackGroup
import app.nama.quest.ui.screens.settings.ProfileGroup
import app.nama.quest.ui.screens.settings.ServicesGroup
import app.nama.quest.ui.screens.settings.SubtitlesGroup

/**
 * Settings, as on the Mac: what the app is on the start side, the groups beside it. The wall is
 * wide and short, so the groups stand in three columns instead of one long list, and the whole
 * page scrolls together when a column runs past the bottom.
 */
@Composable
fun SettingsScreen(model: AppModel) {
  val lang by model.lang.collectAsState()
  val loc = Loc(lang)
  Row(
      Modifier.fillMaxSize().fadeBottom().verticalScroll(rememberScrollState()).padding(start = 44.dp, end = 44.dp, top = 18.dp, bottom = 72.dp),
      horizontalArrangement = Arrangement.spacedBy(30.dp),
  ) {
    Identity(loc)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(26.dp)) {
      AccountGroup(model, loc, lang)
      ProfileGroup(model, loc, lang)
      LanguageGroup(model, loc, lang)
      ComfortGroup(model, lang, loc)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(26.dp)) {
      PlaybackGroup(model, loc, lang)
      ServicesGroup(model, loc, lang)
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(26.dp)) {
      SubtitlesGroup(model, loc)
      AboutGroup(model, loc, lang)
    }
  }
}

/** The logo, what the app is, and its version. */
@Composable
private fun Identity(loc: Loc) {
  Column(Modifier.width(250.dp).padding(top = 34.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Image(painterResource(R.drawable.nama_logo), null, Modifier.size(96.dp))
    Text("30nama", style = Type.title.copy(fontSize = 34.sp, textDirection = TextDirection.Ltr))
    Text(loc(S.unofficial), style = WallType.body)
    Text(
        "${loc(S.version)} ${loc.num(BuildConfig.VERSION_NAME)}",
        style = WallType.caption,
        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Ink.hairline, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 5.dp),
    )
  }
}
