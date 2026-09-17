package com.flintv.launcher.ui.home

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme

/** 全域共用的色票。各佈景主題自帶的顏色定義在 ThemePresets，此處只放跨主題共用的值。 */
object FlintColors {
    val background = Color(0xFF1B1B20)
    val backgroundEnd = Color(0xFF252530)
    val backgroundGradient: Brush = Brush.linearGradient(
        colors = listOf(background, backgroundEnd)
    )
    val card = Color(0xFFF5F5F7)
    val cardHover = Color.White
    val text = Color(0xFF1D1D1F)
    val textSecondary = Color(0xFF86868B)
    val textTertiary = Color(0xFFAEAEB2)
    val accent = Color(0xFF0A84FF)
    val accentBg = Color(0x140A84FF)
    val green = Color(0xFF30D158)
    val blue = Color(0xFF2563EB)
    val red = Color(0xFFE11D48)
    val purple = Color(0xFF7C3AED)
    val amber = Color(0xFFD97706)
    val indigo = Color(0xFF6366F1)
    val teal = Color(0xFF059669)
    val textOnBg = Color(0xFFE5E5EA)
    val barBg = Color.Transparent
    val barText = Color(0xB3FFFFFF)
    val separator = Color(0x1AFFFFFF)

    val gradBlue = Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB)))
    val gradRose = Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48)))
    val gradEmerald = Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669)))
    val gradAmber = Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706)))
    val gradViolet = Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED)))
}

private const val PREFS = "flint_prefs"
private const val KEY_THEME = "selected_theme"

/** 讀取使用者選擇的主題；偏好設定損毀或是舊識別碼時，安全退回預設主題。 */
fun loadThemeId(context: Context): ThemeId {
    val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_THEME, ThemeId.APPLE_TV.name) ?: ThemeId.APPLE_TV.name
    return try { ThemeId.valueOf(name) } catch (_: Exception) { ThemeId.APPLE_TV }
}

// 全域可觀察狀態，而非 remember：切換主題時整棵畫面立即重繪，不需要重建 Activity。
private val _currentThemeId = mutableStateOf(ThemeId.APPLE_TV)

fun saveThemeId(context: Context, id: ThemeId) {
    Log.i("FLINT_THEME", "儲存主題：$id，原為 ${_currentThemeId.value}")
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY_THEME, id.name).apply()
    _currentThemeId.value = id
    Log.i("FLINT_THEME", "主題已儲存，目前狀態為 ${_currentThemeId.value}")
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FlintTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val savedId = loadThemeId(context)
    if (_currentThemeId.value != savedId) {
        _currentThemeId.value = savedId
    }
    val currentId = _currentThemeId.value
    Log.i("FLINT_THEME", "套用主題：$currentId")
    val themeConfig = ThemePresets.fromId(currentId)

    CompositionLocalProvider(LocalThemeConfig provides themeConfig) {
        MaterialTheme {
            content()
        }
    }
}
