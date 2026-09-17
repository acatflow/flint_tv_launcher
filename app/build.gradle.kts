plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.flintv.launcher"
    compileSdk = 35

    defaultConfig {
        // 與正式版刻意區隔：兩者可並存於同一台裝置，也避免簽章不同導致的覆蓋安裝失敗。
        // namespace 維持 com.flintv.launcher，故 R 類別與既有程式碼不受影響。
        applicationId = "com.flintv.launcher.lite"
        minSdk = 26          // Android 8.0，涵蓋市面上絕大多數老舊電視盒
        targetSdk = 34
        versionCode = 60
        versionName = "1.1.58"
    }

    // ═══════════════════════════════════════════════════════════════
    //  簽章設定（已整段移除）
    //
    //  輕量版的建置腳本「不」讀取任何金鑰庫或密碼檔。
    //  原本的 signingConfigs 會從 keystore.properties 載入正式簽章金鑰，
    //  對開源倉庫而言，等同把簽章憑據的路徑、別名與使用方式一併公開，
    //  因此連同 Properties 載入邏輯一起物理清除。
    //
    //  要簽出可發佈的 APK，請改用下列任一方式（憑據一律留在倉庫之外）：
    //    1. Android Studio → Build → Generate Signed App Bundle / APK
    //    2. 指令列 apksigner，金鑰庫放在專案目錄以外的位置
    //    3. CI 環境變數注入（例如 GitHub Actions Secrets）
    //
    //  未簽章的 debug 建置不受影響，可直接 ./gradlew assembleDebug。
    // ═══════════════════════════════════════════════════════════════

    buildTypes {
        release {
            // 預設關閉程式碼壓縮，確保任何人 clone 下來都能一次建置成功。
            // 老舊電視盒若要進一步減少安裝檔體積與方法數，可改為 true
            // 並搭配下方的 proguard-rules.pro（開啟後請務必完整回歸測試一輪）。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // AGP 內建的 lint 偵測器在本專案的 Compose 版本組合下有已知當機問題
    // （與專案程式碼無關），因此不讓 release 建置阻斷於 lint。
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeTvVersion = "1.0.0-beta01"

    // ── AndroidX 基礎 ──
    implementation("androidx.core:core-ktx:1.13.1")
    // themes.xml 的 Theme.FlintTV 繼承自 Theme.AppCompat.NoActionBar，
    // 因此即使 Kotlin 程式碼未直接 import，這個相依仍是資源解析所必需，不可移除。
    implementation("androidx.appcompat:appcompat:1.7.0")
    // 由 activity-compose 遞移引入，此處僅做版本鎖定，不增加安裝檔體積。
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // ── Compose for TV ──
    // 全專案僅使用 androidx.tv.material3，未出現任何 androidx.tv.foundation 的
    // 匯入，故不再顯式宣告 tv-foundation，改由 tv-material 依需要遞移引入。
    implementation("androidx.tv:tv-material:$composeTvVersion")

    // ── Compose 核心 ──
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // ── 畫面導覽 ──
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── 本機偏好設定儲存（我的最愛、首頁卡片綁定）──
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── 協程 ──
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
