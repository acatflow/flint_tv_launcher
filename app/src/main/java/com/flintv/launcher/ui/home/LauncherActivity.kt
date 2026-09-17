package com.flintv.launcher.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.flintv.launcher.navigation.FlintNavHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 啟動器主畫面。
 *
 * ── 輕量版的切除範圍 ──
 * 原版的 onCreate 會依序拉起七個模組：雲端用戶端、內網遙控 HTTP 伺服器、
 * 螢幕推流、提權後端偵測、按鍵注入、遠端授權裁決、保活看門狗與 ADB 自動開啟，
 * 另外還會透過 NSD 對區域網路廣播自己的服務位址。
 * 以上全部屬於遠端協助鏈路，已整組移除。本 Activity 現在只做三件事：
 *   1. 掛上 Compose 畫面
 *   2. 處理遙控器的首頁鍵
 *   3. 攔住返回鍵，避免使用者退出桌面後只看到一片黑畫面
 *
 * UI 層完全未動刀：companion object 只留下首頁鍵事件、開場動畫旗標與焦點記憶，
 * 三者都是純本機的畫面狀態，簽章維持原樣。
 */
class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "啟動器主畫面建立")

        setContent {
            FlintTheme {
                FlintNavHost()
            }
        }
    }

    /**
     * 使用者在任一子頁面按下遙控器的首頁鍵時，系統會以 ACTION_MAIN + CATEGORY_HOME
     * 把 Intent 重新派送給這個已存在的實例（啟動模式為 singleTask）。
     * 這裡發出事件，由 FlintNavHost 與 HomeScreen 收下後退回首頁並還原焦點。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            Log.i(TAG, "偵測到首頁鍵，返回首頁")
            CoroutineScope(Dispatchers.Main).launch { _homeEvent.emit(Unit) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 還有子頁面可退時才交給 NavHost 處理；已經在首頁時刻意不做任何事，
        // 否則使用者會退出桌面、電視只剩一片黑畫面。
        if (onBackPressedDispatcher.hasEnabledCallbacks()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    companion object {
        private const val TAG = "FLINT"

        // ── 首頁鍵事件 ──
        // replay 設為 1：避免 popBackStack 與事件發送之間的競態導致事件遺失。
        private val _homeEvent = MutableSharedFlow<Unit>(replay = 1)
        val homeEvent = _homeEvent.asSharedFlow()

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        fun clearHomeEvent() { _homeEvent.resetReplayCache() }

        /** 開場動畫每個行程只播一次：按首頁鍵讓系統重建 Activity 時不重播。 */
        var splashShownThisProcess = false

        /** 首頁焦點記憶：從子頁面返回時回到離開時那張卡片，而非永遠跳回第一張。 */
        var lastFocusIndex = 0
    }
}
