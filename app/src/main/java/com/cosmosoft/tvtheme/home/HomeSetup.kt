package com.cosmosoft.tvtheme.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.provider.Settings
import java.net.Inet4Address

enum class TvPlatform { GoogleTv, AndroidTv, FireTv }

/** Works out whether pressing Home opens TV Theme, and what this TV offers to make that happen. */
object HomeSetup {
    const val GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"
    const val ANDROID_TV_LAUNCHER = "com.google.android.tvlauncher"
    const val FIRE_TV_LAUNCHER = "com.amazon.tv.launcher"
    val StockLaunchers = setOf(GOOGLE_TV_LAUNCHER, ANDROID_TV_LAUNCHER, FIRE_TV_LAUNCHER)

    fun platform(context: Context): TvPlatform = when {
        isInstalled(context, GOOGLE_TV_LAUNCHER) -> TvPlatform.GoogleTv
        isInstalled(context, FIRE_TV_LAUNCHER) -> TvPlatform.FireTv
        else -> TvPlatform.AndroidTv
    }

    /** The built-in launcher that's still active, if any. */
    fun stockLauncher(context: Context): String? = StockLaunchers.firstOrNull { isInstalled(context, it) }

    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun isHelperEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(context, HomeButtonService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    /** True when the Home button still lands on the built-in launcher. */
    fun needsSetup(context: Context) = !isDefaultHome(context) && !isHelperEnabled(context)

    /** The TV's IPv4 address on the local network, for the ADB instructions. */
    fun ipAddress(context: Context): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val links = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return null
        return links.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    /** Installed and not disabled. */
    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
