package com.flintv.launcher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * 電視可點擊元素的**唯一**封裝。
 *
 * 根因：在電視盒上，Compose 的 `clickable` / `combinedClickable`
 * **不會因為遙控器的 OK 鍵（DPAD_CENTER / Enter）觸發 onClick**，
 * 必須顯式用 `onKeyEvent` 接管。專案早期散落著多份各自為政的封裝，
 * 每新增一個可點元素就極容易漏接（「全部應用」按 OK 打不開應用程式就是這樣來的）。
 * 因此收斂成單一入口，全專案一律使用這兩個修飾詞，不要再裸用 clickable
 * 或自己寫 onKeyEvent。
 *
 * ⚠️ 使用前提：元素必須先 `.focusable()`，否則收不到按鍵事件。
 *    onKeyEvent 要放在 clickable 之前。
 */

/** 一般可點擊。焦點停在其上時，遙控器 OK 鍵或指標點擊都會觸發 [onClick]。 */
fun Modifier.tvClickable(onClick: () -> Unit): Modifier = this
    .onKeyEvent { e ->
        if (e.type == KeyEventType.KeyUp && (e.key == Key.Enter || e.key == Key.DirectionCenter)) {
            onClick(); true
        } else false
    }
    .clickable { onClick() }

/**
 * 可點擊 + 選用的選單鍵。用於「啟動 + 二級操作」兩用的卡片。
 *  - 短按 OK（DPAD_CENTER / Enter）→ [onClick]（啟動或進入）
 *  - 長按 OK 或按下選單鍵 → [onMenu]（叫出該項目的操作選單：換綁、加入我的最愛等）
 *
 * 長按的處理方式：在 KeyDown 的 isLongPress 觸發 [onMenu]，並用一個旗標吞掉隨後的
 * KeyUp，避免「叫出選單」與「啟動應用程式」同時發生。
 *
 * ⚠️ 刻意不使用 combinedClickable：在電視盒上它會對按鍵長按 OK 觸發 onLongClick，
 *    疊加我們自己的 onClick 就變成雙重觸發（實測長按「看電視」會先跳出綁定頁、
 *    接著又啟動應用程式）。所以只用 clickable，長按邏輯自己在 onKeyEvent 裡管，行為可控。
 */
fun Modifier.tvCombinedClickable(
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
): Modifier = composed {
    // 本次 OK 按壓是否已被當成「長按」處理：長按在 KeyDown（isLongPress）觸發 onMenu，
    // 隨後的 KeyUp 只負責清旗標、不再觸發 onClick。
    var longHandled by remember { mutableStateOf(false) }
    this
        .onKeyEvent { e ->
            val isOk = e.key == Key.Enter || e.key == Key.DirectionCenter
            when {
                e.type == KeyEventType.KeyDown && isOk && e.nativeKeyEvent.isLongPress && onMenu != null -> {
                    longHandled = true; onMenu(); true
                }
                e.type == KeyEventType.KeyUp && isOk -> {
                    if (longHandled) longHandled = false else onClick()
                    true
                }
                e.type == KeyEventType.KeyUp && onMenu != null && (e.key == Key.Menu || e.key == Key(AndroidKeyEvent.KEYCODE_SETTINGS)) -> {
                    onMenu(); true
                }
                else -> false
            }
        }
        .clickable { onClick() }
}
