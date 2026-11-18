# Firebase 設定檔放哪

從 Firebase 主控台下載 iOS App（bundle id `com.athena.tms.tmsStaffApp`）的
`GoogleService-Info.plist`，放在這個目錄（`ios/Runner/GoogleService-Info.plist`），
並在 Xcode 把它加進 Runner target 的 Copy Bundle Resources。
檔案不進版控；欄位形狀見同目錄的 `GoogleService-Info.plist.example`。
沒放這個檔案 App 照常能跑，只是推播停用（Dart log 會印「Firebase 初始化失敗，推播停用」）。
