# 技術研究：工務單照片存到雲端儲存

**Plan Package**: `specs/plans/009-repair-photo-storage`

本檔回答三個技術問題：後端用哪個程式庫接雲端儲存、本機與測試怎麼模擬雲端儲存、App 用什麼拍照與壓縮。每題先寫結論，再寫查證過的事實與還沒查證的風險。產品面的決定見 `spec.md`「為什麼這樣定？」，程式落點見 `plan.md`。

## 後端用哪個程式庫接雲端儲存？

**結論**：Google 官方的 Java 程式庫 `com.google.cloud:google-cloud-storage`，在 `tms/pom.xml` 明確宣告，版本鎖成跟 `firebase-admin` 帶進來的同一版。

查證過的事實：

- 分支起點 `7420c55` 上，`firebase-admin` 9.9.0 已經間接帶進 `com.google.cloud:google-cloud-storage` 2.64.0（2026-11-26 以 `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./tms/mvnw -f tms/pom.xml -q dependency:tree -Dincludes=com.google.cloud:google-cloud-storage` 實測）。
- 程式不能只靠間接依賴：哪天 `firebase-admin` 升版或拿掉這個依賴，存照片的程式會在編譯期才發現少了程式庫。明確宣告，版本跟間接帶進來的一樣，避免同一個程式庫兩個版本。

連線設定（實作時照這三項寫）：

| 設定 | 本機與測試 | 正式環境 |
| --- | --- | --- |
| 儲存空間名稱 | 本機 `tms-repair-photos-local`；測試每份 Spring 設定各用自己的名稱 | 維運開好的儲存空間 |
| 模擬服務位址 | `http://localhost:4443`（測試用 Testcontainers 給的位址） | 留空 |
| 登入憑證 | 不需要（程式庫的無憑證模式） | 伺服器本身的身分（程式庫預設的 Application Default Credentials） |

模擬服務位址有值時，程式庫用 `StorageOptions.newBuilder().setHost(位址).setProjectId(任意值).setCredentials(NoCredentials.getInstance())`；留空時用 `StorageOptions.getDefaultInstance()`，憑證與專案從執行環境取得。這是唯一一處看模擬服務位址的分支，存取照片的程式沒有分支。

## 本機與自動化測試怎麼模擬雲端儲存？

**結論**：開源的 `fake-gcs-server`（Docker 映像 `fsouza/fake-gcs-server`）。本機加進根目錄的 `docker-compose.yml`；後端測試在 `SharedContainers` 用 Testcontainers 的 `GenericContainer` 起一個，整個 JVM 共用。

理由：

- 它說的是雲端儲存的 JSON API，官方程式庫把 `host` 指過去就能用，存照片的程式不需要知道自己連的是模擬服務。
- 後端已有 Testcontainers（PostgreSQL、Kafka）與共用容器的寫法（`tms/src/test/java/com/athena/tms/SharedContainers.java`），照同一套加第三個容器。
- Testcontainers 沒有官方的 fake-gcs-server 模組，用 `GenericContainer` 即可，不新增依賴。

要注意的設定（實作時逐項確認）：

- 啟動參數 `-scheme http`，不用 HTTPS，程式庫的 `setHost` 用 `http://`。
- 啟動參數 `-port 4443` 與 `-public-host`／`-external-url` 設成程式庫實際連的位址；分段上傳（resumable upload）會用到回傳的網址，位址不對會失敗。照片最大 5 MB，程式用一次送完的 `storage.create(BlobInfo, byte[])`，不走分段上傳，但仍照設。
- 映像版本鎖一個確切的 tag，跟 compose 與 Testcontainers 用同一個；實作時以 `docker pull` 確認存在後寫入，不用 `latest`。
- 本機資料放 named volume，`docker compose down` 保留照片，`down -v` 清掉，跟 Postgres 一樣。

儲存空間誰建立：

- 模擬服務裡，後端啟動時若設定了模擬服務位址，而儲存空間不存在，就建立它。這段只在模擬服務位址有值時執行。
- 正式環境的儲存空間由維運事先開好，後端不建立：正式環境的伺服器身分不該有建立儲存空間的權限。
- 不用 compose 的初始化腳本建立，因為測試用的 Testcontainers 也要建立，兩邊各寫一次反而會分岔；交給後端啟動時做，本機與測試走同一段程式。

多套後端共用一個模擬服務：

- 本機的手動測試環境（後端 8083）與驗收環境（8080、8081）可以共用同一個 `fake-gcs-server`，各自用不同的儲存空間名稱，以環境變數覆寫，跟它們各用不同資料庫的做法相同。
- 自動化測試裡每份 Spring 設定（`BddSpringConfiguration`、`integration/stepdefs/CucumberSpringConfiguration`、`relaywake/RelayWakeSpringConfiguration`、`PostgresSpringTest`）各用自己的儲存空間名稱，理由跟它們各用自己的資料庫一樣：先跑完的套件留在快取裡的 context 不能清到下一個套件的資料。

## App 用什麼拍照、選照片與壓縮？

**結論**：只用 `image_picker`。拍照用 `ImageSource.camera`、選照片用 `ImageSource.gallery`，縮放與壓縮用 `pickImage` 本身的 `maxWidth: 1600`、`maxHeight: 1600`、`imageQuality: 80`。不加 `camera` 與 `flutter_image_compress`。

理由：

- 員工只需要「打開系統相機拍一張」與「從系統相簿挑一張」，不需要 App 內嵌取景畫面，`camera` 套件用不到。
- `image_picker` 的縮放與品質參數已能把照片縮到長邊 1600 像素、壓成 JPEG，`techstack.md` 原本列的 `flutter_image_compress` 是為了浮水印畫進照片時重新編碼；浮水印這版不畫進照片，就沒有理由多一個原生套件。

權限：

- iOS 在 `app/mobile/ios/Runner/Info.plist` 加 `NSCameraUsageDescription` 與 `NSPhotoLibraryUsageDescription`，文字照 `spec.md`「App 怎麼拍照、選照片、顯示照片？」。缺了這兩項，iOS 一打開相機或相簿 App 就會被系統結束。
- Android 不加 `CAMERA` 權限。`image_picker` 交給系統的相機 App 與系統的照片挑選器，不需要 App 自己有權限；反而在 `AndroidManifest.xml` 宣告了 `CAMERA`，系統會要求 App 先拿到權限才能叫相機。

測試版：

- `--dart-define=TEST_BACKDOOR=true` 的測試版（Appium 在模擬器上跑，模擬器沒有相機）仍回傳既有的 1 × 1 測試照片，不呼叫 `image_picker`。判斷寫在 `app/mobile/lib/core/repair_photo_capture.dart` 一處，跟 `test_backdoor.dart` 用同一個開關。
- Flutter 單元與畫面測試以注入的拍照函式代替 `image_picker`，不碰真的平台通道。

還沒查證的風險：

- **相簿裡的 HEIC 或 PNG 會不會被轉成 JPEG**：`image_picker` 在 iOS 有縮放或品質參數時通常會重新編碼，但不同系統版本與原始格式（例如 PNG 截圖、HEIC）的結果要在真機上確認。App 上傳前檢查開頭三個位元組，不是 JPEG 就照 spec 提示「這張照片不能用」，不會送出後才被後端拒絕。若真機驗證發現常見的相簿照片都轉不成 JPEG，才加 `flutter_image_compress` 做轉檔，屆時另記一列 `/technical-research` 改動。
- **拍攝時間**：從相簿選的照片用 `XFile.lastModified()` 當拍攝時間；這是檔案修改時間，不一定等於快門時間。拿 EXIF 的拍攝時間要多一個套件，這版不做。

## App 怎麼顯示需要登入才拿得到的照片？

**結論**：在 `ApiClient` 加一個讀照片內容的方法，沿用既有的登入標頭與錯誤處理；畫面用一個小元件把讀到的位元組包成 `MemoryImage`，同一次打開 App 期間依照片編號放在記憶體快取裡。不加 `cached_network_image` 之類的套件。

理由：

- `Image.network` 可以帶標頭，但登入逾時、401 換頁、測試裡替換 `ApiClient.instance` 這些既有行為都在 `ApiClient`，繞過它就要再做一次。
- 一張工務單最多幾張照片，每張幾百 KB，記憶體快取足夠；照片附上之後內容不再改，以照片編號當快取鍵不會讀到舊內容。
