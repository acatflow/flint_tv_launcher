package com.flintv.launcher.ui.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import android.widget.Toast
import com.flintv.launcher.R
import com.flintv.launcher.navigation.Screen
import com.flintv.launcher.ui.tvClickable
import com.flintv.launcher.ui.tvCombinedClickable
import com.flintv.launcher.ui.apps.AppUtils
import com.flintv.launcher.ui.apps.clearBoundApp
import com.flintv.launcher.ui.apps.getBoundApp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * 啟動器首頁。
 *
 * 輕量版共四張卡片：看電視、看劇、我的最愛、全部應用。
 * 原版的第三張「手機遙控」屬於遠端協助鏈路，已整組切除。
 * 前兩張是可綁定的應用程式捷徑，後兩張導向本機頁面。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit, onBindApp: (String) -> Unit = {}) {
    val context = LocalContext.current
    val theme = LocalThemeConfig.current

    // 防連點：遙控器 OK 鍵容易連發，500 毫秒內的重複觸發一律忽略。
    var lastClickTime by remember { mutableStateOf(0L) }
    fun debounced(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastClickTime > 500) {
            lastClickTime = now
            action()
        }
    }

    // 開場動畫每個行程只播一次：按首頁鍵讓系統重建 Activity 時不重播。
    var showSplash by rememberSaveable { mutableStateOf(!LauncherActivity.splashShownThisProcess) }
    LaunchedEffect(Unit) {
        if (showSplash) { delay(1500); showSplash = false }
        LauncherActivity.splashShownThisProcess = true
    }

    val firstCardFocus = remember { FocusRequester() }
    LaunchedEffect(showSplash) {
        if (!showSplash) try { firstCardFocus.requestFocus() } catch (_: Exception) {}
    }
    LaunchedEffect(Unit) {
        LauncherActivity.homeEvent.collect {
            try { firstCardFocus.requestFocus() } catch (_: Exception) {}
            LauncherActivity.clearHomeEvent()
        }
    }

    if (showSplash) {
        SplashScreen(theme)
        return
    }

    fun launchOrBind(slot: String) = debounced {
        val pkg = getBoundApp(context, slot)
        if (pkg != null) {
            val intent = AppUtils.getLaunchIntent(context, pkg)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                // 綁定的應用程式已被移除：清掉綁定並直接帶使用者去重綁，而不是按了沒反應。
                clearBoundApp(context, slot)
                Toast.makeText(context, context.getString(R.string.bind_app_removed), Toast.LENGTH_SHORT).show()
                onBindApp(slot)
            }
        } else {
            onBindApp(slot)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundGradient)
    ) {
        if (theme.orbEnabled) {
            AmbientBreathingOrbs(theme.orbColors)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            StatusBar(context, onNavigate)

            val items = listOf(
                HomeItem(stringResource(R.string.watch_tv), R.drawable.ic_tv, 0,
                    { launchOrBind("tv") }, { debounced { onBindApp("tv") } }),
                HomeItem(stringResource(R.string.watch_shows), R.drawable.ic_play, 1,
                    { launchOrBind("shows") }, { debounced { onBindApp("shows") } }),
                HomeItem(stringResource(R.string.favorites), R.drawable.ic_star, 2,
                    { debounced { onNavigate(Screen.Favorites) } }, null),
                HomeItem(stringResource(R.string.all_apps), R.drawable.ic_grid, 3,
                    { debounced { onNavigate(Screen.AllApps) } }, null),
            )

            // 卡片數量從五張減為四張，偏好記憶下來的索引可能已超出範圍。
            // 不夾回有效區間的話，沒有任何一張卡片會拿到初始焦點，
            // 使用者按遙控器會完全沒反應，看起來就像當機。
            LauncherActivity.lastFocusIndex =
                LauncherActivity.lastFocusIndex.coerceIn(0, items.lastIndex)

            when (theme.layout) {
                HomeLayout.GRID -> GridLayout(items, firstCardFocus)
                HomeLayout.HERO_SIDE -> HeroSideLayout(items, firstCardFocus)
                HomeLayout.RAIL -> RailLayout(items, firstCardFocus)
                HomeLayout.CINEMA -> CinemaLayout(items, firstCardFocus)
            }
        }
    }
}

data class HomeItem(
    val title: String,
    @DrawableRes val iconRes: Int,
    val cardIndex: Int,
    val onClick: () -> Unit,
    /** 選單鍵的二級操作（例如換綁應用程式）；沒有二級操作的卡片傳 null。 */
    val onMenu: (() -> Unit)?,
)

@Composable
private fun ItemCard(item: HomeItem, modifier: Modifier, isLarge: Boolean, focusRequester: FocusRequester? = null) {
    ThemedCard(
        title = item.title,
        iconRes = item.iconRes,
        cardIndex = item.cardIndex,
        modifier = modifier,
        onClick = item.onClick,
        onMenu = item.onMenu,
        isLarge = isLarge,
        focusRequester = focusRequester
    )
}

/** 焦點記憶：只有上次離開時所在的那張卡片會拿到 FocusRequester。 */
private fun focusFor(item: HomeItem, firstFocus: FocusRequester): FocusRequester? =
    if (LauncherActivity.lastFocusIndex == item.cardIndex) firstFocus else null

// ═══════════════ 設計 A — Apple TV ═══════════════
/** 前兩張為大卡置於上排，其餘平均分配於下排；不寫死張數，日後增減卡片不會越界。 */
@Composable
private fun GridLayout(items: List<HomeItem>, firstFocus: FocusRequester) {
    val top = items.take(2)
    val bottom = items.drop(2)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (top.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().weight(5f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                top.forEach { item ->
                    ItemCard(item, Modifier.weight(1f).fillMaxHeight(), true, focusFor(item, firstFocus))
                }
            }
        }
        if (bottom.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().weight(3f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                bottom.forEach { item ->
                    ItemCard(item, Modifier.weight(1f).fillMaxHeight(), false, focusFor(item, firstFocus))
                }
            }
        }
    }
}

// ═══════════════ 設計 E — 豪華飯店 ═══════════════
// 頂部品牌與問候語（金色點綴），中段一排方形磁貼，底部細金線文字。
private val GOLD = Color(0xFFD4AF37)

@Composable
private fun HeroSideLayout(items: List<HomeItem>, firstFocus: FocusRequester) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) { in 5..10 -> "早安"; in 11..17 -> "午安"; else -> "晚安" }
    val tileBgs = listOf(
        Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF2D5A8F))),
        Brush.linearGradient(listOf(Color(0xFF5A1A2A), Color(0xFF8A3040))),
        Brush.linearGradient(listOf(Color(0xFF4A3A1A), Color(0xFF7A6A2A))),
        Brush.linearGradient(listOf(Color(0xFF2A1A4A), Color(0xFF4A3A6A))),
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Text("FLINT TV", color = GOLD.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
        Spacer(Modifier.height(4.dp))
        Text(greeting, color = Color.White.copy(alpha = 0.78f), fontSize = 34.sp, fontWeight = FontWeight.Light)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                // 一排方形磁貼務必用 weight(1f) + aspectRatio(1f)：依「寬度」等分。
                // 若寫成 fillMaxHeight().aspectRatio()，高度大時總寬會超出螢幕、末尾磁貼被裁掉。
                LuxuryTile(
                    item = item,
                    bg = tileBgs.getOrElse(item.cardIndex) { tileBgs.first() },
                    focusRequester = focusFor(item, firstFocus),
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                )
            }
        }
        Text("Flint TV · 智慧電視", color = GOLD.copy(alpha = 0.28f), fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LuxuryTile(item: HomeItem, bg: Brush, focusRequester: FocusRequester?, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .scale(if (focused) 1.08f else 1f)
            .shadow(if (focused) 26.dp else 6.dp, shape, spotColor = GOLD, ambientColor = Color.Black)
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, if (focused) GOLD.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f), shape)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) LauncherActivity.lastFocusIndex = item.cardIndex }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .tvCombinedClickable(onClick = item.onClick, onMenu = item.onMenu),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(item.iconRes), item.title, modifier = Modifier.size(if (focused) 46.dp else 40.dp))
            Spacer(Modifier.height(12.dp))
            Text(item.title, color = if (focused) Color.White else Color.White.copy(alpha = 0.7f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════ 設計 G — 水墨 ═══════════════
// 墨韻暈染背景 + 不等寬網格（左欄略寬、每欄上格略高），襯線標題與副標在左下，圖示在左上。
private val INK_GOLD = Color(0xFFB4A078)

@Composable
private fun RailLayout(items: List<HomeItem>, firstFocus: FocusRequester) {
    val subs = listOf("直播頻道", "影視劇場", "珍藏內容", "應用中心")
    fun bg(vararg c: Long) = Brush.linearGradient(c.map { Color(it) })
    val gradients = listOf(
        bg(0xFF0D1A2E, 0xFF162D4A, 0xFF1A3555), // 看電視
        bg(0xFF2A0F0F, 0xFF5A1A1A, 0xFF7A2525), // 看劇
        bg(0xFF1F1A0F, 0xFF3A3018, 0xFF4A3D20), // 我的最愛
        bg(0xFF1A152A, 0xFF2A2040, 0xFF352A50), // 全部應用
    )
    // 偶數索引走左欄、奇數索引走右欄，維持原設計的不等寬視覺。
    val left = items.filterIndexed { i, _ -> i % 2 == 0 }
    val right = items.filterIndexed { i, _ -> i % 2 == 1 }
    Box(modifier = Modifier.fillMaxSize()) {
        InkWashBackdrop()
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InkColumn(left, 1.2f, subs, gradients, firstFocus)
            InkColumn(right, 1f, subs, gradients, firstFocus)
        }
    }
}

@Composable
private fun RowScope.InkColumn(
    cells: List<HomeItem>,
    widthWeight: Float,
    subs: List<String>,
    gradients: List<Brush>,
    firstFocus: FocusRequester,
) {
    Column(
        Modifier.weight(widthWeight).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        cells.forEachIndexed { row, item ->
            InkCell(
                item = item,
                bg = gradients.getOrElse(item.cardIndex) { gradients.first() },
                subtitle = subs.getOrElse(item.cardIndex) { "" },
                focusRequester = focusFor(item, firstFocus),
                modifier = Modifier.fillMaxWidth().weight(if (row == 0) 1.1f else 1f),
            )
        }
    }
}

@Composable
private fun InkWashBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize().blur(90.dp)) {
        drawCircle(Color(0xFF3C3C46), radius = size.minDimension * 0.35f, center = Offset(size.width * 0.2f, size.height * 0.3f), alpha = 0.15f)
        drawCircle(Color(0xFF32323C), radius = size.minDimension * 0.4f, center = Offset(size.width * 0.75f, size.height * 0.65f), alpha = 0.12f)
        drawCircle(Color(0xFF37323C), radius = size.minDimension * 0.3f, center = Offset(size.width * 0.5f, size.height * 0.85f), alpha = 0.1f)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun InkCell(item: HomeItem, bg: Brush, subtitle: String, focusRequester: FocusRequester?, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .scale(if (focused) 1.03f else 1f)
            .shadow(if (focused) 22.dp else 4.dp, shape, spotColor = INK_GOLD, ambientColor = Color.Black)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (focused) INK_GOLD.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f), shape)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) LauncherActivity.lastFocusIndex = item.cardIndex }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .tvCombinedClickable(onClick = item.onClick, onMenu = item.onMenu)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Image(painterResource(item.iconRes), item.title, modifier = Modifier.size(38.dp))
            Column {
                Text(item.title, color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
                    fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, letterSpacing = 2.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp, letterSpacing = 1.sp)
            }
        }
    }
}

// ═══════════════ 設計 H — 電影院 ═══════════════
// 上半為影院英雄區（品牌與標語），下半一排暗色卡片，卡頂彩色高亮條，取得焦點時鋪滿並發光。
@Composable
private fun CinemaLayout(items: List<HomeItem>, firstFocus: FocusRequester) {
    val accents = listOf(Color(0xFF60A5FA), Color(0xFFFB7185), Color(0xFFFBBF24), Color(0xFFA78BFA))
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(6f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FLINT TV", color = Color.White.copy(alpha = 0.6f), fontSize = 46.sp, fontWeight = FontWeight.Light, letterSpacing = 10.sp)
                Spacer(Modifier.height(10.dp))
                Text("您的私人影院", color = Color.White.copy(alpha = 0.28f), fontSize = 15.sp, letterSpacing = 4.sp)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(3.5f).padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEach { item ->
                CinemaCard(
                    item = item,
                    accent = accents.getOrElse(item.cardIndex) { accents.first() },
                    focusRequester = focusFor(item, firstFocus),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CinemaCard(item: HomeItem, accent: Color, focusRequester: FocusRequester?, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .scale(if (focused) 1.05f else 1f)
            .shadow(if (focused) 22.dp else 6.dp, shape, spotColor = accent, ambientColor = Color.Black)
            .clip(shape)
            .background(if (focused) Color(0xFF18181F) else Color(0xFF111116))
            .border(1.dp, if (focused) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f), shape)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) LauncherActivity.lastFocusIndex = item.cardIndex }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .tvCombinedClickable(onClick = item.onClick, onMenu = item.onMenu)
    ) {
        // 頂部彩色高亮條：取得焦點時鋪滿整寬並加高。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(if (focused) 1f else 0.55f)
                .height(if (focused) 4.dp else 3.dp)
                .background(accent)
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(painterResource(item.iconRes), item.title, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Text(item.title, color = if (focused) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.55f),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 背景緩慢呼吸的光暈，僅 Apple TV 主題啟用。 */
@Composable
fun AmbientBreathingOrbs(colors: List<Color>) {
    if (colors.size < 3) return
    val inf = rememberInfiniteTransition(label = "amb")
    val s1 by inf.animateFloat(1f, 1.12f, infiniteRepeatable(tween(9000, easing = EaseInOutSine), RepeatMode.Reverse), label = "s1")
    val a1 by inf.animateFloat(0.05f, 0.09f, infiniteRepeatable(tween(9000, easing = EaseInOutSine), RepeatMode.Reverse), label = "a1")
    val s2 by inf.animateFloat(1f, 1.15f, infiniteRepeatable(tween(11000, easing = EaseInOutSine), RepeatMode.Reverse), label = "s2")
    val a2 by inf.animateFloat(0.04f, 0.08f, infiniteRepeatable(tween(11000, easing = EaseInOutSine), RepeatMode.Reverse), label = "a2")
    val s3 by inf.animateFloat(1f, 1.08f, infiniteRepeatable(tween(13000, easing = EaseInOutSine), RepeatMode.Reverse), label = "s3")
    val a3 by inf.animateFloat(0.05f, 0.09f, infiniteRepeatable(tween(13000, easing = EaseInOutSine), RepeatMode.Reverse), label = "a3")

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(100.dp)
    ) {
        val w = size.width
        val h = size.height
        drawCircle(colors[0], radius = 250f * s1, center = Offset(-80f, -120f), alpha = a1)
        drawCircle(colors[1], radius = 200f * s2, center = Offset(w + 60f, -40f), alpha = a2)
        drawCircle(colors[2], radius = 225f * s3, center = Offset(w * 0.4f, h + 180f), alpha = a3)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThemedCard(
    title: String,
    @DrawableRes iconRes: Int,
    cardIndex: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
    isLarge: Boolean,
    focusRequester: FocusRequester? = null,
) {
    val theme = LocalThemeConfig.current
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val cardScale = if (isFocused) theme.cardFocusScale else 1f

    // 尺寸針對 50 吋電視、3 公尺觀看距離調校，長者也能看清楚。
    val iconSize = if (isLarge) 72.dp else 52.dp
    val iconRadius = if (isLarge) 19.dp else 14.dp
    val titleSize = if (isLarge) 30.sp else 21.sp
    val iconImgSize = if (isLarge) 34.dp else 26.dp

    val cardBg = theme.cardBackgrounds.getOrElse(cardIndex) { theme.cardBackgrounds.first() }

    Box(
        modifier = modifier
            // 取得焦點的卡片浮到最上層，放大後才不會被相鄰卡片裁切。
            .zIndex(if (isFocused) 1f else 0f)
            .scale(cardScale)
            .shadow(
                elevation = if (isFocused) 24.dp else 8.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .clip(shape)
            .background(cardBg)
            .then(
                if (isFocused) Modifier.border(3.dp, theme.cardFocusBorderColor, shape)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) LauncherActivity.lastFocusIndex = cardIndex }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .tvCombinedClickable(onClick = { onClick() }, onMenu = onMenu),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(iconRadius)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    modifier = Modifier.size(iconImgSize)
                )
            }

            Spacer(modifier = Modifier.height(if (isLarge) 16.dp else 11.dp))

            Text(
                text = title,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) theme.textOnCardFocused else theme.textOnCard,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.2).sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StatusBar(context: Context, onNavigate: (Screen) -> Unit = {}) {
    val theme = LocalThemeConfig.current
    val time = remember { mutableStateOf("") }
    val ramPct = remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            time.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            ramPct.value = getMemoryUsagePercent(context)
            delay(10_000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.weight(1f))

        StatusIcon(iconRes = R.drawable.ic_wifi, onClick = {
            try { context.startActivity(Intent(AndroidSettings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        })
        Spacer(modifier = Modifier.width(8.dp))

        // 只有具備藍牙硬體的機型才顯示藍牙圖示。
        // 改用 PackageManager 查詢系統功能，而非已棄用的 BluetoothAdapter.getDefaultAdapter()：
        // 前者不需要任何藍牙權限，對「純本機」的權限清單最乾淨。
        val hasBluetooth = remember(context) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        }
        if (hasBluetooth) {
            StatusIcon(iconRes = R.drawable.ic_bluetooth, onClick = {
                try {
                    context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    try { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                }
            })
            Spacer(modifier = Modifier.width(8.dp))
        }

        StatusIcon(iconRes = R.drawable.ic_network, onClick = {
            try { context.startActivity(Intent(AndroidSettings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {
                try { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }
        })

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(13.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Spacer(modifier = Modifier.width(16.dp))

        // ── 記憶體用量指示 ──
        // 按下時重新讀取目前用量。輕量版刻意「不」提供一鍵清理／加速：
        // Android 的安全模型不允許一般應用程式終止其他應用程式的行程
        // （killBackgroundProcesses 自 API 22 起只對自己的套件有效），
        // 原版是靠 root 執行 am kill-all 才辦得到。提權模組既已整組切除，
        // 這裡就不該再顯示「已清理 N MB」這種做不到的承諾。
        var ramFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (ramFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                .border(
                    1.5.dp,
                    if (ramFocused) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                    RoundedCornerShape(10.dp)
                )
                .onFocusChanged { ramFocused = it.isFocused }
                .focusable()
                .tvClickable { ramPct.value = getMemoryUsagePercent(context) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_clean),
                contentDescription = null,   // 相鄰的文字已說明用途，圖示純裝飾
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "RAM ${ramPct.value}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (ramFocused) Color.White else theme.statusBarTextColor
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        StatusIcon(iconRes = R.drawable.ic_settings, onClick = {
            onNavigate(Screen.Settings)
        })

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(13.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = time.value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = theme.statusBarTextColor,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun StatusIcon(@DrawableRes iconRes: Int, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.5.dp,
                if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .tvClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SplashScreen(theme: ThemeConfig) {
    val inf = rememberInfiniteTransition(label = "splash")
    val pulse by inf.animateFloat(
        0.8f, 1f,
        infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulse)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Flint TV",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "載入中…",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

/** 目前記憶體用量百分比。只讀取系統統計值，不需要任何權限。 */
fun getMemoryUsagePercent(context: Context): Int {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val usedMem = memInfo.totalMem - memInfo.availMem
    return ((usedMem.toFloat() / memInfo.totalMem) * 100).toInt()
}
