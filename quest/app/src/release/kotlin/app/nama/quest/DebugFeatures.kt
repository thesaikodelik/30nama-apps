package app.nama.quest

import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.toolkit.AppSystemActivity

/** Hot reload is a debug tool; the release build carries none of it. */
fun debugFeatures(activity: AppSystemActivity): List<SpatialFeature> = emptyList()

fun assetUri(path: String): String = "apk:///$path"

fun assetVersion(): Int = 0
