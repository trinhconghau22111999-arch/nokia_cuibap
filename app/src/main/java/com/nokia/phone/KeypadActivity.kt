package com.nokia.phone

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Bàn phím Nokia cố định ở nửa dưới khi chia đôi màn hình.
 * Cửa sổ không nhận focus, nên app ở nửa trên vẫn giữ focus (nhập chữ, nút Quay lại...).
 */
class KeypadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        NokiaState.keypad = this
        NokiaState.keypadAlive = true
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val l = IntArray(2)
            window.decorView.getLocationOnScreen(l)
            NokiaState.keypadTop = l[1]
        }
        setContent { KeypadScreen { k -> send(k) } }
    }

    private fun send(k: String) {
        when {
            NokiaState.lcdResumed -> NokiaState.listener?.invoke(k)      // nửa trên đang là màn hình LCD Nokia
            k == "END" || k == "SOFTL" ->                                // về màn hình Nokia (Menu = vào thẳng menu)
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("menu", k == "SOFTL"))
            else -> {                                                    // nửa trên là app khác
                val svc = NokiaAccessibilityService.instance
                if (svc != null) svc.control(k)
                else Toast.makeText(this, "Hãy bật dịch vụ Trợ năng phonecuibap để điều khiển app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (!isInMultiWindowMode) finish()
    }

    override fun onStop() {
        super.onStop()
        NokiaAccessibilityService.instance?.hideCursor()
    }

    override fun onDestroy() {
        NokiaState.keypadAlive = false
        if (NokiaState.keypad === this) NokiaState.keypad = null
        NokiaAccessibilityService.instance?.hideCursor()
        super.onDestroy()
    }
}

@Composable
fun KeypadScreen(onKey: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    BackHandler { }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF4469BC), Color(0xFF223C78))))
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 6.dp)
    ) {
        Keys { k ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onKey(k)
        }
    }
}
