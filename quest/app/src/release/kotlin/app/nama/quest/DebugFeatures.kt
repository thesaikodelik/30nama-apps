package app.nama.quest

import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.PanelRegistration

/** Hot reload and the note for Claude are debug tools; the release build carries none of them. */
fun debugFeatures(activity: AppSystemActivity): List<SpatialFeature> = emptyList()

fun debugPanels(): List<PanelRegistration> = emptyList()

@Suppress("UNUSED_PARAMETER")
fun debugTick(activity: AppSystemActivity, model: AppModel, shelf: Shelf) = Unit

fun assetUri(path: String): String = "apk:///$path"

fun assetVersion(): Int = 0
