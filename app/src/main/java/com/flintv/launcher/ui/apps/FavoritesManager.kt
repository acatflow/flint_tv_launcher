package com.flintv.launcher.ui.apps

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesStore by preferencesDataStore("favorites")
private val KEY_PACKAGES = stringSetPreferencesKey("fav_packages")

/**
 * 「我的收藏」的本機儲存，以 DataStore 存一組套件名稱。
 * 全部留在裝置上，不上傳也不同步。
 */
object FavoritesManager {

    /** 收藏清單的資料流；有異動時畫面會自動重繪。 */
    fun getPackages(context: Context): Flow<Set<String>> {
        return context.favoritesStore.data.map { it[KEY_PACKAGES] ?: emptySet() }
    }

    suspend fun add(context: Context, packageName: String) {
        context.favoritesStore.edit { prefs ->
            val current = prefs[KEY_PACKAGES] ?: emptySet()
            prefs[KEY_PACKAGES] = current + packageName
        }
    }

    suspend fun remove(context: Context, packageName: String) {
        context.favoritesStore.edit { prefs ->
            val current = prefs[KEY_PACKAGES] ?: emptySet()
            prefs[KEY_PACKAGES] = current - packageName
        }
    }
}
