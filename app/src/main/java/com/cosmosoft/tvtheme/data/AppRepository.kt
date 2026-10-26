package com.cosmosoft.tvtheme.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class LaunchableApp(
    val label: String,
    val component: ComponentName,
    /** True for apps built for TV (they declare LEANBACK_LAUNCHER and usually ship a banner). */
    val isTvApp: Boolean,
    val image: ImageBitmap,
) {
    /** Stable id used to remember favorites and hidden apps. */
    val key: String get() = component.flattenToString()

    fun launchIntent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(if (isTvApp) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

object AppRepository {
    private const val BANNER_WIDTH = 640
    private const val BANNER_HEIGHT = 360
    private const val ICON_SIZE = 192

    /** Must be called off the main thread: loading icons and banners is slow. */
    fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val tvApps = query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvPackages = tvApps.map { it.activityInfo.packageName }.toSet()
        val otherApps = query(pm, Intent.CATEGORY_LAUNCHER)
            .filter { it.activityInfo.packageName !in tvPackages }

        return (tvApps.map { it.toApp(pm, isTvApp = true) } + otherApps.map { it.toApp(pm, isTvApp = false) })
            .filter { it.component.packageName != context.packageName }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun query(pm: PackageManager, category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun ResolveInfo.toApp(pm: PackageManager, isTvApp: Boolean): LaunchableApp {
        val info = activityInfo
        val banner = if (isTvApp) info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm) else null
        val bitmap = if (banner != null) {
            banner.toBitmap(BANNER_WIDTH, BANNER_HEIGHT)
        } else {
            loadIcon(pm).toBitmap(ICON_SIZE, ICON_SIZE)
        }
        return LaunchableApp(
            label = loadLabel(pm).toString(),
            component = ComponentName(info.packageName, info.name),
            isTvApp = banner != null,
            image = bitmap.asImageBitmap(),
        )
    }
}
