package app.nama.quest.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.quest.AppModel
import app.nama.quest.R
import app.nama.quest.Route
import app.nama.quest.Section
import app.nama.quest.ui.screens.AdvancedSearchScreen
import app.nama.quest.ui.screens.ArticleScreen
import app.nama.quest.ui.screens.BrowseScreen
import app.nama.quest.ui.screens.CatalogScreen
import app.nama.quest.ui.screens.NewsScreen
import app.nama.quest.ui.screens.PersonScreen
import app.nama.quest.ui.screens.ProfileScreen
import app.nama.quest.ui.screens.ScheduleScreen
import app.nama.quest.ui.screens.SearchScreen
import app.nama.quest.ui.screens.SettingsScreen
import app.nama.quest.ui.screens.TopListsScreen

/**
 * The bar over the wall, like the Mac's floating glass capsule: a back button that appears when
 * something is open on top of a section, the sections, and the language beside them.
 */
@Composable
fun NavBar(model: AppModel) {
  val lang by model.lang.collectAsState()
  val section by model.section.collectAsState()
  val routes by model.routes.collectAsState()
  val loc = model.loc
  Row(
      Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Image(painterResource(R.drawable.nama_logo), null, Modifier.size(40.dp))
      Text("30nama", style = Type.title.copy(fontSize = 26.sp, textDirection = TextDirection.Ltr))
      if (model.catalog.isDemo) {
        Text(
            X.demoBadge(lang),
            style = Type.caption.copy(color = Ink.accent),
            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Ink.accent.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
        )
      }
    }
    // Kept in place while hidden so the bar doesn't shift when it comes and goes.
    val canGoBack = routes.isNotEmpty()
    RoundButton(Icon.Back, { if (canGoBack) model.back() }, 52.dp, modifier = Modifier.alpha(if (canGoBack) 1f else 0f))
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0x1FF4F1EA)).border(1.dp, Ink.hairline, RoundedCornerShape(50)).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Section.entries.forEach { s ->
        val on = s == section && routes.isEmpty()
        val p = rememberPointed()
        Row(
            Modifier.pointable(p, { model.go(s) }, 1.04f)
                .clip(RoundedCornerShape(50))
                .background(if (on) Color(0x38F4F1EA) else if (p.hovered) Color(0x1AF4F1EA) else Color.Transparent)
                .padding(horizontal = if (s.iconOnly) 15.dp else 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val ink = if (on || p.hovered) Ink.text1 else Ink.text1.copy(alpha = 0.7f)
          when (s) {
            Section.search -> Glyph(Icon.Search, Modifier.size(22.dp), ink)
            Section.settings -> Glyph(Icon.Gear, Modifier.size(22.dp), ink)
            Section.profile -> Glyph(Icon.Person, Modifier.size(22.dp), ink)
            else -> Unit
          }
          if (!s.iconOnly) Text(loc(s.title), style = Type.label.copy(fontSize = 19.sp, color = ink))
        }
      }
    }
    Spacer(Modifier.width(52.dp))
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { LanguageChips(model, lang) }
  }
}

/** The page under the bar: what is open on top of the section, or the section itself. */
@Composable
fun WallContent(model: AppModel) {
  val section by model.section.collectAsState()
  val routes by model.routes.collectAsState()
  val top: Any = routes.lastOrNull() ?: section
  AnimatedContent(top, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) }, label = "wall") { page ->
    Box(Modifier.fillMaxSize()) {
      when (page) {
        Section.home -> HomeRows(model)
        Section.search -> SearchScreen(model)
        Section.movies -> CatalogScreen(model, "movies", model.loc(app.nama.core.S.movies))
        Section.series -> CatalogScreen(model, "series", model.loc(app.nama.core.S.series))
        Section.anime -> CatalogScreen(model, "anime", model.loc(app.nama.core.S.anime))
        Section.browse -> BrowseScreen(model)
        Section.profile -> ProfileScreen(model)
        Section.settings -> SettingsScreen(model)
        is Route.Catalog -> CatalogScreen(model, page.slug, page.title)
        is Route.AdvancedSearch -> AdvancedSearchScreen(model, page.query)
        is Route.PersonPage -> PersonScreen(model, page.person)
        Route.Schedule -> ScheduleScreen(model)
        Route.TopLists -> TopListsScreen(model)
        Route.News -> NewsScreen(model)
        is Route.ArticlePage -> ArticleScreen(model, page.article)
      }
    }
  }
}

/** For screens that need an account or the live catalogue (not the demo). */
@Composable
fun NeedsService(model: AppModel, content: @Composable (app.nama.quest.Services) -> Unit) {
  val services = model.services
  if (services == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(X.demoBadge(model.lang.value), style = Type.heading.copy(color = Ink.text3))
    }
  } else content(services)
}

/** Text sizes for the wall, which sits twice as far as the near panels. */
object WallType {
  val title = Type.title.copy(fontSize = 34.sp, lineHeight = 44.sp)
  val heading = Type.heading.copy(fontSize = 26.sp, lineHeight = 36.sp)
  val body = Type.body.copy(fontSize = 19.sp, lineHeight = 30.sp)
  val label = Type.label.copy(fontSize = 19.sp, lineHeight = 26.sp)
  val caption = Type.caption.copy(fontSize = 16.sp, lineHeight = 22.sp)
}
