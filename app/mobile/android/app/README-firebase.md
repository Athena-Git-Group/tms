# Firebase 設定檔放哪

從 Firebase 主控台下載 Android App（package `com.athena.tms.tms_staff_app`）的
`google-services.json`，放在這個目錄（`android/app/google-services.json`）。
檔案不進版控；欄位形狀見同目錄的 `google-services.json.example`。
`build.gradle.kts` 只在檔案存在時套 google-services 外掛，沒放檔案 App 照常建置，只是推播停用。
