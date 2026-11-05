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

  /// `/seed` 灌進來的員工清單（目前只存名字，畫面還沒用到）。
  List<String> seedStaffNames = <String>[];

  /// `/seed` 灌進來的名下任務清單；待認領畫面、任務頁都從這份 fixture 渲染。
  ///
  /// 過渡做法：真後端接線前，先讓測試腳本用這個路由直接灌資料進畫面，
  /// 不是真的打後端查任務。等 F4（待認領池）、F2（任務頁）接上真實 API
  /// 後，這條路要整段換掉，不是補丁。
  List<SeedTask> seedTasks = <SeedTask>[];

  /// 測試之間重置狀態，避免前一個測試灌的 fixture 漏到下一個測試。
  void reset() {
    injectedClockIso = null;
    forceOffline = false;
    uploadFailAtIndex = null;
    seedStaffNames = <String>[];
    seedTasks = <SeedTask>[];
  }
}

/// `/seed` 灌進來的單筆任務 fixture：對應 feature 檔「阿美名下有以下任務」
/// data table 的房號、內容、狀態、已做分鐘、停住原因五欄。
class SeedTask {
  const SeedTask({
    required this.roomNo,
    required this.content,
    required this.status,
    this.elapsedMinutes = 0,
    this.pausedReason,
  });

  /// 房號，例如 `305`。
  final String roomNo;

  /// 任務內容，例如 `退房清潔`。
  final String content;

  /// 任務狀態，直接沿用 spec 用語（白話全寫，不縮寫），例如
  /// `待認領`、`進行中`、`已中斷`。
  final String status;

  /// 已做分鐘數。
  final int elapsedMinutes;

  /// 停住原因（只有「已中斷」狀態才有意義），例如 `工務已修好：水電`。
  final String? pausedReason;

  factory SeedTask.fromJson(Map<String, dynamic> json) {
    return SeedTask(
      roomNo: json['roomNo'] as String,
      content: json['content'] as String,
      status: json['status'] as String,
      elapsedMinutes: json['elapsedMinutes'] as int? ?? 0,
      pausedReason: json['pausedReason'] as String?,
    );
  }
}

/// 接收測試腳本指令的 localhost HTTP server，只在
/// `--dart-define=TEST_BACKDOOR=true` 時由 [maybeStart] 啟動。
class TestBackdoor {
  TestBackdoor._();

  static const bool _enabled = bool.fromEnvironment('TEST_BACKDOOR');

  static const int port = 9099;

  static final DateTime _processStartedAt = DateTime.now();

  static HttpServer? _server;

  static String get _seedFilePath =>
      '${Directory.systemTemp.path}/tms_test_seed.json';

  /// 旗標沒開時直接返回；旗標開了才啟動 server，重複呼叫是 no-op。
  static Future<void> maybeStart() async {
    if (!_enabled || _server != null) {
      return;
    }
    await _restoreSeedIfPersisted();
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
    _server = server;
    server.listen(_handleRequest);
  }

  /// 冷啟動時把上一個 process 持久化的 seed 載回（說明見 POST /seed 處的註解）。
  static Future<void> _restoreSeedIfPersisted() async {
    try {
      final file = File(_seedFilePath);
      if (await file.exists()) {
        _applySeed(jsonDecode(await file.readAsString()) as Map<String, dynamic>);
      }
    } catch (_) {
      // 檔案壞掉就當沒有 seed，讓測試在斷言處誠實失敗，而不是啟動就崩。
    }
  }

  static void _applySeed(Map<String, dynamic> payload) {
    final staffJson = payload['staff'] as List<dynamic>? ?? <dynamic>[];
    final tasksJson = payload['tasks'] as List<dynamic>? ?? <dynamic>[];
    TestHooks.instance.seedStaffNames =
        staffJson.map((entry) => entry.toString()).toList();
    TestHooks.instance.seedTasks = tasksJson
        .map((entry) => SeedTask.fromJson(entry as Map<String, dynamic>))
        .toList();
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
          // 觀測用：harness 排障時看「這個 App process 眼中」的 fixture 現況與
          // process 起始時間（判斷點開推播是否意外換了一個新 process）。
          request.response.statusCode = HttpStatus.ok;
          request.response.headers.contentType = ContentType.json;
          request.response.write(jsonEncode({
            'processStartedAt': _processStartedAt.toIso8601String(),
            'staff': TestHooks.instance.seedStaffNames,
            'taskRoomNos':
                TestHooks.instance.seedTasks.map((t) => t.roomNo).toList(),
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
        case 'POST /seed':
          // 過渡做法（見 TestHooks.seedTasks 說明）：真後端接線前，待認領
          // 池與任務頁直接讀這份灌進來的 fixture，不打真實 API。
          _applySeed(payload);
          // 同步落地到 sandbox 暫存檔：點開推播可能把 App 換成一個全新的
          // process（XCUITest session 與 simctl launch 的 process 不是同一個，
          // 由誰喚醒就由誰起新 process），記憶體裡的 fixture 會整包蒸發。
          // 持久化後冷啟動時載回，語意上等同真實 App 冷啟動後重新向後端取資料。
          await File(_seedFilePath)
              .writeAsString(jsonEncode(payload), flush: true);
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
