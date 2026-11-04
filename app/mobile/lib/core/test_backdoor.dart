import 'dart:convert';
import 'dart:io';

/// App 跑在自動化測試底下時，讓測試腳本能從外部操控裝置狀態（例如把裝置時間
/// 改到某個時間點、強迫斷網、讓某一次照片上傳失敗），好驗證離線、逾時、重試
/// 這類難以用真實環境重現的情境。
///
/// 一般建置完全不含這個機制：只有編譯時加上
/// `--dart-define=TEST_BACKDOOR=true`，[TestBackdoor.maybeStart] 才會啟動對應
/// 的伺服器；沒加這個旗標時連伺服器物件都不會建立，production 建置不會多開
/// 一個 port。
///
/// 目前測試腳本送進來的指令只會被存進 [TestHooks] 這個 singleton、回應
/// 200，App 自己的業務邏輯還沒去讀這些值來改變行為——也就是「裝置時間」
/// 「離線」「上傳失敗」這三個開關目前只是收得到、還沒真的生效，等對應功能
/// 開發到需要被測試控制時再接上。
class TestHooks {
  TestHooks._();

  static final TestHooks instance = TestHooks._();

  /// 測試腳本要求裝置採用的時間（ISO 格式字串）；App 尚未讀取這個值。
  String? injectedClockIso;

  /// 測試腳本要求裝置視為離線；App 尚未讀取這個值。
  bool forceOffline = false;

  /// 測試腳本要求第幾張照片（從 1 算起）上傳失敗；App 尚未讀取這個值。
  int? uploadFailAtIndex;
}

/// 接收測試腳本指令的 localhost HTTP server，只在
/// `--dart-define=TEST_BACKDOOR=true` 時由 [maybeStart] 啟動。
class TestBackdoor {
  TestBackdoor._();

  static const bool _enabled = bool.fromEnvironment('TEST_BACKDOOR');

  static const int port = 9099;

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
      final Map<String, dynamic> payload =
          body.isEmpty ? <String, dynamic>{} : jsonDecode(body) as Map<String, dynamic>;

      switch ('${request.method} ${request.uri.path}') {
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
