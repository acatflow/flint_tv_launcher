package com.flintv.launcher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/**
 * 進入畫面時自動把焦點送到第一個可互動的元素。
 *
 * 電視沒有觸控，使用者只有方向鍵可用；若進頁面後沒有任何元素持有焦點，
 * 遙控器按下去會完全沒有反應，看起來就像當機。所有新頁面都應該在最外層
 * 的元素掛上這個 FocusRequester。
 */
@Composable
fun rememberAutoFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    return fr
}
