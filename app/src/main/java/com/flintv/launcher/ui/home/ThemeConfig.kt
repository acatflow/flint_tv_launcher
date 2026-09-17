package com.flintv.launcher.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.flintv.launcher.R

/**
 * 佈景主題識別碼。
 *
 * 這個列舉的字面名稱會以 `name` 寫進偏好設定，等同持久化的鍵值，
 * 更名會讓使用者已選的主題在下次啟動時回到預設值，日後請勿隨意更動。
 */
enum class ThemeId {
    APPLE_TV, LUXURY_HOTEL, INK_WASH, CINEMA
}

/** 每套主題以不同的排版方式呈現首頁的五張卡片。 */
enum class HomeLayout { GRID, HERO_SIDE, RAIL, CINEMA }

data class ThemeConfig(
    val id: ThemeId,
    /**
     * 主題顯示名稱的字串資源 id。
     *
     * 刻意「不」用寫死的字串：主題名稱是使用者在設定頁看得到的文字，
     * 寫死就等於英文語系的使用者也只會看到中文名稱。改走資源 id 之後，
     * 同一份設定在 values / values-en 各自取用正確語言。
     */
    @StringRes val nameRes: Int,
    val layout: HomeLayout,
    val backgroundGradient: Brush,
    val cardBackgrounds: List<Brush>,
    val cardBorderColor: Color,
    val cardFocusBorderColor: Color,
    val cardFocusShadowColor: Color,
    val cardFocusScale: Float,
    val textOnCard: Color,
    val textOnCardFocused: Color,
    val orbColors: List<Color>,
    val orbEnabled: Boolean,
    val statusBarTextColor: Color,
    val accentColor: Color,
    val useCardGradientBg: Boolean,
)

val LocalThemeConfig = compositionLocalOf { ThemePresets.appleTV }

object ThemePresets {

    /** 預設主題：深色底 + 五張滿版漸層卡片，焦點以白色外框與放大呈現。 */
    val appleTV = ThemeConfig(
        id = ThemeId.APPLE_TV,
        nameRes = R.string.theme_apple_tv,
        layout = HomeLayout.GRID,
        backgroundGradient = Brush.linearGradient(listOf(Color(0xFF0D0D14), Color(0xFF161622))),
        cardBackgrounds = listOf(
            Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF3B82F6))),
            Brush.linearGradient(listOf(Color(0xFF5A1A2A), Color(0xFFE11D48))),
            Brush.linearGradient(listOf(Color(0xFF1A4A3A), Color(0xFF059669))),
            Brush.linearGradient(listOf(Color(0xFF4A3A1A), Color(0xFFD97706))),
            Brush.linearGradient(listOf(Color(0xFF2A1A4A), Color(0xFF7C3AED))),
        ),
        cardBorderColor = Color(0x1AFFFFFF),
        cardFocusBorderColor = Color(0x60FFFFFF),
        cardFocusShadowColor = Color(0x30FFFFFF),
        cardFocusScale = 1.06f,
        textOnCard = Color.White,
        textOnCardFocused = Color.White,
        orbColors = listOf(Color(0xFF2563EB), Color(0xFFEC4899), Color(0xFF8B5CF6)),
        orbEnabled = true,
        statusBarTextColor = Color(0xB3FFFFFF),
        accentColor = Color(0xFF0A84FF),
        useCardGradientBg = true,
    )

    /** 豪華飯店：金色主調，一排方形磁貼，焦點以金邊與金色光暈呈現。 */
    val luxuryHotel = ThemeConfig(
        id = ThemeId.LUXURY_HOTEL,
        nameRes = R.string.theme_luxury_hotel,
        layout = HomeLayout.HERO_SIDE,
        backgroundGradient = Brush.linearGradient(listOf(Color(0xFF0D1117), Color(0xFF151B24))),
        cardBackgrounds = listOf(
            Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF2D5A8F))),
            Brush.linearGradient(listOf(Color(0xFF5A1A2A), Color(0xFF8A3040))),
            Brush.linearGradient(listOf(Color(0xFF1A4A3A), Color(0xFF2A6A4A))),
            Brush.linearGradient(listOf(Color(0xFF4A3A1A), Color(0xFF7A6A2A))),
            Brush.linearGradient(listOf(Color(0xFF2A1A4A), Color(0xFF4A3A6A))),
        ),
        cardBorderColor = Color(0x15FFFFFF),
        cardFocusBorderColor = Color(0xAAC9A84C),
        cardFocusShadowColor = Color(0x30C9A84C),
        cardFocusScale = 1.05f,
        textOnCard = Color(0xFFE5E5EA),
        textOnCardFocused = Color(0xFFF5E6C8),
        orbColors = emptyList(),
        orbEnabled = false,
        statusBarTextColor = Color(0x99FFFFFF),
        accentColor = Color(0xFFC9A84C),
        useCardGradientBg = true,
    )

    /** 水墨：不等寬網格配襯線標題，背景帶墨韻暈染，焦點以淡金邊呈現。 */
    val inkWash = ThemeConfig(
        id = ThemeId.INK_WASH,
        nameRes = R.string.theme_ink_wash,
        layout = HomeLayout.RAIL,
        backgroundGradient = Brush.linearGradient(listOf(Color(0xFF0A0A0F), Color(0xFF12121A))),
        cardBackgrounds = listOf(
            Brush.linearGradient(listOf(Color(0xFF0E1E35), Color(0xFF1A3055))),
            Brush.linearGradient(listOf(Color(0xFF3A1515), Color(0xFF6A2525))),
            Brush.linearGradient(listOf(Color(0xFF122A22), Color(0xFF1E4A3A))),
            Brush.linearGradient(listOf(Color(0xFF2A2010), Color(0xFF5A4A25))),
            Brush.linearGradient(listOf(Color(0xFF1A1528), Color(0xFF2E2545))),
        ),
        cardBorderColor = Color(0x12FFFFFF),
        cardFocusBorderColor = Color(0x80B89B5E),
        cardFocusShadowColor = Color(0x25B89B5E),
        cardFocusScale = 1.04f,
        textOnCard = Color(0xFFD4D0C8),
        textOnCardFocused = Color(0xFFF0E8D8),
        orbColors = emptyList(),
        orbEnabled = false,
        statusBarTextColor = Color(0x80FFFFFF),
        accentColor = Color(0xFFB89B5E),
        useCardGradientBg = true,
    )

    /** 電影院：近全黑背景，底部一排暗卡，焦點時整張鋪滿同色高亮。 */
    val cinema = ThemeConfig(
        id = ThemeId.CINEMA,
        nameRes = R.string.theme_cinema,
        layout = HomeLayout.CINEMA,
        backgroundGradient = Brush.linearGradient(listOf(Color(0xFF000000), Color(0xFF0A0A10))),
        cardBackgrounds = listOf(
            Brush.linearGradient(listOf(Color(0xFF101418), Color(0xFF141820))),
            Brush.linearGradient(listOf(Color(0xFF141014), Color(0xFF1A1218))),
            Brush.linearGradient(listOf(Color(0xFF101412), Color(0xFF141A16))),
            Brush.linearGradient(listOf(Color(0xFF141410), Color(0xFF1A1A14))),
            Brush.linearGradient(listOf(Color(0xFF121018), Color(0xFF16141C))),
        ),
        cardBorderColor = Color(0x0AFFFFFF),
        cardFocusBorderColor = Color(0x00FFFFFF),
        cardFocusShadowColor = Color(0x20FFFFFF),
        cardFocusScale = 1.05f,
        textOnCard = Color(0xFFAAAAAA),
        textOnCardFocused = Color.White,
        orbColors = emptyList(),
        orbEnabled = false,
        statusBarTextColor = Color(0x66FFFFFF),
        accentColor = Color(0xFF4A90D9),
        useCardGradientBg = true,
    )

    val all = listOf(appleTV, luxuryHotel, inkWash, cinema)

    fun fromId(id: ThemeId): ThemeConfig = all.first { it.id == id }
    fun fromName(name: String): ThemeConfig = all.firstOrNull { it.id.name == name } ?: appleTV
}
