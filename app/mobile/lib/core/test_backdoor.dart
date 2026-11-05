import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

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
/// 五個開關。畫面資料
/// 一律走 `core/api_client.dart` 向真後端取——測試腳本要準備資料，走後端
/// 的測試資料端點，不再從這裡灌 fixture 進畫面。
///
/// 目前測試腳本送進來的指令只會被存進 [TestHooks] 這個 singleton、回應
/// 200，App 自己的業務邏輯還沒去讀這些值來改變行為——也就是這些開關目前
/// 只是收得到、還沒真的生效，等對應功能開發到需要被測試控制時再接上。
class TestHooks {
  TestHooks._();

  static final TestHooks instance = TestHooks._();

  /// 測試腳本要求裝置採用的時間（ISO 格式字串）；App 尚未讀取這個值。
  String? injectedClockIso;

  /// 測試腳本要求裝置視為離線。「我的」頁登出前的離線攔阻讀這個值判斷
  /// 離線（依規格，離線攔阻以這個注入開關為準，App 不另做網路偵測）。
  bool forceOffline = false;

  /// 測試腳本要求第幾張照片（從 1 算起）上傳失敗；App 尚未讀取這個值。
  int? uploadFailAtIndex;

  /// 「這支手機記住的帳號」。登入畫面監聽它決定顯示記住帳號模式（身分卡＋
  /// 密碼欄）或共用手機模式（員工編號＋密碼欄）。測試後門是目前唯一的寫入
  /// 端；正式的「記住我」持久化還沒有 feature 承接，一般建置下永遠是 null
  /// （共用手機模式）。value 為 null 代表共用手機、沒有記住任何帳號。
  final ValueNotifier<RememberedAccount?> rememberedAccount =
      ValueNotifier<RememberedAccount?>(null);

  /// 測試腳本要求把即時連線切成異常態（裝置仍連網，但即時查詢逾時或間歇
  /// 失敗）；App 尚未讀取這個值。
  bool flakyConnection = false;

  /// 測試之間重置狀態，避免前一個測試設定的開關漏到下一個測試。
  void reset() {
    injectedClockIso = null;
    forceOffline = false;
    uploadFailAtIndex = null;
    rememberedAccount.value = null;
    flakyConnection = false;
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
            'forceOffline': TestHooks.instance.forceOffline,
            'uploadFailAtIndex': TestHooks.instance.uploadFailAtIndex,
            'rememberedAccount': TestHooks.instance.rememberedAccount.value?.loginId,
            'flakyConnection': TestHooks.instance.flakyConnection,
          }));
          await request.response.close();
          break;
        case 'POST /clock':
          TestHooks.instance.injectedClockIso = payload['isoTime'] as String?;
          _respondOk(request);
          break;
        case 'POST /offline':
          TestHooks.instance.forceOffline = payload['offline'] as bool? ?? false;
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
        case 'POST /flaky-connection':
          TestHooks.instance.flakyConnection = payload['flaky'] as bool? ?? false;
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
