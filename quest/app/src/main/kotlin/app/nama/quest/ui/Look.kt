package app.nama.quest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.quest.R

// Same palette as the Mac app (30nama/Shared/Theme.swift), so the three apps read as one product.
object Ink {
  val bg0 = Color(0xFF07080B)
  val bg1 = Color(0xFF0E1016)
  val surface = Color(0xFF161922)
  val surface2 = Color(0xFF20242F)
  val text1 = Color(0xFFF4F1EA)
  val text2 = Color(0xB8F4F1EA)
  val text3 = Color(0x75F4F1EA)
  val accent = Color(0xFFF0454A)
  val good = Color(0xFF62D39B)
  val star = Color(0xFFF6C453)
  val focusInk = Color(0xFF0B0C10)
  val hairline = Color(0x24F4F1EA)
  // Panels float over the user's real room; a little more opacity than the Mac keeps text legible
  // against a bright window behind them.
  val glass = Color(0xE00E1016)
}

val Vazirmatn =
    FontFamily(
        Font(R.font.vazirmatn_regular, FontWeight.Normal),
        Font(R.font.vazirmatn_medium, FontWeight.Medium),
        Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
        Font(R.font.vazirmatn_bold, FontWeight.Bold),
        Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold),
    )

object Type {
  private fun s(size: Int, weight: FontWeight, color: Color = Ink.text1, line: Float = 1.45f) =
      TextStyle(
          fontFamily = Vazirmatn,
          fontSize = size.sp,
          fontWeight = weight,
          color = color,
          lineHeight = (size * line).sp,
      )

  val display = s(46, FontWeight.ExtraBold, line = 1.2f)
  val title = s(30, FontWeight.ExtraBold, line = 1.3f)
  val heading = s(22, FontWeight.Bold)
  val body = s(17, FontWeight.Medium, Ink.text2, 1.6f)
  val label = s(15, FontWeight.SemiBold)
  val caption = s(13, FontWeight.Medium, Ink.text3)
  val button = s(17, FontWeight.Bold)
}

/** The app's frosted panel: rounded glass with a hairline edge that catches the light. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), fill: Color = Ink.glass): Modifier =
    this.clip(shape)
        .background(fill, shape)
        .background(
            Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color.Transparent)),
            shape,
        )
        .border(1.dp, Ink.hairline, shape)

fun Modifier.hairline(shape: Shape, width: Dp = 1.dp) = this.border(width, Ink.hairline, shape)

/** Panels are separate windows, so each one sets the reading direction for itself. */
@Composable
fun Directional(rtl: Boolean, content: @Composable () -> Unit) {
  CompositionLocalProvider(
      LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
      content = content,
  )
}
