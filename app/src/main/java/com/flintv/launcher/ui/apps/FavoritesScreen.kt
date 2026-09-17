package com.flintv.launcher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.flintv.launcher.R
import com.flintv.launcher.ui.home.FlintColors
import com.flintv.launcher.ui.home.rememberAutoFocus
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val scope = rememberCoroutineScope()
    val favPackages by FavoritesManager.getPackages(context).collectAsState(initial = emptySet())

    // 收藏清單本身是資料流，套件被解除安裝後這裡會自動少一項；
    // 圖示載入同樣放背景執行緒，避免在弱效能電視盒上堵住 UI。
    val favApps by produceState<List<AppInfo>?>(initialValue = null, favPackages) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            favPackages.mapNotNull { pkg ->
                try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    val bmp = try {
                        pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
                    } catch (_: Exception) { null }
                    AppInfo(pm.getApplicationLabel(ai).toString(), pkg, bmp)
                } catch (_: Exception) { null }
            }.sortedBy { it.name.lowercase() }
        }
    }

    var contextMenuTarget by remember { mutableStateOf<AppInfo?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlintColors.backgroundGradient)
            .padding(24.dp)
    ) {
        Text(
            text = "< ${stringResource(R.string.favorites)}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = FlintColors.textOnBg,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val list = favApps
        if (list == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.loading_apps),
                    fontSize = 16.sp,
                    color = FlintColors.textTertiary
                )
            }
        } else if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.no_favorites),
                        fontSize = 18.sp,
                        color = FlintColors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_favorites_hint),
                        fontSize = 14.sp,
                        color = FlintColors.textTertiary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val autoFocus = rememberAutoFocus()
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(list) { index, app ->
                    AppCard(
                        app = app,
                        isFavorite = true,
                        focusRequester = if (index == 0) autoFocus else null,
                        onClick = {
                            val intent = AppUtils.getLaunchIntent(context, app.packageName)
                            if (intent != null) context.startActivity(intent)
                        },
                        // 二級操作走選單鍵，與「全部應用」一致
                        onMenuAction = { contextMenuTarget = app }
                    )
                }
            }
        }

        contextMenuTarget?.let { app ->
            AppContextMenu(
                appName = app.name,
                isFavorite = true,
                onToggleFavorite = {
                    scope.launch { FavoritesManager.remove(context, app.packageName) }
                    contextMenuTarget = null
                },
                onUninstall = {
                    contextMenuTarget = null
                    // 先移出收藏再交給系統解除安裝：系統畫面沒有結果回呼，
                    // 若使用者中途取消，重新收藏即可，總比留下一個開不起來的項目好。
                    scope.launch { FavoritesManager.remove(context, app.packageName) }
                    requestUninstallFromFavorites(context, app.packageName)
                },
                onDismiss = { contextMenuTarget = null }
            )
        }
    }
}

/** 與「全部應用」同一條路：交給系統內建的解除安裝畫面，由系統自己向使用者確認。 */
private fun requestUninstallFromFavorites(context: android.content.Context, packageName: String) {
    try {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_DELETE,
                android.net.Uri.parse("package:$packageName")
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        android.widget.Toast.makeText(
            context, context.getString(R.string.uninstall_unavailable),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
