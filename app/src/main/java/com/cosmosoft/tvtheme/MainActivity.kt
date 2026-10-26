package com.cosmosoft.tvtheme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.cosmosoft.tvtheme.billing.Premium
import com.cosmosoft.tvtheme.data.ProfileStore
import com.cosmosoft.tvtheme.plex.PlexSession
import com.cosmosoft.tvtheme.ui.LauncherApp

class MainActivity : ComponentActivity() {
    private var appsVersion by mutableIntStateOf(0)
    private var homeSignal by mutableIntStateOf(0)
    private val premium by lazy { Premium(this) }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            appsVersion++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profiles = ProfileStore(this)
        val plex = PlexSession(this)
        setContent {
            LauncherApp(profiles = profiles, premium = premium, plex = plex, appsVersion = appsVersion, homeSignal = homeSignal)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: pressing Home while we're already running lands here.
        homeSignal++
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        // Pick up anything installed or removed while another app was in front.
        appsVersion++
        premium.connect()
    }

    override fun onStop() {
        unregisterReceiver(packageReceiver)
        super.onStop()
    }
}
