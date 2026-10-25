package app.nama.quest.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.nama.quest.AppModel
import app.nama.quest.ui.WallType

// Placeholder until the screen is built.
@Composable
fun CatalogScreen(model: AppModel, slug: String, title: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("CatalogScreen", style = WallType.heading) }
}
