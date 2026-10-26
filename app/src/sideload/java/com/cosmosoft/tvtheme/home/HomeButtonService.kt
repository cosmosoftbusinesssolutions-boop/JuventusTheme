package com.cosmosoft.tvtheme.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.cosmosoft.tvtheme.MainActivity

/**
 * "Home button helper" for TVs that don't let you pick another home app (Google TV, Fire TV).
 *
 * It only reacts to two things: the remote's Home key, and the built-in launcher's window coming
 * to the front. Either way it opens TV Theme instead. It doesn't read screen content (see
 * res/xml/home_button_service.xml) and stores nothing.
 */
class HomeButtonService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) openLauncher()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Some TVs handle Home before accessibility services see it; catch the built-in launcher appearing instead.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName?.toString() in HomeSetup.StockLaunchers
        ) {
            openLauncher()
        }
    }

    override fun onInterrupt() = Unit

    private fun openLauncher() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
