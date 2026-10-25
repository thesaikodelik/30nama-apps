package app.nama.quest.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.AudioMix
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.S
import app.nama.quest.AppModel
import app.nama.quest.BuildConfig
import app.nama.quest.Settings
import app.nama.quest.SubtitleBackground
import app.nama.quest.SubtitleColor
import app.nama.quest.ui.Glyph
import app.nama.quest.ui.Icon
import app.nama.quest.ui.Ink
import app.nama.quest.ui.Pill
import app.nama.quest.ui.WallType
import app.nama.quest.ui.rememberImage

/** Sign in for a guest; for a member, who they are and Sign out, confirmed in place. */
@Composable
fun AccountGroup(model: AppModel, loc: Loc, lang: Lang) {
  val services = model.services ?: return
  val user = services.user
  val typedName by model.settings.displayName.state.collectAsState()
  var confirming by remember { mutableStateOf(false) }
  SettingsGroup(loc(S.account)) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Avatar(user?.avatar?.toString(), user?.name)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (user == null) {
          Text(SX.guestLine(lang), style = WallType.label)
          Text(SX.guestHint(lang), style = WallType.caption)
        } else {
          val name = user.name?.takeIf { it.isNotBlank() } ?: typedName.takeIf { it.isNotBlank() } ?: "—"
          Text(name, style = WallType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
          val plan = listOfNotNull(user.type?.takeIf { it.isNotBlank() }?.let(loc::accountType), loc.subscriptionLine(user)).joinToString(" · ")
          if (plan.isNotEmpty()) Text(plan, style = WallType.caption)
        }
      }
      if (user == null) Pill(loc(S.signIn), { model.requestSignIn() }, primary = true)
      else if (!confirming) Pill(loc(S.signOut), { confirming = true })
    }
    if (user != null && confirming) {
      RowDivider()
      Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(loc(S.signOutTitle), style = WallType.label)
        Text(loc(S.signOutBody), style = WallType.caption)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Pill(loc(S.cancel), { confirming = false })
          Pill(loc(S.signOut), {
            confirming = false
            model.signOut()
          }, primary = true)
        }
      }
    }
  }
}

@Composable
private fun Avatar(url: String?, name: String?) {
  val image = rememberImage(url, 128)
  Box(
      Modifier.size(60.dp).clip(CircleShape).background(Ink.surface2).border(1.dp, Ink.hairline, CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    when {
      image != null -> Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      !name.isNullOrBlank() -> Text(name.trim().take(1).uppercase(), style = WallType.heading)
      else -> Glyph(Icon.Person, Modifier.size(28.dp), Ink.text2)
    }
  }
}

/** The member's own name, for a profile the site keeps private. Members only, as on the Mac. */
@Composable
fun ProfileGroup(model: AppModel, loc: Loc, lang: Lang) {
  if (model.services?.signedIn != true) return
  val name by model.settings.displayName.state.collectAsState()
  SettingsGroup(loc(S.profile)) {
    SettingRow("${loc(S.yourName)} (${loc(S.optional)})", minLabel = 140.dp) {
      TextBox(name, { model.settings.displayName.set(it) }, width = 300.dp)
    }
    Text(SX.yourNameHint(lang), style = WallType.caption, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp))
  }
}

@Composable
fun LanguageGroup(model: AppModel, loc: Loc, lang: Lang) {
  SettingsGroup(loc(S.language)) {
    SettingRow(loc(S.language)) {
      Segments(listOf(Lang.en, Lang.fa, Lang.ar), lang, { it.nativeName }) { model.setLang(it) }
    }
  }
}

@Composable
fun ComfortGroup(model: AppModel, lang: Lang, loc: Loc) {
  val calm by model.settings.reduceMotion.state.collectAsState()
  SettingsGroup(SX.comfort(lang)) {
    ToggleRow(loc(S.reduceMotion), calm, calm, SX.reduceMotionHint(lang)) { model.settings.reduceMotion.set(it) }
  }
}

@Composable
fun PlaybackGroup(model: AppModel, loc: Loc, lang: Lang) {
  val s = model.settings
  val adaptive by s.preferAdaptive.state.collectAsState()
  val mix by s.audioMix.state.collectAsState()
  val autoplay by s.autoplayNext.state.collectAsState()
  val autoSkip by s.autoSkip.state.collectAsState()
  val buffer by s.bufferAhead.state.collectAsState()
  val calm by s.reduceMotion.state.collectAsState()
  SettingsGroup(loc(S.playback)) {
    SettingRow(loc(S.quality)) {
      Segments(listOf(false, true), adaptive, { if (it) loc(S.qualityAuto) else loc(S.qualityBest) }) { s.preferAdaptive.set(it) }
    }
    RowDivider()
    // Surround is left out: the headset plays two channels, so only the file's own mix is an alternative.
    SettingRow(loc(S.audio), SX.audioHint(lang)) {
      val shown = if (mix == AudioMix.original) AudioMix.original else AudioMix.stereo
      Segments(listOf(AudioMix.stereo, AudioMix.original), shown, { loc(if (it == AudioMix.stereo) S.audioStereo else S.audioOriginal) }) { s.audioMix.set(it) }
    }
    RowDivider()
    ToggleRow(loc(S.autoplay), autoplay, calm) { s.autoplayNext.set(it) }
    RowDivider()
    ToggleRow(loc(S.autoSkip), autoSkip, calm) { s.autoSkip.set(it) }
    RowDivider()
    // Read when a stream is opened, so it counts from the next one.
    SettingRow(loc(S.bufferTime), "${loc(S.seconds)} · ${SX.fromNextVideo(lang)}", minLabel = 140.dp) {
      Segments(Settings.BUFFER_CHOICES, buffer, { if (it == 0) loc(S.qualityAuto) else loc.num(it) }) { s.bufferAhead.set(it) }
    }
  }
}

@Composable
fun SubtitlesGroup(model: AppModel, loc: Loc) {
  val s = model.settings
  val sub by s.defaultSubtitle.state.collectAsState()
  val size by s.subtitleSize.state.collectAsState()
  val color by s.subtitleColor.state.collectAsState()
  val background by s.subtitleBackground.state.collectAsState()
  SettingsGroup(loc(S.subtitles)) {
    SettingRow(loc(S.defaultSubs)) {
      Segments(listOf("fa", "en", "off"), sub, { loc(if (it == "fa") S.persian else if (it == "en") S.english else S.off) }) { s.defaultSubtitle.set(it) }
    }
    RowDivider()
    SettingRow(loc(S.subSize)) {
      Segments(Settings.SUBTITLE_SIZES, size, { loc(if (it < 42) S.small else if (it >= 50) S.large else S.medium) }) { s.subtitleSize.set(it) }
    }
    RowDivider()
    SettingRow(loc(S.subColor), loc(color.label)) {
      Swatches(SubtitleColor.entries, color, { it.color }) { s.subtitleColor.set(it) }
    }
    RowDivider()
    SettingRow(loc(S.subBackground), minLabel = 140.dp) {
      Segments(SubtitleBackground.entries, background, { loc(it.label) }) { s.subtitleBackground.set(it) }
    }
    Sample(loc(S.sample), size, color, background)
  }
}

/** A live preview over a bright, busy picture, where the choice of background actually matters. */
@Composable
private fun Sample(text: String, size: Int, color: SubtitleColor, background: SubtitleBackground) {
  Box(
      Modifier.fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .height(124.dp)
          .clip(RoundedCornerShape(20.dp))
          .background(Brush.linearGradient(listOf(Color(0xFFF2A65A), Color(0xFFD9567B), Color(0xFF5B6CFF), Color(0xFF3FC1C9)))),
      contentAlignment = Alignment.Center,
  ) {
    val outlined = background == SubtitleBackground.none
    Text(
        text,
        style =
            WallType.label.copy(
                fontSize = (size * 0.55f).sp,
                lineHeight = (size * 0.75f).sp,
                fontWeight = FontWeight.SemiBold,
                color = color.color,
                textAlign = TextAlign.Center,
                shadow = if (outlined) Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 2f), 6f) else null,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier.padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = background.plateAlpha))
                .padding(horizontal = 16.dp, vertical = 6.dp),
    )
  }
}

/** Keys the user makes on other sites, kept on this headset; everything works without them. */
@Composable
fun ServicesGroup(model: AppModel, loc: Loc, lang: Lang) {
  val s = model.settings
  val subsource by s.subsourceKey.state.collectAsState()
  val tmdb by s.tmdbKey.state.collectAsState()
  SettingsGroup(loc(S.services)) {
    KeyField(loc(S.subsourceKey), loc(S.subsourceKeyHint), subsource, { s.subsourceKey.set(it.trim()) }, loc(S.showKey), loc(S.hideKey), loc(S.removeKey), SX.keySaved(lang))
    RowDivider()
    KeyField(loc(S.tmdbKey), loc(S.tmdbKeyHint), tmdb, { s.tmdbKey.set(it.trim()) }, loc(S.showKey), loc(S.hideKey), loc(S.removeKey), SX.keySaved(lang))
  }
}

@Composable
fun AboutGroup(model: AppModel, loc: Loc, lang: Lang) {
  val context = model.services?.context ?: LocalContext.current
  SettingsGroup(loc(S.about)) {
    FactRow(loc(S.version), loc.num(BuildConfig.VERSION_NAME))
    model.services?.api?.host?.let { host ->
      RowDivider()
      FactRow(loc(S.apiHost), runCatching { Uri.parse(host).host }.getOrNull() ?: host)
    }
    RowDivider()
    SettingRow(loc(S.reportProblem)) {
      Pill(loc(S.reportProblem), {
        if (reportProblem(context, lang)) model.showToast(SX.openingBrowser(lang)) else model.showToast(loc(S.error))
      })
    }
  }
}

/**
 * A new issue on the project's GitHub page, with what a report needs already filled in, as the
 * Mac does. Nothing about the account goes with it.
 */
private fun reportProblem(context: Context, lang: Lang): Boolean {
  val body =
      """
      **What happened / چه اتفاقی افتاد:**


      **What I expected / چه انتظاری داشتم:**


      ---
      30nama for Quest ${BuildConfig.VERSION_NAME} · Horizon OS ${Build.DISPLAY} · ${lang.name}
      """
          .trimIndent()
  val url = Uri.parse("https://github.com/thesaikodelik/30nama-apps/issues/new").buildUpon().appendQueryParameter("body", body).build()
  return try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
  } catch (e: Exception) {
    Log.w("Nama", "report: $e")
    false
  }
}
