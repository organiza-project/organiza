package org.organiza.android

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.organiza.domain.PackageName
import org.organiza.domain.port.BlockSurface

/** Full-screen touch-consuming overlay owned by the accessibility service (ADR-0005). */
class AccessibilityBlockSurface(private val service: AccessibilityService) : BlockSurface {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var overlay: View? = null

    override fun showOverlay(blocked: PackageName) {
        if (overlay != null) return
        val view = TextView(service).apply {
            setText(R.string.overlay_message)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            textSize = 22f
            isClickable = true
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        windows.addView(view, params)
        overlay = view
    }

    override fun dismissOverlay() {
        overlay?.let(windows::removeView)
        overlay = null
    }

    override fun goHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }
}
