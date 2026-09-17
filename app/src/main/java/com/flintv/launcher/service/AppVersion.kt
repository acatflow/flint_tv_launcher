package com.flintv.launcher.service

import android.content.Context
import android.os.Build
import com.flintv.launcher.BuildConfig

/**
 * 權威版本號 = **已安裝套件**的版本（PackageManager），而不是 `BuildConfig`
 * （後者是目前執行中那份程式碼的版本）。
 *
 * 兩者在剛升級完的短暫時間內會不一致：套件已經換新，但行程還是舊的那一份，
 * 這時用 BuildConfig 會讓「關於」顯示舊版本。改讀 PackageManager 就一律以
 * 裝置上真正裝著的版本為準。PM 讀取失敗時才退回 BuildConfig。
 */
object AppVersion {
    fun code(context: Context): Int = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode.toInt()
        else @Suppress("DEPRECATION") pi.versionCode
    } catch (e: Exception) {
        BuildConfig.VERSION_CODE
    }

    fun name(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME
    } catch (e: Exception) {
        BuildConfig.VERSION_NAME
    }
}
