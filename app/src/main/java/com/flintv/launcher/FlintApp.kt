package com.flintv.launcher

import android.app.Application

/**
 * 應用程式進入點。
 *
 * 輕量版不含任何常駐服務、網路連線或螢幕推流模組，因此這裡保持為空實作。
 * 原版在此轉發 onTrimMemory 給推流模組以便在記憶體吃緊時主動降級；
 * 該模組已整個移除，記憶體回收交還給系統與 Compose 自行處理即可。
 *
 * 保留這個類別而非直接刪除，是為了日後需要「行程層級」初始化時有現成的
 * 掛載點（例如預先載入使用者選擇的佈景主題、註冊全域例外處理器）。
 */
class FlintApp : Application()
