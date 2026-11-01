package com.nokia.phone

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Số icon trên một hàng ở chế độ bảng icon. */
const val APP_COLS = 4

/** Icon app nạp theo nhu cầu (chỉ ô đang hiện), giữ tối đa 48 icon nhỏ; xóa sạch khi rời màn hình Ứng dụng. */
object AppIcons {
    private val cache = LruCache<String, ImageBitmap>(48)

    fun get(pkg: String): ImageBitmap? = cache.get(pkg)

    fun load(ctx: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return try {
            val bmp = ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96, Bitmap.Config.ARGB_8888)
            bmp.asImageBitmap().also { cache.put(pkg, it) }
        } catch (_: Exception) { null }
    }

    fun clear() = cache.evictAll()
}

/** Màn hình Ứng dụng: hai thẻ ở trên "Chữ" (danh sách tên) và "Icon" (bảng icon vuông, nhiều icon một hàng). */
@Composable
fun AppsScreen(
    apps: List<AppInfo>, mode: Int, sel: Int,
    onMode: (Int) -> Unit, onTap: (Int) -> Unit, onScroll: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Chữ", "Icon").forEachIndexed { i, t ->
                Box(Modifier.weight(1f).border(1.dp, INK).background(if (mode == i) INK else LCD)
                    .clickable { onMode(i) }.padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                    Text(t, color = if (mode == i) LCD else INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (mode == 0) Box(Modifier.weight(1f)) {
            Lines(apps.map { it.label }.ifEmpty { listOf("(trống)") }, sel, onTap, onScroll)
        } else IconGrid(apps, sel, onTap)
    }
}

@Composable
private fun ColumnScope.IconGrid(apps: List<AppInfo>, sel: Int, onTap: (Int) -> Unit) {
    val ctx = LocalContext.current
    val state = rememberLazyGridState()
    // Cuộn theo ô đang chọn khi di chuyển bằng phím
    LaunchedEffect(sel) {
        val vis = state.layoutInfo.visibleItemsInfo
        if (vis.isNotEmpty()) {
            val first = vis.first().index
            val last = vis.last().index
            val rows = maxOf(1, vis.size / APP_COLS)
            if (sel < first) state.scrollToItem(sel / APP_COLS * APP_COLS)
            else if (sel > last) state.scrollToItem(maxOf(0, sel / APP_COLS - rows + 1) * APP_COLS)
        }
    }
    if (apps.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth()) { Msg("(trống)") }
    else LazyVerticalGrid(GridCells.Fixed(APP_COLS), Modifier.weight(1f).fillMaxWidth(), state = state) {
        itemsIndexed(apps, key = { _, a -> a.pkg }) { i, a ->
            val icon by produceState<ImageBitmap?>(AppIcons.get(a.pkg), a.pkg) {
                if (value == null) value = withContext(Dispatchers.IO) { AppIcons.load(ctx, a.pkg) }
            }
            Box(Modifier.aspectRatio(1f).padding(3.dp)
                .then(if (i == sel) Modifier.background(INK) else Modifier.border(1.dp, INK))
                .clickable { onTap(i) }.padding(6.dp), contentAlignment = Alignment.Center) {
                icon?.let { Image(it, null, Modifier.fillMaxSize()) }
            }
        }
    }
    // Tên app đang chọn
    Text(apps.getOrNull(sel)?.label ?: "", Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        color = INK, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
}
