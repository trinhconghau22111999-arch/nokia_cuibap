package com.nokia.phone

import android.graphics.Color as AColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private fun detach(v: View) { (v.parent as? ViewGroup)?.removeView(v) }

@Composable
private fun HostView(v: View) {
    key(v) { AndroidView(factory = { detach(v); v }, modifier = Modifier.fillMaxSize()) }
}

/** Cửa sổ phủ kín TOÀN màn hình điện thoại (ẩn thanh trạng thái / điều hướng), nền đen. */
@Composable
private fun FullDialog(content: View, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        val host = LocalView.current
        SideEffect {
            val w = (host.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.setBackgroundDrawable(ColorDrawable(AColor.BLACK))
            w.setDimAmount(0f)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= 28) {
                val lp = w.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                w.attributes = lp
            }
            WindowCompat.setDecorFitsSystemWindows(w, false)
            val c = WindowInsetsControllerCompat(w, host)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) { HostView(content) }
    }
}

/**
 * Vùng hiển thị của app con TV / YouTube.
 * - Bình thường: nằm trong ô LCD phía trên.
 * - Full màn hình khi: xoay ngang máy hoặc bấm nút phóng to của trang (video toàn màn hình).
 */
@Composable
fun WebArea(pane: View?, custom: View?, onClose: () -> Unit) {
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val full: View? = custom ?: if (landscape) pane else null
    Box(Modifier.fillMaxSize().clipToBounds()) {
        if (pane != null && full !== pane) HostView(pane)
    }
    if (full != null) FullDialog(full, onClose)
}
