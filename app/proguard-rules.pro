# 火石啟動器輕量版 —— R8 / ProGuard 規則
#
# 本專案未使用反射、序列化或 JNI，AndroidX 與 Compose 各套件也都隨套件
# 提供自己的 consumer rules，因此預設規則即足夠，此處刻意保持精簡。
#
# 日後若加入下列任一項，再回來補對應的 -keep 規則：
#   * 以反射存取的類別或方法
#   * JSON 序列化的資料類別（Gson / Moshi / kotlinx.serialization）
#   * 由 XML 佈局或 Manifest 以字串名稱參照的自訂 View / 元件

# 保留行號資訊，讓正式版的當機堆疊仍可還原
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
