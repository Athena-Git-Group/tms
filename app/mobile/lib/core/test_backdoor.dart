import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'api_client.dart';
import 'connection_status.dart';

/// App 跑在自動化測試底下時，讓測試腳本能從外部操控裝置狀態（例如把裝置時間
/// 改到某個時間點、強迫斷網、讓某一次照片上傳失敗），好驗證離線、逾時、重試
/// 這類難以用真實環境重現的情境。
///
/// 一般建置完全不含這個機制：只有編譯時加上
/// `--dart-define=TEST_BACKDOOR=true`，[TestBackdoor.maybeStart] 才會啟動對應
/// 的伺服器；沒加這個旗標時連伺服器物件都不會建立，production 建置不會多開
/// 一個 port。
///
/// 這個後門只負責「裝置側注入」：時間、離線、上傳失敗、記住帳號、連線不穩
/// 五個開關，以及離線佇列的起始筆數。畫面資料一律走 `core/api_client.dart`
/// 向真後端取——測試腳本要準備資料，走後端的測試資料端點，不再從這裡灌
/// fixture 進畫面。
///
/// 離線與連線不穩兩個開關不存在這裡：它們寫進 [ConnectionStatus]，也就是全
/// App 唯一的連線狀態來源。注入端與讀取端分開，畫面才不必為了問「現在是不是
/// 離線」而認識測試後門，將來換成真實的網路偵測也只是換掉寫入的那一端。
/// 其餘開關存在 [TestHooks] 這個 singleton 裡；其中裝置時間與上傳失敗還沒有
/// 畫面在讀，等對應功能開發到需要被測試控制時再接上。
class TestHooks {
  TestHooks._();

  static final TestHooks instance = TestHooks._();

  /// 測試腳本要求裝置採用的時間（ISO 格式字串）；App 尚未讀取這個值。
  String? injectedClockIso;

  /// 測試腳本要求第幾張照片（從 1 算起）上傳失敗；App 尚未讀取這個值。
  int? uploadFailAtIndex;

  /// 「這支手機記住的帳號」。登入畫面監聽它決定顯示記住帳號模式（身分卡＋
  /// 密碼欄）或共用手機模式（員工編號＋密碼欄）。測試後門是目前唯一的寫入
  /// 端；正式的「記住我」持久化還沒有 feature 承接，一般建置下永遠是 null
  /// （共用手機模式）。value 為 null 代表共用手機、沒有記住任何帳號。
  final ValueNotifier<RememberedAccount?> rememberedAccount =
      ValueNotifier<RememberedAccount?>(null);

  /// 測試之間重置狀態，避免前一個測試設定的開關漏到下一個測試。
  void reset() {
    injectedClockIso = null;
    uploadFailAtIndex = null;
    rememberedAccount.value = null;
    ConnectionStatus.instance.reset();
  }
}

/// 「這支手機記住的帳號」：員工編號供登入請求使用，姓名供身分卡顯示。
/// 姓名必須一起記住——登入前 App 沒有任何可以用員工編號反查姓名的 API
/// （也不該有，會洩漏員工編號是否存在）。
class RememberedAccount {
  const RememberedAccount({required this.loginId, required this.name});

  /// 員工編號（staff_accounts.login_id）。
  final String loginId;

  /// 員工姓名，身分卡顯示用。
  final String name;
}

/// 接收測試腳本指令的 localhost HTTP server，只在
/// `--dart-define=TEST_BACKDOOR=true` 時由 [maybeStart] 啟動。
class TestBackdoor {
  TestBackdoor._();

  static const bool _enabled = bool.fromEnvironment('TEST_BACKDOOR');

  static const int port = 9099;

  static final DateTime _processStartedAt = DateTime.now();

  static HttpServer? _server;

  /// 旗標沒開時直接返回；旗標開了才啟動 server，重複呼叫是 no-op。
  static Future<void> maybeStart() async {
    if (!_enabled || _server != null) {
      return;
    }
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
    _server = server;
    server.listen(_handleRequest);
  }

  static Future<void> _handleRequest(HttpRequest request) async {
    try {
      final body = await utf8.decoder.bind(request).join();
      // 排障用請求日誌（headless 環境看不到 console，落檔最可靠）。
      File('${Directory.systemTemp.path}/tms_backdoor_log.txt').writeAsStringSync(
          '${DateTime.now().toIso8601String()} pid=$pid ${request.method} '
          '${request.uri.path} body=[$body]\n',
          mode: FileMode.append);
      final Map<String, dynamic> payload =
          body.isEmpty ? <String, dynamic>{} : jsonDecode(body) as Map<String, dynamic>;

      switch ('${request.method} ${request.uri.path}') {
        case 'GET /state':
          // 觀測用：harness 排障時看「這個 App process 眼中」的開關現況與
          // process 起始時間（判斷點開推播是否意外換了一個新 process）。
          request.response.statusCode = HttpStatus.ok;
          request.response.headers.contentType = ContentType.json;
          request.response.write(jsonEncode({
            'processStartedAt': _processStartedAt.toIso8601String(),
            'injectedClockIso': TestHooks.instance.injectedClockIso,
            'forceOffline': ConnectionStatus.instance.isOffline,
            'uploadFailAtIndex': TestHooks.instance.uploadFailAtIndex,
            'rememberedAccount': TestHooks.instance.rememberedAccount.value?.loginId,
            'flakyConnection': ConnectionStatus.instance.isUnstable,
            'pendingSyncCount': ConnectionStatus.instance.pendingSyncCount.value,
          }));
          await request.response.close();
          break;
        case 'POST /clock':
          TestHooks.instance.injectedClockIso = payload['isoTime'] as String?;
          _respondOk(request);
          break;
        case 'POST /offline':
          ConnectionStatus.instance
              .setOffline(payload['offline'] as bool? ?? false);
          _respondOk(request);
          break;
        case 'POST /upload-fail':
          TestHooks.instance.uploadFailAtIndex = payload['photoIndex'] as int?;
          _respondOk(request);
          break;
        case 'POST /remembered-account':
          // employee 給員工編號代表記住該帳號（name 給身分卡要顯示的姓名，
          // 不帶時以員工編號代替）；employee 給 null（或不帶）代表清成共用
          // 手機、沒有記住任何帳號。
          final employee = payload['employee'] as String?;
          TestHooks.instance.rememberedAccount.value = employee == null
              ? null
              : RememberedAccount(
                  loginId: employee,
                  name: payload['name'] as String? ?? employee,
                );
          _respondOk(request);
          break;
        case 'POST /offline-queue':
          // 「手機上已經累積了 N 筆待同步」這個起始狀態，在 App 自己的規則下
          // 走不出來：離線時唯一還能做的動作是完成進行中的單，而後端保證同一
          // 位員工最多只有一筆進行中，所以靠真實操作最多只累積得到一筆。這裡
          // 放進去的是沒有送出內容的佔位動作，驗收看的是離線列顯示的筆數就是
          // 佇列長度本身。
          final queued = payload['count'] as int? ?? 0;
          for (var i = 0; i < queued; i++) {
            OfflineQueue.instance.enqueue(PendingOperation(
              kind: 'test-placeholder',
              idempotencyKey: ApiClient.newIdempotencyKey(),
              send: (_) async {},
            ));
          }
          _respondOk(request);
          break;
        case 'POST /flaky-connection':
          ConnectionStatus.instance
              .setUnstable(payload['flaky'] as bool? ?? false);
          _respondOk(request);
          break;
        default:
          request.response.statusCode = HttpStatus.notFound;
          await request.response.close();
      }
    } catch (_) {
      request.response.statusCode = HttpStatus.badRequest;
      await request.response.close();
    }
  }

  static void _respondOk(HttpRequest request) {
    request.response.statusCode = HttpStatus.ok;
    request.response.headers.contentType = ContentType.json;
    request.response.write(jsonEncode({'ok': true}));
    request.response.close();
  }
}
