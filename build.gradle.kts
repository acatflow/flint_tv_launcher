// 專案層級建置腳本。
// 此處只宣告外掛程式（Plugin）版本，實際設定一律下放到 :app 模組。
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}
