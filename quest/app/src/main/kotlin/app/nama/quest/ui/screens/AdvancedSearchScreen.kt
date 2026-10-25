package app.nama.quest.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.nama.core.AdvancedQuery
import app.nama.quest.AppModel
import app.nama.quest.ui.WallType

// Placeholder until the screen is built.
@Composable
fun AdvancedSearchScreen(model: AppModel, start: AdvancedQuery) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("AdvancedSearchScreen", style = WallType.heading) }
}
