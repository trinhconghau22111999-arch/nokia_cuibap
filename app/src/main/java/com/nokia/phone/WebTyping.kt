package com.nokia.phone

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gõ chữ vào ô nhập đang được chọn trong trang web (Zalo Web / YouTube / TV) bằng phím số của app,
 * dùng bộ gõ multi-tap như soạn tin (TextEntry). Không dùng bàn phím ảo của máy.
 */
object WebTyping {
    // Đọc nội dung ô nhập đang chọn (và đưa con trỏ về cuối). null = chưa chọn ô nhập chữ nào.
    private const val READ_JS = "(function(){try{var e=document.activeElement;if(!e)return null;var t=(e.tagName||'');" +
        "if(t=='INPUT'||t=='TEXTAREA'){var ty=(e.type||'text').toLowerCase();" +
        "if(['button','submit','checkbox','radio','file','image','reset','range','color'].indexOf(ty)>=0)return null;" +
        "try{var n=e.value.length;e.setSelectionRange(n,n)}catch(x){}return e.value}" +
        "if(e.isContentEditable){var s=window.getSelection();var r=document.createRange();r.selectNodeContents(e);r.collapse(false);" +
        "s.removeAllRanges();s.addRange(r);var v=e.innerText||'';return v.replace(/\\n$/,'')}" +
        "return null}catch(x){return null}})()"

    private fun decode(r: String?): String? {
        if (r == null || r == "null") return null
        return try { JSONArray("[$r]").getString(0) } catch (e: Exception) { null }
    }

    /** k = "0".."9", "*" (dấu tiếng Việt) hoặc "DEL" (xóa một ký tự). */
    fun type(v: View?, entry: TextEntry, k: String) {
        val w = v as? WebView ?: return
        val now = SystemClock.uptimeMillis()
        w.requestFocus()
        w.evaluateJavascript(READ_JS) { r ->
            val cur = decode(r) ?: return@evaluateJavascript
            if (entry.text != cur) entry.text = cur          // đồng bộ với nội dung thật của ô
            val old = entry.text
            if (k == "DEL") entry.backspace() else entry.key(k, now)
            val nw = entry.text
            var p = 0
            while (p < old.length && p < nw.length && old[p] == nw[p]) p++
            val del = old.length - p
            val ins = nw.substring(p)
            if (del == 0 && ins.isEmpty()) return@evaluateJavascript
            w.evaluateJavascript(
                "(function(d,t){if(!document.activeElement)return;for(var i=0;i<d;i++)document.execCommand('delete');" +
                    "if(t)document.execCommand('insertText',false,t)})($del,${JSONObject.quote(ins)})", null)
        }
    }

    /** Phím Enter (gửi tin / tìm kiếm). */
    fun enter(v: View?) {
        val w = v ?: return
        w.requestFocus()
        w.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        w.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}
