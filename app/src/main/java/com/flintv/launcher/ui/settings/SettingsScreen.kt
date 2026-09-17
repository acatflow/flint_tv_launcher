package com.flintv.launcher.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.flintv.launcher.R
import com.flintv.launcher.service.AppVersion
import com.flintv.launcher.service.DefaultLauncher
import com.flintv.launcher.ui.apps.AppUtils
import com.flintv.launcher.ui.apps.getBoundApp
import com.flintv.launcher.ui.home.FlintColors
import com.flintv.launcher.ui.home.LocalThemeConfig
import com.flintv.launcher.ui.home.ThemeId
import com.flintv.launcher.ui.home.ThemePresets
import com.flintv.launcher.ui.home.loadThemeId
import com.flintv.launcher.ui.home.rememberAutoFocus
import com.flintv.launcher.ui.home.saveThemeId
import com.flintv.launcher.ui.tvClickable
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onBindApp: (String) -> Unit = {}) {
    val context = LocalContext.current
    val theme = LocalThemeConfig.current
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    var showLangPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    val currentLang = getCurrentLanguageLabel()
    val firstRowFocus = remember { FocusRequester() }

    // 每次回到設定清單都重新查一次預設桌面狀態：使用者可能剛從系統的
    // 桌面選擇畫面回來，那邊的變更我們收不到通知。
    var isDefaultHome by remember { mutableStateOf(false) }
    LaunchedEffect(showLangPicker, showThemePicker) {
        if (!showLangPicker && !showThemePicker) {
            isDefaultHome = DefaultLauncher.isDefault(context)
            try { firstRowFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundGradient)
            .padding(24.dp)
    ) {
        Text(
            text = "< ${stringResource(R.string.settings)}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = theme.statusBarTextColor,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when {
            showLangPicker -> LanguagePicker(
                onSelect = { locale ->
                    setAppLocale(context, locale)
                    showLangPicker = false
                },
                onBack = { showLangPicker = false }
            )
            showThemePicker -> ThemePicker(onBack = { showThemePicker = false })
            else -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── 設為預設桌面 ──
                // 輕量版一律交給系統的桌面選擇畫面。原版在有 root 的機器上會直接
                // 執行 cmd package set-home-activity 一鍵設定，該提權路徑已整段切除；
                // 一般應用程式本來就不被允許擅自把自己設成預設桌面。
                SettingsRow(
                    label = stringResource(R.string.set_default_launcher),
                    value = if (isDefaultHome) stringResource(R.string.default_launcher_set)
                            else stringResource(R.string.default_launcher_not_set),
                    focusRequester = firstRowFocus,
                    onClick = {
                        if (!isDefaultHome) DefaultLauncher.openSystemHomeSettings(context)
                    }
                )
                SettingsRow(
                    label = stringResource(R.string.language),
                    value = currentLang,
                    onClick = { showLangPicker = true }
                )
                SettingsRow(
                    label = stringResource(R.string.theme_style),
                    value = stringResource(ThemePresets.fromId(loadThemeId(context)).nameRes),
                    onClick = { showThemePicker = true }
                )
                val tvBound = getBoundApp(context, "tv")
                SettingsRow(
                    label = stringResource(R.string.bind_tv_label),
                    value = tvBound?.let { AppUtils.getAppName(context, it) }
                        ?: stringResource(R.string.bind_not_set),
                    onClick = { onBindApp("tv") }
                )
                val showsBound = getBoundApp(context, "shows")
                SettingsRow(
                    label = stringResource(R.string.bind_shows_label),
                    value = showsBound?.let { AppUtils.getAppName(context, it) }
                        ?: stringResource(R.string.bind_not_set),
                    onClick = { onBindApp("shows") }
                )
                SettingsRow(
                    label = stringResource(R.string.about),
                    // 讀已安裝套件的版本，而非 BuildConfig（詳見 AppVersion 的說明）
                    value = "v${AppVersion.name(context)}",
                    onClick = { }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Android ID: $androidId",
                    fontSize = 13.sp,
                    color = FlintColors.textOnBg.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ThemePicker(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(loadThemeId(context)) }
    // 防連點：遙控器 OK 鍵連發會在 500 毫秒內重複寫入偏好設定
    var lastClickTime by remember { mutableStateOf(0L) }
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { try { firstItemFocus.requestFocus() } catch (_: Exception) {} }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var backFocused by remember { mutableStateOf(false) }
        Text(
            text = "< ${stringResource(R.string.theme_style)}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (backFocused) FlintColors.accent else FlintColors.textOnBg,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .onFocusChanged { backFocused = it.isFocused }
                .focusable()
                .tvClickable { onBack() }
        )

        ThemePresets.all.forEachIndexed { index, preset ->
            val isSelected = preset.id == selectedId
            var isFocused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(14.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(if (isFocused) 8.dp else 2.dp, shape)
                    .clip(shape)
                    .background(preset.cardBackgrounds.first(), shape)
                    .then(
                        if (isFocused) Modifier.border(2.dp, preset.accentColor, shape)
                        else if (isSelected) Modifier.border(2.dp, preset.accentColor.copy(alpha = 0.6f), shape)
                        else Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), shape)
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                    .focusable()
                    .tvClickable {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime > 500) {
                            lastClickTime = now
                            selectedId = preset.id
                            saveThemeId(context, preset.id)
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 三個小色點預覽該主題的卡片配色
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        )
                    }
                }

                Text(
                    text = stringResource(preset.nameRes),
                    fontSize = 18.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Text(
                        text = stringResource(R.string.theme_current),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = preset.accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = preset.accentColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        var restoreFocused by remember { mutableStateOf(false) }
        val restoreShape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .shadow(if (restoreFocused) 6.dp else 1.dp, restoreShape)
                .background(
                    if (restoreFocused) FlintColors.accent else Color.White.copy(alpha = 0.08f),
                    restoreShape
                )
                .border(
                    1.dp,
                    if (restoreFocused) FlintColors.accent else Color.White.copy(alpha = 0.1f),
                    restoreShape
                )
                .onFocusChanged { restoreFocused = it.isFocused }
                .focusable()
                .tvClickable {
                    selectedId = ThemeId.APPLE_TV
                    saveThemeId(context, ThemeId.APPLE_TV)
                }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.theme_restore_default),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (restoreFocused) Color.White else FlintColors.textOnBg.copy(alpha = 0.6f)
            )
        }
    }
}

data class LangOption(val label: String, val locale: String)

/** 只提供台灣繁體與英文兩種語系，系統設為其他中文變體時一律回落台灣繁體。 */
val LANGUAGES = listOf(
    LangOption("繁體中文", "zh-TW"),
    LangOption("English", "en")
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LanguagePicker(onSelect: (String) -> Unit, onBack: () -> Unit) {
    val current = Locale.getDefault().toLanguageTag()
    val autoFocus = rememberAutoFocus()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LANGUAGES.forEachIndexed { index, lang ->
            val isSelected = current.startsWith(lang.locale.substringBefore("-"))
            var isFocused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(12.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(if (isFocused) 8.dp else 2.dp, shape)
                    .background(FlintColors.card, shape)
                    .then(if (isFocused) Modifier.border(2.dp, FlintColors.accent, shape) else Modifier)
                    .onFocusChanged { isFocused = it.isFocused }
                    .then(if (index == 0) Modifier.focusRequester(autoFocus) else Modifier)
                    .focusable()
                    .tvClickable { onSelect(lang.locale) }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lang.label,
                    fontSize = 18.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isFocused) FlintColors.accent else FlintColors.text,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FlintColors.accent)
                }
            }
        }
    }
}

fun getCurrentLanguageLabel(): String {
    val tag = Locale.getDefault().toLanguageTag()
    return when {
        tag.startsWith("zh") -> "繁體中文"
        tag.startsWith("en") -> "English"
        else -> Locale.getDefault().displayLanguage
    }
}

fun setAppLocale(context: Context, localeTag: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val lm = context.getSystemService(LocaleManager::class.java)
        lm?.applicationLocales = LocaleList.forLanguageTags(localeTag)
    } else {
        // Android 13 以前沒有 per-app locale，只能改設定再重建 Activity
        val locale = Locale.forLanguageTag(localeTag)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        (context as? android.app.Activity)?.recreate()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsRow(label: String, value: String, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isFocused) 8.dp else 2.dp, shape)
            .background(FlintColors.card, shape)
            .then(if (isFocused) Modifier.border(2.dp, FlintColors.accent, shape) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .tvClickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) FlintColors.accent else FlintColors.text,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(text = value, fontSize = 15.sp, color = FlintColors.textTertiary)
        }
        Text(text = " ›", fontSize = 18.sp, color = FlintColors.textTertiary)
    }
}
