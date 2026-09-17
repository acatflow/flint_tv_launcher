package com.flintv.launcher.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * 把火石設為裝置的預設桌面（HOME）。
 *
 * 這件事很關鍵：沒設成預設桌面的話，電視盒重新開機會進到原廠桌面，
 * 使用者得自己找一輪才回得來，按遙控器的首頁鍵也不會回到這裡。
 *
 * ── 輕量版的切除範圍 ──
 * 原版在有 root 的機器上會直接執行 `cmd package set-home-activity` 一鍵設定。
 * 該提權路徑已整段移除，輕量版一律交給**系統內建的桌面選擇器**，
 * 由使用者自己選——這也是一般應用程式唯一能做的事：Android 不允許
 * 任何應用程式擅自把自己設成預設桌面，這道關卡是刻意設計的。
 */
object DefaultLauncher {

    /** 目前的預設桌面是不是本應用程式。 */
    fun isDefault(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == context.packageName
    }

    /**
     * 開啟系統的桌面選擇畫面。
     * 部分電視盒韌體沒有獨立的桌面設定頁，退而求其次開一般系統設定。
     */
    fun openSystemHomeSettings(context: Context) {
        for (action in listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
    }
}
