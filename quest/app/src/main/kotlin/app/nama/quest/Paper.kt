package app.nama.quest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout as TextLayout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import app.nama.core.Lang
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.AlphaMode
import com.meta.spatial.runtime.MaterialSidedness
import com.meta.spatial.runtime.SamplerConfig
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.runtime.SceneMesh
import com.meta.spatial.runtime.SceneTexture
import com.meta.spatial.runtime.TriangleMesh
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The magazine's paper, shared with quest/tools/blender/magazine.py, which builds the bodies with
 * the same sizes: the open magazine's pages dip into the gutter over [GUTTER] metres, and the
 * pages drawn here lie a hair above the model's own paper. Local frame, as the glTF models have
 * it: x across (the spine at x = 0 when open), y out of the paper, -z toward the top of the page.
 */
object Paper {
  const val CW = 0.21f
  const val CH = 0.28f
  const val CT = 0.009f
  const val PW = 0.34f
  const val PH = 0.45f
  const val PT = 0.007f
  const val GUTTER = 0.022f
  const val COVER = 0.0004f
  /** How far above the model's own paper the printed page lies. */
  private const val LIFT = 0.0007f

  /** Height of the open page's top at [d] metres from the spine. */
  fun top(d: Float): Float {
    val t = max(0f, 1f - d / GUTTER)
    return PT + COVER - PT * 0.95f * t * t + LIFT
  }

  /**
   * A rotation whose local +y is [up] and local -z is [ahead]: how the models are laid out,
   * whatever way round the SDK's lookRotation counts its forward axis.
   */
  fun basis(ahead: Vector3, up: Vector3): Quaternion {
    // Measured, not assumed: whichever way lookRotation counts forward, local -z ends up on [ahead].
    val q = Quaternion.lookRotation(ahead, up)
    return if ((q * Vector3(0f, 0f, -1f)).dot(ahead) > 0f) q else Quaternion.lookRotation(-ahead, up)
  }

  private val sampler = SamplerConfig()

  fun material(texture: SceneTexture): SceneMaterial =
      SceneMaterial(texture, AlphaMode.OPAQUE, SceneMaterial.PHYSICALLY_BASED_SHADER).apply {
        setRoughnessMetallicness(0.8f, 0f)
        // Both sides draw: the page behind each face is a hair further back, so it is the one seen
        // from that side, and the winding of the grid never matters.
        setSidedness(MaterialSidedness.DOUBLE_SIDED)
      }

  fun texture(bitmap: Bitmap): SceneTexture = SceneTexture(bitmap, sampler)

  /** Plain paper, until a page's picture arrives. */
  fun blank(): SceneTexture = texture(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFF4F0E8.toInt()) })

  /** A poster filling a page of [w] x [h] pixels, cropped from its centre like a full-bleed print. */
  fun page(poster: Bitmap, w: Int = 768, h: Int = 1016): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val s = max(w.toFloat() / poster.width, h.toFloat() / poster.height)
    val m = Matrix().apply {
      setScale(s, s)
      postTranslate((w - poster.width * s) / 2f, (h - poster.height * s) / 2f)
    }
    c.drawBitmap(poster, m, Paint(Paint.FILTER_BITMAP_FLAG))
    return out
  }

  /**
   * The inside of the cover: the row's name and its titles listed with their page numbers, set
   * like a magazine's contents, black on paper.
   */
  fun contents(context: Context, title: String, titles: List<String>, loc: app.nama.core.Loc, lang: Lang, w: Int = 768, h: Int = 1016): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFF4F0E8.toInt()) }
    val c = Canvas(out)
    val black = ResourcesCompat.getFont(context, R.font.vazirmatn_extrabold)
    val regular = ResourcesCompat.getFont(context, R.font.vazirmatn_medium)
    val pad = w * 0.09f
    val width = (w - 2 * pad).toInt()
    val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = w * 0.085f; color = 0xFF1D1A17.toInt() }
    val item = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; textSize = w * 0.04f; color = 0xFF1D1A17.toInt() }
    val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = w * 0.04f; color = 0xFFF0454A.toInt() }
    val rule = Paint().apply { color = 0x331D1A17; strokeWidth = 2f }
    val heading = StaticLayout.Builder.obtain(title, 0, title.length, head, width).setMaxLines(2).build()
    c.save()
    c.translate(pad, pad)
    heading.draw(c)
    c.restore()
    var y = pad + heading.height + w * 0.06f
    val line = item.textSize * 1.9f
    val rtl = lang.isRTL
    for ((i, t) in titles.withIndex()) {
      if (y + line > h - pad) break
      val page = loc.num(i + 3)
      val nw = num.measureText(page)
      // The page number on the reading side's far edge, the title from the near one.
      val tx = if (rtl) w - pad - item.measureText(t).coerceAtMost(width - nw * 2) else pad
      val clipped = android.text.TextUtils.ellipsize(t, item, width - nw * 2, android.text.TextUtils.TruncateAt.END).toString()
      c.drawText(clipped, if (rtl) w - pad - item.measureText(clipped) else tx, y + item.textSize, item)
      c.drawText(page, if (rtl) pad else w - pad - nw, y + item.textSize, num)
      c.drawLine(pad, y + line - item.textSize * 0.35f, w - pad, y + line - item.textSize * 0.35f, rule)
      y += line
    }
    return out
  }

  /**
   * A magazine's cover as print: the row's first poster, the masthead, the row's name and how many
   * titles it holds. Drawn here rather than in Compose because it goes onto a mesh.
   */
  fun cover(context: Context, poster: Bitmap?, title: String, count: String, lang: Lang, w: Int = 768, h: Int = 1024): Bitmap {
    val out = if (poster != null) page(poster, w, h) else Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF20242F.toInt()) }
    val c = Canvas(out)
    val shade = Paint().apply {
      shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0x80000000.toInt(), 0x00000000, 0x00000000, 0xE6000000.toInt()), floatArrayOf(0f, 0.28f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
    val black = ResourcesCompat.getFont(context, R.font.vazirmatn_extrabold)
    val regular = ResourcesCompat.getFont(context, R.font.vazirmatn_medium)
    val pad = w * 0.07f
    val rtl = lang.isRTL
    val mast = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = w * 0.17f }
    // The masthead keeps its two colours: 30 in red, the rest in white.
    val red = if (lang == Lang.fa) "۳۰" else "30"
    val rest = if (lang == Lang.fa) "نما" else "nama"
    val redW = mast.measureText(red)
    val restW = mast.measureText(rest)
    val y = pad + mast.textSize * 0.9f
    val x0 = if (rtl) w - pad - redW - restW else pad
    // Persian reads "۳۰" then "نما" from the right: the red part is on the right.
    if (rtl) {
      mast.color = 0xFFFFFFFF.toInt(); c.drawText(rest, x0, y, mast)
      mast.color = 0xFFF0454A.toInt(); c.drawText(red, x0 + restW, y, mast)
    } else {
      mast.color = 0xFFF0454A.toInt(); c.drawText(red, x0, y, mast)
      mast.color = 0xFFFFFFFF.toInt(); c.drawText(rest, x0 + redW, y, mast)
    }
    val rule = Paint().apply { color = 0xFFFFFFFF.toInt(); strokeWidth = w * 0.004f }
    c.drawLine(pad, y + w * 0.035f, w - pad, y + w * 0.035f, rule)
    val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = w * 0.105f; color = 0xFFFFFFFF.toInt() }
    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; textSize = w * 0.045f; color = 0xCCFFFFFF.toInt() }
    val width = (w - 2 * pad).toInt()
    val align = TextLayout.Alignment.ALIGN_NORMAL
    val name = StaticLayout.Builder.obtain(title, 0, title.length, big, width).setAlignment(align).setMaxLines(2).build()
    val sub = StaticLayout.Builder.obtain(count, 0, count.length, small, width).setAlignment(align).build()
    c.save()
    c.translate(pad, h - pad - sub.height - name.height - w * 0.02f)
    name.draw(c)
    c.translate(0f, name.height + w * 0.02f)
    sub.draw(c)
    c.restore()
    return out
  }
}

/** A [PaperShape] on the GPU: the open pages and the leaf that turns, each face with its own picture. */
class Sheet(cols: Int, rows: Int, front: SceneTexture, back: SceneTexture) {
  val shape = PaperShape(cols, rows)
  private val frontMat = Paper.material(front)
  private val backMat = Paper.material(back)
  private val half = cols * rows * 6
  private val tri = TriangleMesh(shape.count * 2, half * 2, intArrayOf(0, half, half, half), arrayOf(frontMat, backMat))
  private val col = IntArray(shape.count * 2) { -1 }
  val mesh: SceneMesh

  init {
    tri.updatePrimitives(0, shape.indices)
    tri.updateGeometry(0, shape.pos, shape.nor, shape.uv, col)
    mesh = SceneMesh.fromTriangleMesh(tri, false)
  }

  fun setFront(t: SceneTexture) = frontMat.setAlbedoTexture(t)

  fun setBack(t: SceneTexture) = backMat.setAlbedoTexture(t)

  fun lay(side: Float, turn: Float) {
    shape.lay(side, turn)
    tri.updateGeometry(0, shape.pos, shape.nor, shape.uv, col)
    mesh.updateWithTriangleMesh(tri, false)
  }
}
