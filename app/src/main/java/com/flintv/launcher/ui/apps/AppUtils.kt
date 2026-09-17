package com.flintv.launcher.ui.apps

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** 已安裝應用程式的列舉與啟動。全部走標準 PackageManager，不需要任何特殊權限。 */
object AppUtils {

    /**
     * 列出所有「可啟動」的第三方應用程式。
     *
     * 電視盒的情況比手機複雜，要同時收集三種進入點：
     *  - LAUNCHER：一般手機應用程式
     *  - LEANBACK_LAUNCHER：Android TV 專用應用程式
     *  - HOME：不少 IPTV 應用程式把自己註冊成桌面，只查前兩種會漏掉
     *
     * ⚠️ 這個函式會掃描全部套件並載入每個圖示，在弱效能的電視盒上很重。
     *    呼叫端務必放在背景執行緒（produceState + Dispatchers.IO），
     *    直接在組合區塊呼叫會把 UI 執行緒堵死、畫面凍結。
     */
    fun getThirdPartyApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val launchablePackages = mutableSetOf<String>()

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcherIntent, 0).forEach {
            launchablePackages.add(it.activityInfo.packageName)
        }

        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        pm.queryIntentActivities(leanbackIntent, 0).forEach {
            launchablePackages.add(it.activityInfo.packageName)
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(homeIntent, 0).forEach {
            if (it.activityInfo.packageName != "android") {
                launchablePackages.add(it.activityInfo.packageName)
            }
        }

        launchablePackages.remove(context.packageName)

        return launchablePackages.mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                // 圖示在這裡（背景執行緒）就先轉成點陣圖，讓格線組合時完全不做點陣圖運算。
                // 只保留轉好的 ImageBitmap，不留原始 Drawable：Drawable 不會被任何畫面讀取，
                // 留著等於替每個應用程式白佔一份記憶體，弱效能電視盒上很吃虧。
                val bmp = try {
                    pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
                } catch (_: Exception) { null }
                AppInfo(pm.getApplicationLabel(ai).toString(), pkg, bmp)
            } catch (_: Exception) { null }
        }.sortedBy { it.name.lowercase() }
    }

    fun getAppName(context: Context, packageName: String): String? {
        return try {
            val ai = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { null }
    }

    /**
     * 取得啟動指定套件的 Intent，依序嘗試三種進入點。
     * 回傳 null 代表該套件已被移除或沒有任何可啟動的進入點，呼叫端應據此清掉綁定。
     */
    fun getLaunchIntent(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        pm.getLaunchIntentForPackage(packageName)?.let { return it }

        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(packageName), 0
        )
        if (leanback.isNotEmpty()) {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                setClassName(packageName, leanback[0].activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        val home = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName), 0
        )
        if (home.isNotEmpty()) {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                setClassName(packageName, home[0].activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return null
    }
}
