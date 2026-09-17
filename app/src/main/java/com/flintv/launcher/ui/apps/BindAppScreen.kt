package com.flintv.launcher.ui.apps

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.flintv.launcher.R
import com.flintv.launcher.ui.home.FlintColors
import com.flintv.launcher.ui.tvClickable

private const val PREFS = "flint_prefs"
const val KEY_BOUND_TV = "bound_app_tv"
const val KEY_BOUND_SHOWS = "bound_app_shows"

/**
 * 首頁前兩張卡片（看電視、追劇）綁定的應用程式。
 * 只存套件名稱在本機偏好設定，啟動時再即時查詢是否還裝著。
 */
fun getBoundApp(context: Context, slot: String): String? {
    val key = if (slot == "tv") KEY_BOUND_TV else KEY_BOUND_SHOWS
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
}

fun setBoundApp(context: Context, slot: String, packageName: String) {
    val key = if (slot == "tv") KEY_BOUND_TV else KEY_BOUND_SHOWS
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(key, packageName).apply()
}

fun clearBoundApp(context: Context, slot: String) {
    val key = if (slot == "tv") KEY_BOUND_TV else KEY_BOUND_SHOWS
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().remove(key).apply()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BindAppScreen(slot: String, onDone: () -> Unit) {
    val context = LocalContext.current
    // 掃描套件與載入圖示放背景執行緒，在組合區塊直接做會凍結畫面
    val apps by produceState<List<AppInfo>?>(initialValue = null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AppUtils.getThirdPartyApps(context)
        }
    }
    val slotLabel = if (slot == "tv") stringResource(R.string.watch_tv) else stringResource(R.string.watch_shows)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlintColors.backgroundGradient)
            .padding(24.dp)
    ) {
        // 標題不可聚焦，返回一律用遙控器的返回鍵
        Text(
            text = "< ${stringResource(R.string.bind_select_app, slotLabel)}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = FlintColors.textOnBg,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val list = apps
        if (list == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.loading_apps),
                    fontSize = 16.sp,
                    color = FlintColors.textOnBg.copy(alpha = 0.5f)
                )
            }
        } else if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.bind_no_apps),
                    fontSize = 18.sp,
                    color = FlintColors.textOnBg.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val rows = list.chunked(5)
            val firstFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { try { firstFocus.requestFocus() } catch (_: Exception) {} }
            var isFirst = true
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)   // 留白給聚焦放大的卡片，否則會被捲動容器裁掉
            ) {
                rows.forEach { rowApps ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowApps.forEach { app ->
                            val fr = if (isFirst) { isFirst = false; firstFocus } else null
                            BindAppCard(
                                app = app,
                                onClick = {
                                    setBoundApp(context, slot, app.packageName)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.bind_done, app.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDone()
                                },
                                modifier = Modifier.weight(1f).height(140.dp),
                                focusRequester = fr
                            )
                        }
                        // 最後一列不足五格時補空白，避免卡片被拉寬
                        repeat(5 - rowApps.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BindAppCard(app: AppInfo, onClick: () -> Unit, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null) {
    var isFocused by remember { mutableStateOf(false) }
    // 防連點：遙控器 OK 鍵容易連發，重複觸發會綁兩次並跳兩個提示
    var lastClick by remember { mutableStateOf(0L) }
    fun debouncedClick() {
        val now = System.currentTimeMillis()
        if (now - lastClick > 500) { lastClick = now; onClick() }
    }
    val shape = RoundedCornerShape(12.dp)

    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "scale"
    )

    Box(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = focusScale; scaleY = focusScale
                clip = false
            }
            .shadow(
                elevation = if (isFocused) 24.dp else 2.dp,
                shape = shape,
                ambientColor = if (isFocused) FlintColors.accent else Color.Black.copy(alpha = 0.1f),
                spotColor = if (isFocused) FlintColors.accent else Color.Black.copy(alpha = 0.1f)
            )
            .background(if (isFocused) Color.White else FlintColors.card, shape)
            .then(if (isFocused) Modifier.border(5.dp, FlintColors.accent, shape) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .tvClickable { debouncedClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            if (app.iconBitmap != null) {
                Image(
                    bitmap = app.iconBitmap,
                    contentDescription = app.name,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(FlintColors.textTertiary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                fontSize = 12.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isFocused) FlintColors.accent else FlintColors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
