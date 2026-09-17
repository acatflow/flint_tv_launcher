package com.flintv.launcher.ui.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.flintv.launcher.R
import com.flintv.launcher.ui.home.FlintColors
import com.flintv.launcher.ui.tvClickable
import com.flintv.launcher.ui.tvCombinedClickable
import kotlinx.coroutines.launch

data class AppInfo(
    val name: String,
    val packageName: String,
    // 圖示在背景執行緒先轉好點陣圖，讓格線組合時完全不做點陣圖運算
    // （在主執行緒轉圖曾造成進入頁面時整個畫面凍結）。
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AllAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 掃描全部套件並載入圖示很重，一律在背景執行緒做，載入期間顯示提示文字。
    var refreshKey by remember { mutableStateOf(0) }
    val apps by produceState<List<AppInfo>?>(initialValue = null, refreshKey) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AppUtils.getThirdPartyApps(context)
        }
    }

    // 從系統解除安裝畫面回到這裡時重新掃描：輕量版沒有靜默解除安裝，
    // 我們拿不到結果回呼，只能在重新取得焦點時比對一次清單。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val autoFocus = remember { FocusRequester() }
    LaunchedEffect(apps) {
        if (!apps.isNullOrEmpty()) { try { autoFocus.requestFocus() } catch (_: Exception) {} }
    }
    var contextMenuTarget by remember { mutableStateOf<AppInfo?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlintColors.backgroundGradient)
            .padding(24.dp)
    ) {
        // 標題不可聚焦，返回一律用遙控器的返回鍵
        Text(
            text = "< ${stringResource(R.string.all_apps)}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = FlintColors.textOnBg,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.long_press_tip),
            fontSize = 12.sp,
            color = FlintColors.textOnBg.copy(alpha = 0.4f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val scope = rememberCoroutineScope()
        val favPackages by FavoritesManager.getPackages(context).collectAsState(initial = emptySet())

        val list = apps
        if (list == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.loading_apps),
                    fontSize = 14.sp,
                    color = FlintColors.textOnBg.copy(alpha = 0.5f)
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(list) { index, app ->
                AppCard(
                    app = app,
                    isFavorite = app.packageName in favPackages,
                    focusRequester = if (index == 0) autoFocus else null,
                    onClick = {
                        val intent = AppUtils.getLaunchIntent(context, app.packageName)
                        if (intent != null) context.startActivity(intent)
                    },
                    // 二級操作一律走選單鍵：實測電視遙控器長按 OK 無法可靠傳成 onLongClick
                    onMenuAction = { contextMenuTarget = app }
                )
            }
        }

        contextMenuTarget?.let { app ->
            val isFav = app.packageName in favPackages
            AppContextMenu(
                appName = app.name,
                isFavorite = isFav,
                onToggleFavorite = {
                    scope.launch {
                        if (isFav) FavoritesManager.remove(context, app.packageName)
                        else {
                            FavoritesManager.add(context, app.packageName)
                            Toast.makeText(context, "★ ${app.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    contextMenuTarget = null
                },
                onUninstall = {
                    contextMenuTarget = null
                    requestUninstall(context, app.packageName)
                },
                onDismiss = { contextMenuTarget = null }
            )
        }
    }
}

/**
 * 請系統解除安裝指定套件。
 *
 * 輕量版只有這一條路。原版優先走 root 的 `pm uninstall` 做靜默解除安裝，
 * 該提權路徑已整段切除；`ACTION_DELETE` 會交給系統內建的解除安裝畫面，
 * 由**系統自己**向使用者確認，我們這邊不再另外跳一次確認框
 * （兩層確認對長者反而是困擾）。
 *
 * 系統畫面關閉後沒有結果回呼，清單改由 ON_RESUME 重新掃描來更新。
 */
private fun requestUninstall(context: Context, packageName: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.uninstall_unavailable), Toast.LENGTH_SHORT).show()
    }
}

/** 應用程式操作選單（按選單鍵叫出）：收藏切換 + 解除安裝。 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppContextMenu(
    appName: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .background(Color(0xFF1C1C1E), RoundedCornerShape(16.dp))
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                appName, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = Color.White, textAlign = TextAlign.Center, maxLines = 1
            )
            Spacer(modifier = Modifier.height(16.dp))
            val firstFocus = remember { FocusRequester() }
            ContextMenuRow(
                text = stringResource(
                    if (isFavorite) R.string.favorite_remove else R.string.favorite_add
                ),
                textColor = Color.White,
                focusRequester = firstFocus,
                onClick = onToggleFavorite,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ContextMenuRow(
                text = stringResource(R.string.uninstall),
                textColor = Color(0xFFFF6B6B),
                focusRequester = null,
                onClick = onUninstall,
            )
            // 對話框必須指定初始焦點，否則第一次按 OK 不會有反應
            LaunchedEffect(Unit) { try { firstFocus.requestFocus() } catch (_: Exception) {} }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContextMenuRow(text: String, textColor: Color, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color(0xFF2A2A2C), shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .tvClickable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (focused) Color.White else textColor
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    app: AppInfo,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onMenuAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f), label = "scale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = focusScale; scaleY = focusScale
                clip = false
            }
            // shadow 會強制建立一層算繪圖層再做模糊，整個格線一次組合時成本很高。
            // 只有取得焦點的那張需要光暈，未聚焦時原本 2dp 的陰影幾乎看不出來。
            .then(
                if (isFocused) Modifier.shadow(
                    elevation = 24.dp, shape = shape,
                    ambientColor = FlintColors.accent, spotColor = FlintColors.accent
                ) else Modifier
            )
            .background(if (isFocused) Color.White else FlintColors.card, shape)
            .then(if (isFocused) Modifier.border(5.dp, FlintColors.accent, shape) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .tvCombinedClickable(onClick = onClick, onMenu = onMenuAction),
        contentAlignment = Alignment.Center
    ) {
        if (isFavorite) {
            Text(
                text = "★",
                fontSize = 10.sp,
                color = FlintColors.accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            )
        }
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
                fontWeight = FontWeight.SemiBold,
                color = if (isFocused) FlintColors.accent else FlintColors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
