import 'dart:convert';
import 'dart:io';
import 'dart:math';

/// 真後端的 API client：登入換 JWT，之後的讀取請求都帶這把 token。
///
/// 基礎 URL 沿用登入殼既有的 `--dart-define=API_BASE_URL=...` 慣例，沒帶
/// 旗標時預設打本機 8080。目前只封裝本 increment 畫面需要的端點
/// （`specs/plans/001-staff-app-increment/ui/ui-plan.md` 列出的後端契約
/// 清單內）：
///
/// - `POST /staff-accounts/login`（`LoginRequest`／`LoginResponse`）
/// - `POST /staff-accounts/change-password`（`ChangePasswordRequest`／`LoginResponse`）
/// - `GET /me/today`（`MyToday`）
/// - `GET /pool`（`DeptPool`）
/// - `GET /tasks/{id}`（`TaskDetail`）
/// - `GET /notifications`、`POST /notifications/{id}/read`、
///   `POST /notifications/read-all`（通知中心，`NotificationItem`）
///
/// 欄位名全部照抄 `specs/truth/contracts/components/schemas/` 的契約定義，
/// 不自創欄位。
class ApiClient {
  ApiClient({String? baseUrl}) : baseUrl = baseUrl ?? _defaultBaseUrl;

  /// 預設打本機後端。路徑要含 `/api/v1`：後端所有 REST controller 都掛在
  /// 這個前綴下，少了它會打到 Spring Security 眼中不存在的路徑，一律被
  /// 回 401，連登入端點都進不去。
  static const String _defaultBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080/api/v1',
  );

  /// 全 App 共用的 client。宣告成可覆寫的靜態欄位，widget 測試可以換成
  /// fake 實作，不用真的起後端。
  static ApiClient instance = ApiClient();

  final String baseUrl;

  /// 登入成功後存下的 JWT；之後的請求放進 `Authorization: Bearer` 標頭。
  String? _token;

  /// 登入成功的身分資訊（staffId／dept 等）。工務首頁要用 staffId 在任務
  /// 清單裡挑出「我手上的工務單」、登入後的部門分流要看 dept，所以整份留著。
  LoginResult? _session;

  bool get isLoggedIn => _token != null;

  /// 目前登入者；還沒登入為 null。
  LoginResult? get session => _session;

  /// `POST /staff-accounts/login`：員工編號＋明文密碼換 JWT。
  /// 成功時把 token 存在 client 裡，後續讀取請求自動帶上。
  Future<LoginResult> login({
    required String loginId,
    required String password,
  }) async {
    final json = await _requestJson(
      'POST',
      '/staff-accounts/login',
      body: {'loginId': loginId, 'password': password},
    );
    final result = LoginResult.fromJson(json as Map<String, dynamic>);
    _token = result.token;
    _session = result;
    return result;
  }

  /// `POST /staff-accounts/change-password`：本人設定新密碼（契約
  /// `changeOwnPassword`）。後端重新雜湊新密碼、清掉 `mustChangePassword`
  /// 旗標，並換發一組新的 JWT 回傳——形狀與登入相同，所以回應沿用
  /// [LoginResult]。
  ///
  /// 強制改密情境（臨時密碼登入後）身分已由登入換得的 token 證明，不帶
  /// [currentPassword]；自願變更情境才需要帶。密碼強度規則 truth 尚未定義，
  /// App 不自行發明長度或字元門檻，一律交給後端判斷。
  ///
  /// 成功後舊 token 立即失效，所以這裡把 client 裡的 token 與身分一起換成
  /// 新的，後續請求才不會拿著已失效的憑證。
  Future<LoginResult> changePassword({
    String? currentPassword,
    required String newPassword,
  }) async {
    final json = await _requestJson(
      'POST',
      '/staff-accounts/change-password',
      body: {
        if (currentPassword != null) 'currentPassword': currentPassword,
        'newPassword': newPassword,
      },
    );
    final result = LoginResult.fromJson(json as Map<String, dynamic>);
    _token = result.token;
    _session = result;
    return result;
  }

  /// `GET /me/today`：我的今日進度與下一間推薦。
  Future<MyToday> getMyToday() async {
    final json = await _requestJson('GET', '/me/today');
    return MyToday.fromJson(json as Map<String, dynamic>);
  }

  /// `GET /pool`：部門待認領池（含分類筆數）。四個查詢維度都來自契約
  /// `listDeptPool`：[dept] 指定要看哪一個部門的池；[category] 只回該類別
  /// （`cleaning`／`amenity`／`repair`／`other`，房務待認領的分類磚用它）；
  /// [urgent]=true 只回急件；[source] 只回該來源（`staff_report`＝房務報修、
  /// `desk_forward`＝櫃檯）。帶 [category] 時 `counts` 仍是整個池的筆數，
  /// 分類磚的數字不會跟著縮水。分流與排序一律交給後端——App 只把磚的選擇
  /// 換成查詢參數，不在本機重排或過濾。
  ///
  /// [dept] 省略就是「看自己帳號上的部門」，現場員工（房務／工務／櫃檯）一律
  /// 這樣打。主管與老闆的帳號沒有部門，後端無從推斷要看哪一池，省略時回 422
  /// `dept_required`，所以這兩種身分必須指定；現場員工帶了自己部門以外的值
  /// 會被後端以 403 `forbidden_dept_pool` 擋下。
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async {
    final query = <String, String>{
      if (dept != null) 'dept': dept,
      if (category != null) 'category': category,
      if (urgent != null) 'urgent': '$urgent',
      if (source != null) 'source': source,
    };
    final json = await _requestJson('GET', '/pool', query: query);
    return DeptPool.fromJson(json as Map<String, dynamic>);
  }

  /// `GET /tasks`：租戶任務清單。工務首頁用它挑出「指派給我、還開著」的
  /// 工務單（契約沒有「我的工務單」專用查詢，上游缺口已回報；這裡的挑選
  /// 是補位，不是待認領池那種被禁止的本機過濾）。
  Future<List<TaskSummary>> listTasks() async {
    final json = await _requestJson('GET', '/tasks');
    return (json as List<dynamic>? ?? <dynamic>[])
        .map((entry) => TaskSummary.fromJson(entry as Map<String, dynamic>))
        .toList();
  }

  /// `POST /tasks/{id}/claim`：認領待認領的任務（工務單也是任務）。
  /// 已被別人認領時後端回 409，message 帶認領人姓名，丟出 [ApiException]
  /// 讓畫面呈現撞單提示。
  Future<void> claimTask(String taskId) async {
    await _requestJson('POST', '/tasks/$taskId/claim', idempotent: true);
  }

  /// `POST /tasks/{id}/complete`：完成任務（契約 `tasksComplete`）。
  /// 修繕任務必附完修照片；[resolutionType] 是完修三選一（fixed／temporary／
  /// cannot_fix）。追蹤單自動建立與房況轉停用待確認都是後端行為，App 只送
  /// 這個欄位、呈現結果。
  ///
  /// 打掃類任務兩個欄位都不帶：後端對非修繕任務不收也不看這兩個值，硬塞空
  /// 清單反而會讓請求看起來像一張沒有照片的工務單。
  ///
  /// [idempotencyKey] 給離線佇列再送一次時指定同一把鍵用；不帶時每次都配一把
  /// 新的，一次動作只保護它自己的重送。
  Future<void> completeTask(
    String taskId, {
    List<String>? photoIds,
    String? resolutionType,
    String? idempotencyKey,
  }) async {
    await _requestJson(
      'POST',
      '/tasks/$taskId/complete',
      idempotent: true,
      idempotencyKey: idempotencyKey,
      body: {
        if (photoIds != null) 'photoIds': photoIds,
        if (resolutionType != null) 'resolutionType': resolutionType,
      },
    );
  }

  /// `GET /repair-tickets?roomId=`：同房未撤回的工務單（契約
  /// `listRepairTickets`）。任務頁的同房工務單區用它，報修人從那一列進到
  /// 自己那張單的詳情。
  Future<List<RepairTicketSummary>> listRepairTickets(String roomId) async {
    final json = await _requestJson(
      'GET',
      '/repair-tickets',
      query: {'roomId': roomId},
    );
    return (json as List<dynamic>)
        .whereType<Map<String, dynamic>>()
        .map(RepairTicketSummary.fromJson)
        .toList();
  }

  /// `POST /repair-tickets/precheck`：開單前檢查（契約 `precheckRepairTicket`）。
  /// 報修人選定類別時呼叫，不建立任何工務單，只回兩件事：同房同類別還沒結案
  /// 的那張單（重複提示用），以及系統依飯店設定規則算出的急件判定。判定是後端
  /// 的結論，App 只負責呈現。
  Future<RepairTicketPrecheck> precheckRepairTicket({
    required String sourceTaskId,
    required String category,
  }) async {
    final json = await _requestJson(
      'POST',
      '/repair-tickets/precheck',
      body: {'sourceTaskId': sourceTaskId, 'category': category},
    );
    return RepairTicketPrecheck.fromJson(json as Map<String, dynamic>);
  }

  /// `GET /repair-tickets/{ticketId}`：工務單詳情（路徑參數是工務單的任務
  /// id，不是 W- 單號）。
  Future<RepairTicketDetail> readRepairTicket(String taskId) async {
    final json = await _requestJson('GET', '/repair-tickets/$taskId');
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `POST /repair-tickets/{ticketId}/photos`：補照片到既有工務單（契約
  /// `appendRepairTicketPhotos`）。重複提示的「補充照片到這張單」走這裡。
  Future<void> appendRepairTicketPhotos(
    String taskId, {
    required List<String> photoIds,
  }) async {
    await _requestJson(
      'POST',
      '/repair-tickets/$taskId/photos',
      idempotent: true,
      body: {'photoIds': photoIds},
    );
  }

  /// `POST /repair-tickets/{ticketId}/withdraw`：報修人在工務認領前撤回
  /// （契約 `withdrawRepairTicket`）。已被認領時後端回 409 `already_claimed`
  /// ——畫面要在那之前就不顯示這顆按鈕。
  Future<RepairTicketDetail> withdrawRepairTicket(String taskId) async {
    final json = await _requestJson(
      'POST',
      '/repair-tickets/$taskId/withdraw',
      idempotent: true,
    );
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `POST /repair-tickets/{ticketId}/resubmit`：報修人補完資料後把被退回的
  /// 工務單重新送審（契約 `resubmitRepairTicket`）。
  Future<RepairTicketDetail> resubmitRepairTicket(String taskId) async {
    final json = await _requestJson(
      'POST',
      '/repair-tickets/$taskId/resubmit',
      idempotent: true,
    );
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `POST /repair-tickets/{ticketId}/release-to-pool`：負責人自行把公共區域
  /// 的工務單放回待認領（契約 `releaseRepairTicketToPool`）。客房單後端回
  /// 409 `room_ticket_not_requeueable`——畫面要在那之前就不顯示按鈕。
  Future<RepairTicketDetail> releaseRepairTicketToPool(String taskId) async {
    final json = await _requestJson(
      'POST',
      '/repair-tickets/$taskId/release-to-pool',
      idempotent: true,
    );
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `POST /repair-photos`：建立一張工務單照片的上傳位置（兩段式的第一段，
  /// 契約 `createRepairPhoto`）。[purpose] 是 report（報修）或 completion
  /// （完修）。回傳照片 id，接著用 [uploadRepairPhotoContent] 上傳內容。
  Future<String> createRepairPhoto({required String purpose}) async {
    final json = await _requestJson(
      'POST',
      '/repair-photos',
      idempotent: true,
      body: {
        'purpose': purpose,
        'takenAt': DateTime.now().toUtc().toIso8601String(),
      },
    );
    return (json as Map<String, dynamic>)['id'] as String;
  }

  /// `PUT /repair-photos/{photoId}/content`：上傳一張照片的內容（兩段式的
  /// 第二段，冪等）。
  Future<void> uploadRepairPhotoContent(String photoId, List<int> jpegBytes) async {
    await _requestJson(
      'PUT',
      '/repair-photos/$photoId/content',
      binaryBody: jpegBytes,
    );
  }

  /// `POST /repair-tickets`：開立工務單（契約 `createRepairTicket`）。
  /// 三條開單路徑擇一：房務從原任務報修帶 [sourceTaskId]，工務自開帶
  /// [roomId] 或 [isPublicArea]=true。急件由系統判定，請求不帶任何急件欄位。
  /// [idempotencyKey] 給離線佇列重送用：同一次送出重送幾遍都帶同一把鍵，
  /// 後端才認得出是同一次動作、只建立一張單。
  Future<RepairTicketDetail> createRepairTicket({
    String? sourceTaskId,
    String? roomId,
    bool isPublicArea = false,
    required String category,
    required List<String> photoIds,
    bool interruptSourceTask = false,
    String? idempotencyKey,
  }) async {
    final json = await _requestJson(
      'POST',
      '/repair-tickets',
      idempotent: true,
      idempotencyKey: idempotencyKey,
      body: {
        if (sourceTaskId != null) 'sourceTaskId': sourceTaskId,
        if (roomId != null) 'roomId': roomId,
        if (isPublicArea) 'isPublicArea': true,
        'category': category,
        'photoIds': photoIds,
        if (interruptSourceTask) 'interruptSourceTask': true,
      },
    );
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `POST /tasks/{id}/start`：負責人開工（契約 `tasksStart`）。同一位員工
  /// 已經有另一筆進行中的單時後端回 409 `doing_task_exists`，message 是給人
  /// 看的「{房號} 還在進行中」、context 帶那一筆的 taskId 與 roomNo——「一次
  /// 一間」的判定全在後端，App 只負責把它呈現出來。
  Future<TaskDetail> startTask(String taskId) async {
    final json = await _requestJson('POST', '/tasks/$taskId/start', idempotent: true);
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `POST /tasks/{id}/defer`：負責人把自己名下待開始的任務延後（契約
  /// `tasksDefer`）。[reason] 是預設原因或自填文字，長度規則（去頭尾空白後
  /// 1–60 字）由後端把關，回 422 `reason_invalid`；App 端的字數上限提示只是
  /// 讓員工在送出前就知道，不是權威判定。
  Future<TaskDetail> deferTask(String taskId, String reason) async {
    final json = await _requestJson(
      'POST',
      '/tasks/$taskId/defer',
      body: {'reason': reason},
      idempotent: true,
    );
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `POST /tasks/{id}/interrupt`：負責人把自己進行中的任務轉為已中斷
  /// （契約 `tasksInterrupt`）。[reason] 的規則同 [deferTask]。
  Future<TaskDetail> interruptTask(String taskId, String reason) async {
    final json = await _requestJson(
      'POST',
      '/tasks/$taskId/interrupt',
      body: {'reason': reason},
      idempotent: true,
    );
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `GET /tasks/{id}`：單一任務的員工視角詳情。
  Future<TaskDetail> getTask(String taskId) async {
    final json = await _requestJson('GET', '/tasks/$taskId');
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `GET /sop-items`：租戶的 SOP 與查房項目全集（契約 `listSopItems`）。
  /// 打掃 SOP 與查房項目同構共表，差別在 `appliesTo`（`cleaning`／`inspection`），
  /// 端點不收參數，要哪一種由呼叫端自己挑。任務詳情的 `checkedSopItems`／
  /// `skippedSopItems` 只給 id，項目名稱一律從這裡取。
  Future<List<SopItem>> listSopItems() async {
    final json = await _requestJson('GET', '/sop-items');
    return (json as List<dynamic>? ?? <dynamic>[])
        .map((entry) => SopItem.fromJson(entry as Map<String, dynamic>))
        .toList();
  }

  /// `POST /tasks/{id}/sop-check`：勾選或跳過一項 SOP（契約 `tasksSopCheck`）。
  /// [result] 只收 `checked` 或 `skipped`；跳過不擋完成，後端在完成那一步會把
  /// 沒勾也沒跳過的項目一律補記成跳過。回應是更新後的任務詳情。
  Future<TaskDetail> sopCheck(
    String taskId, {
    required String sopItemId,
    required String result,
  }) async {
    final json = await _requestJson(
      'POST',
      '/tasks/$taskId/sop-check',
      body: {'sopItemId': sopItemId, 'result': result},
      idempotent: true,
    );
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  /// `GET /room-memories/suggestions?taskId=`：查一下打開時的推薦問題。
  /// 順序由後端依任務情境排好（剛跳過的 SOP 項在前），App 照單列出，不自己排。
  Future<List<AskSuggestion>> listAskSuggestions(String taskId) async {
    final json = await _requestJson(
      'GET',
      '/room-memories/suggestions',
      query: {'taskId': taskId},
    );
    return (json as List<dynamic>? ?? <dynamic>[])
        .map((entry) => AskSuggestion.fromJson(entry as Map<String, dynamic>))
        .toList();
  }

  /// `POST /room-memories/ask`：拿一則問題去查這張任務的房間記錄或 SOP。
  /// 回答與出處全部由後端組好，查不到時 `found=false`、`answer` 是 null。
  Future<AskAnswer> askRoomMemory({
    required String taskId,
    required String question,
  }) async {
    final json = await _requestJson(
      'POST',
      '/room-memories/ask',
      body: {'taskId': taskId, 'question': question},
      idempotent: true,
    );
    return AskAnswer.fromJson(json as Map<String, dynamic>);
  }

  /// `GET /notifications`：我的通知清單，後端已依 createdAt 新到舊排好；
  /// 「今天／更早」分組是 App 端的呈現邏輯（契約明訂本端點不分組）。
  Future<List<NotificationItem>> listNotifications() async {
    final json = await _requestJson('GET', '/notifications');
    return _asNotificationList(json);
  }

  /// `POST /notifications/{id}/read`：標記單則已讀（冪等），回更新後那筆。
  Future<NotificationItem> markNotificationRead(String notificationId) async {
    final json = await _requestJson('POST', '/notifications/$notificationId/read');
    return NotificationItem.fromJson(json as Map<String, dynamic>);
  }

  /// `POST /notifications/read-all`：全部標為已讀，回標記後的完整清單。
  Future<List<NotificationItem>> markAllNotificationsRead() async {
    final json = await _requestJson('POST', '/notifications/read-all');
    return _asNotificationList(json);
  }

  static List<NotificationItem> _asNotificationList(Object? json) {
    return (json as List<dynamic>? ?? <dynamic>[])
        .map((entry) => NotificationItem.fromJson(entry as Map<String, dynamic>))
        .toList();
  }

  Future<Object?> _requestJson(
    String method,
    String path, {
    Map<String, dynamic>? body,
    Map<String, String>? query,
    List<int>? binaryBody,
    bool idempotent = false,
    String? idempotencyKey,
  }) async {
    final client = HttpClient();
    try {
      var uri = Uri.parse('$baseUrl$path');
      if (query != null && query.isNotEmpty) {
        uri = uri.replace(queryParameters: {...uri.queryParameters, ...query});
      }
      final request = await client.openUrl(method, uri);
      final token = _token;
      if (token != null) {
        request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
      }
      if (idempotent) {
        // 沒有指定鍵時每次操作配一把新的：同一把鍵只保護「同一次使用者動作
        // 的重送」，跨動作重用會讓第二次動作拿到第一次的回放結果。離線佇列
        // 再送一次時會把那筆動作自己的鍵傳進來，後端才認得出是同一次動作。
        request.headers.set(
            'Idempotency-Key', idempotencyKey ?? newIdempotencyKey());
      }
      if (binaryBody != null) {
        request.headers.contentType = ContentType('image', 'jpeg');
        request.add(binaryBody);
      } else if (body != null) {
        request.headers.contentType = ContentType.json;
        request.write(jsonEncode(body));
      }
      final response = await request.close();
      final text = await response.transform(utf8.decoder).join();
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw ApiException(statusCode: response.statusCode, body: text);
      }
      return text.isEmpty ? null : jsonDecode(text);
    } finally {
      client.close();
    }
  }

  /// 產生 UUID v4 字串（Idempotency-Key 用）。App 沒有引入 uuid 套件，
  /// 這把鍵只要求「夠隨機、不重複」，用 [Random.secure] 自組即可。
  static String newIdempotencyKey() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex =
        bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
        '${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }
}

/// 後端回非 2xx 時丟出的例外，帶狀態碼讓畫面決定要顯示哪句提示。
///
/// 後端錯誤 body 的固定形狀（`specs/truth/contracts/components/schemas/shared.yaml`
/// 的 ApiError）：`{code, message, details?, context?}`。[errorCode]／
/// [errorMessage]／[errorContext] 是對這個形狀的惰性解析，body 不是合法
/// JSON 時回 null，讓畫面退回自己的固定文案。
class ApiException implements Exception {
  const ApiException({required this.statusCode, required this.body});

  final int statusCode;
  final String body;

  /// ApiError.code，例如 `credentials_invalid`／`account_locked`。
  String? get errorCode => _bodyJson()?['code'] as String?;

  /// ApiError.message，後端已經是給人看的白話句，可直接上畫面。
  String? get errorMessage => _bodyJson()?['message'] as String?;

  /// ApiError.context，例如帳號鎖定時的 `lockedUntil`／`remainingMinutes`。
  Map<String, dynamic>? get errorContext =>
      _bodyJson()?['context'] as Map<String, dynamic>?;

  /// 帳號鎖定（429 account_locked）時後端算好的剩餘鎖定分鐘；不是鎖定回應
  /// 時為 null。鎖定剩餘時間一律以這個值為準，App 不自己計時，才不會和
  /// 後端的解鎖時間點對不上。
  int? get lockoutRemainingMinutes => statusCode == 429
      ? (errorContext?['remainingMinutes'] as num?)?.toInt()
      : null;

  Map<String, dynamic>? _bodyJson() {
    try {
      final decoded = jsonDecode(body);
      return decoded is Map<String, dynamic> ? decoded : null;
    } on FormatException {
      return null;
    }
  }

  @override
  String toString() => 'ApiException($statusCode)';
}

/// `LoginResponse`（specs/truth/contracts/components/schemas/員工帳號與登入.yaml）。
class LoginResult {
  const LoginResult({
    required this.token,
    required this.expiresAt,
    required this.staffId,
    required this.name,
    required this.role,
    required this.mustChangePassword,
    this.dept,
  });

  final String token;
  final String expiresAt;
  final String staffId;
  final String name;
  final String? dept;
  final String role;

  /// true 表示臨時密碼登入，必須先改密碼才能呼叫其他需要身分的 API。
  final bool mustChangePassword;

  factory LoginResult.fromJson(Map<String, dynamic> json) {
    return LoginResult(
      token: json['token'] as String,
      expiresAt: json['expiresAt'] as String,
      staffId: json['staffId'] as String,
      name: json['name'] as String,
      dept: json['dept'] as String?,
      role: json['role'] as String,
      mustChangePassword: json['mustChangePassword'] as bool,
    );
  }
}

/// `MyToday`（specs/truth/contracts/components/schemas/員工工作台.yaml）。
class MyToday {
  const MyToday({
    required this.cleaningDone,
    required this.cleaningTotal,
    required this.inspectedCount,
    required this.completedCount,
    required this.adHocCompleted,
    required this.remainingMinutes,
    required this.todayTasks,
    required this.nextTasks,
    required this.zeroReturnStreakDays,
    this.engineeringSummary,
  });

  final int cleaningDone;
  final int cleaningTotal;
  final int inspectedCount;
  final int completedCount;
  final int adHocCompleted;
  final int remainingMinutes;

  /// 本人今日的全部任務，已由後端排好（未結束的在前、同組內 doing 最前）。
  /// 首頁任務列要列出或進入今天任何一張單一律用這份，不是只收 accepted 的
  /// [nextTasks]——進行中、延後、中斷、今天已完成的單只有這裡看得到。
  final List<TodayTask> todayTasks;

  /// 待開始的單的推薦子集，只用於首頁「下一間」卡，不是今日任務的全部。
  final List<NextTask> nextTasks;
  final int zeroReturnStreakDays;

  /// 工務視角今日進度；只有工務部門（dept=engineering）的呼叫者有值。
  final EngineeringSummary? engineeringSummary;

  factory MyToday.fromJson(Map<String, dynamic> json) {
    final todayTasksJson = json['todayTasks'] as List<dynamic>? ?? <dynamic>[];
    final nextTasksJson = json['nextTasks'] as List<dynamic>? ?? <dynamic>[];
    final engineeringJson = json['engineeringSummary'] as Map<String, dynamic>?;
    return MyToday(
      cleaningDone: json['cleaningDone'] as int,
      cleaningTotal: json['cleaningTotal'] as int,
      inspectedCount: json['inspectedCount'] as int,
      completedCount: json['completedCount'] as int,
      adHocCompleted: json['adHocCompleted'] as int,
      remainingMinutes: json['remainingMinutes'] as int,
      todayTasks: todayTasksJson
          .map((entry) => TodayTask.fromJson(entry as Map<String, dynamic>))
          .toList(),
      nextTasks: nextTasksJson
          .map((entry) => NextTask.fromJson(entry as Map<String, dynamic>))
          .toList(),
      zeroReturnStreakDays: json['zeroReturnStreakDays'] as int,
      engineeringSummary: engineeringJson == null
          ? null
          : EngineeringSummary.fromJson(engineeringJson),
    );
  }
}

/// `MyToday.engineeringSummary`：工務首頁三格數字的資料來源。
class EngineeringSummary {
  const EngineeringSummary({
    required this.openCount,
    required this.completedToday,
    required this.urgentCount,
    required this.tickets,
  });

  /// 三格都是 0、沒有任何單的彙總，給「回應裡沒有工務彙總」的呼叫端當顯示值用。
  static const EngineeringSummary none = EngineeringSummary(
    openCount: 0,
    completedToday: 0,
    urgentCount: 0,
    tickets: <EngineeringTicket>[],
  );

  /// 手上張數：本人目前 accepted／doing 的工務單筆數。
  final int openCount;

  /// 今天已完修：本人今天 done 的工務單筆數。
  final int completedToday;

  /// 急件數：手上單（accepted／doing）中 isUrgent=true 的筆數。
  final int urgentCount;

  /// 本人手上（accepted／doing）與今天完修（done）的工務單，已由後端排好
  /// （急件在前、未結案在已完修之前），工務首頁的單列直接依序呈現。
  final List<EngineeringTicket> tickets;

  factory EngineeringSummary.fromJson(Map<String, dynamic> json) {
    final ticketsJson = json['tickets'] as List<dynamic>? ?? <dynamic>[];
    return EngineeringSummary(
      openCount: json['openCount'] as int,
      completedToday: json['completedToday'] as int,
      urgentCount: json['urgentCount'] as int,
      tickets: ticketsJson
          .map((entry) => EngineeringTicket.fromJson(entry as Map<String, dynamic>))
          .toList(),
    );
  }
}

/// `EngineeringTicket`：工務首頁單列的一張工務單。
///
/// 欄位名與 `RepairTicketSummary` 一致，[taskId] 可以直接拿去打工務單詳情。
class EngineeringTicket {
  const EngineeringTicket({
    required this.ticketNo,
    required this.taskId,
    required this.roomNo,
    required this.isPublicArea,
    required this.title,
    required this.state,
    required this.isUrgent,
    required this.source,
  });

  /// 單號（W-0412 這種）。契約列為必填，但後端目前可能給 null，所以收得下
  /// null，不在解析時就把整份回應打掉。
  final String? ticketNo;
  final String taskId;

  /// 客房單的房號；開給公共區域的單沒有房號，為 null。
  final String? roomNo;

  /// true 表示這張單開給公共區域，此時 [roomNo] 為 null。
  final bool isPublicArea;
  final String title;

  /// accepted｜doing｜done，照契約 enum，不縮寫。
  final String state;
  final bool isUrgent;

  /// 單的來源，照契約 PoolSource enum：工務單只會是 staff_report（房務報修或
  /// 工務自開）或 desk_forward（櫃檯交辦）。
  final String? source;

  factory EngineeringTicket.fromJson(Map<String, dynamic> json) {
    return EngineeringTicket(
      ticketNo: json['ticketNo'] as String?,
      taskId: json['taskId'] as String,
      roomNo: json['roomNo'] as String?,
      isPublicArea: json['isPublicArea'] as bool? ?? false,
      title: json['title'] as String? ?? '',
      state: json['state'] as String,
      isUrgent: json['isUrgent'] as bool? ?? false,
      source: json['source'] as String?,
    );
  }
}

/// `TodayTask`：首頁任務列的一張單。
///
/// 欄位與任務詳情（`TaskDetail`）共用同一組名稱與值，[taskId] 可以直接拿去
/// 打任務頁。
class TodayTask {
  const TodayTask({
    required this.taskId,
    required this.roomNo,
    required this.title,
    required this.type,
    required this.state,
    required this.redoCount,
    required this.estMin,
    required this.workedMinutes,
    required this.holdReason,
    required this.inspection,
    required this.inspectionWaived,
  });

  final String taskId;
  final String roomNo;
  final String title;

  /// cleaning｜amenity｜repair｜other，照契約 enum，不縮寫。
  final String type;

  /// accepted｜doing｜deferred｜interrupted｜done｜inspected，與 tasks.state
  /// 同一套值。
  final String state;

  /// 被查房退回重做的次數；大於 0 時單卡標示「重做第 N 次」。
  final int redoCount;

  /// 預估分鐘，由排房計畫帶入；沒有預估值時為 null。
  final int? estMin;

  /// 已做分鐘，口徑同 `TaskDetail.elapsedMinutes`。
  final int workedMinutes;

  /// 停住原因，延後或中斷時才有值，其他狀態為 null。
  final String? holdReason;

  /// passed｜waived｜failed，還沒有查房結果為 null。
  final String? inspection;

  /// 開工當下鎖定的是否免查房。
  final bool inspectionWaived;

  factory TodayTask.fromJson(Map<String, dynamic> json) {
    return TodayTask(
      taskId: json['taskId'] as String,
      roomNo: json['roomNo'] as String,
      title: json['title'] as String? ?? '',
      type: json['type'] as String,
      state: json['state'] as String,
      redoCount: json['redoCount'] as int? ?? 0,
      estMin: json['estMin'] as int?,
      workedMinutes: json['workedMinutes'] as int? ?? 0,
      holdReason: json['holdReason'] as String?,
      inspection: json['inspection'] as String?,
      inspectionWaived: json['inspectionWaived'] as bool? ?? false,
    );
  }
}

/// `GET /tasks` 清單的一筆，只收工務首頁挑「我手上的工務單」要看的欄位。
class TaskSummary {
  const TaskSummary({
    required this.id,
    required this.type,
    required this.state,
    this.assigneeId,
  });

  final String id;

  /// cleaning｜amenity｜repair｜other，照契約 enum。
  final String type;

  /// 任務狀態，照契約 enum（pool｜accepted｜doing｜done…）。
  final String state;
  final String? assigneeId;

  factory TaskSummary.fromJson(Map<String, dynamic> json) {
    return TaskSummary(
      id: json['id'] as String,
      type: json['type'] as String,
      state: json['state'] as String,
      assigneeId: json['assigneeId'] as String?,
    );
  }
}

/// `RepairTicket`（specs/truth/contracts/components/schemas/工務單.yaml）中
/// 工務視角畫面要用的欄位。
class RepairTicketDetail {
  const RepairTicketDetail({
    required this.taskId,
    required this.ticketNo,
    required this.roomNo,
    required this.category,
    required this.isUrgent,
    required this.state,
    required this.reporterName,
    this.verifiedBy,
    this.verifiedAt,
    this.resolutionNote,
    this.rejectReason,
    this.urgentDowngradeReason,
    this.originTicketNo,
  });

  final String taskId;

  /// W- 開頭的單號，例如 W-0410。
  final String ticketNo;

  /// 房號；公共區域的單固定是「公共區域」（後端以這個特殊房號代表無房號
  /// 的公共區域單，契約沒有獨立的布林旗標）。
  final String roomNo;

  /// 工務單類別，照契約 RepairCategory enum（air_conditioning…other）。
  final String category;
  final bool isUrgent;

  /// pending_review｜rejected｜pool｜accepted｜doing｜done｜cancelled。
  final String state;
  final String reporterName;

  /// 驗收通過的主管顯示名稱；尚未驗收為 null。App 只讀不寫——驗收是主管端
  /// 的動作，App 沒有驗收操作。
  final String? verifiedBy;
  final String? verifiedAt;
  final String? resolutionNote;
  final String? rejectReason;
  final String? urgentDowngradeReason;
  final String? originTicketNo;

  /// 公共區域單（無房號）才可以放回待認領；客房單只能走暫停。
  bool get isPublicArea => roomNo == '公共區域';

  factory RepairTicketDetail.fromJson(Map<String, dynamic> json) {
    return RepairTicketDetail(
      taskId: json['taskId'] as String,
      ticketNo: json['ticketNo'] as String,
      roomNo: json['roomNo'] as String,
      category: json['category'] as String,
      isUrgent: json['isUrgent'] as bool? ?? false,
      state: json['state'] as String,
      reporterName: json['reporterName'] as String,
      verifiedBy: json['verifiedBy'] as String?,
      verifiedAt: json['verifiedAt'] as String?,
      resolutionNote: json['resolutionNote'] as String?,
      rejectReason: json['rejectReason'] as String?,
      urgentDowngradeReason: json['urgentDowngradeReason'] as String?,
      originTicketNo: json['originTicketNo'] as String?,
    );
  }
}

/// 開單前檢查的結果（契約 RepairTicketPrecheckResponse）。
class RepairTicketPrecheck {
  const RepairTicketPrecheck({
    required this.urgent,
    this.urgentRule,
    this.duplicate,
  });

  /// 系統判定這張單是不是急件。這是後端依飯店設定規則算出來的結論，
  /// App 不自己判、也不讓房務改。
  final bool urgent;

  /// 命中的判定規則說明，例如「VIP · 15:00 入住」；判定為一般件時為 null。
  final String? urgentRule;

  /// 同房、同類別、還沒結案的工務單；沒有時為 null。
  final RepairTicketSummary? duplicate;

  factory RepairTicketPrecheck.fromJson(Map<String, dynamic> json) {
    final judgement = json['urgentJudgement'] as Map<String, dynamic>?;
    final duplicate = json['duplicate'] as Map<String, dynamic>?;
    return RepairTicketPrecheck(
      urgent: judgement?['suggested'] as bool? ?? false,
      urgentRule: judgement?['rule'] as String?,
      duplicate:
          duplicate == null ? null : RepairTicketSummary.fromJson(duplicate),
    );
  }
}

/// 工務單摘要（契約 RepairTicketSummary）：任務詳情的同房工務單區與開單前
/// 檢查的重複提示共用這一份。
class RepairTicketSummary {
  const RepairTicketSummary({
    required this.taskId,
    required this.ticketNo,
    required this.roomNo,
    required this.category,
    required this.isUrgent,
    required this.state,
    required this.reporterName,
  });

  final String taskId;
  final String ticketNo;
  final String roomNo;
  final String category;
  final bool isUrgent;
  final String state;
  final String reporterName;

  factory RepairTicketSummary.fromJson(Map<String, dynamic> json) {
    return RepairTicketSummary(
      taskId: json['taskId'] as String,
      ticketNo: json['ticketNo'] as String,
      roomNo: json['roomNo'] as String? ?? '',
      category: json['category'] as String,
      isUrgent: json['isUrgent'] as bool? ?? false,
      state: json['state'] as String,
      reporterName: json['reporterName'] as String? ?? '',
    );
  }
}

/// `NextTask`：下一間推薦清單的一筆（只列 accepted，後端已排好順序）。
class NextTask {
  const NextTask({
    required this.taskId,
    required this.roomNo,
    required this.title,
    required this.type,
    required this.redoCount,
  });

  final String taskId;
  final String roomNo;
  final String title;

  /// cleaning｜amenity｜repair｜other，照契約 enum，不縮寫。
  final String type;
  final int redoCount;

  factory NextTask.fromJson(Map<String, dynamic> json) {
    return NextTask(
      taskId: json['taskId'] as String,
      roomNo: json['roomNo'] as String,
      title: json['title'] as String,
      type: json['type'] as String,
      redoCount: json['redoCount'] as int? ?? 0,
    );
  }
}

/// `DeptPool`：待認領池回應，items 已依後端定義的優先序排好。
class DeptPool {
  const DeptPool({
    required this.items,
    required this.counts,
    required this.activeStaffCount,
  });

  final List<PoolItem> items;

  /// 分類筆數，key 是 all／cleaning／amenity／repair／other。
  final Map<String, int> counts;

  /// 這個部門目前在職、可以搶單的員工數（待開通與停用的不算）。畫面靠它
  /// 分辨兩種都看不到單的情況：大於 0 而 [items] 是空的＝單都被接走了；
  /// 等於 0＝這個部門沒有人可以搶單。
  final int activeStaffCount;

  factory DeptPool.fromJson(Map<String, dynamic> json) {
    final itemsJson = json['items'] as List<dynamic>? ?? <dynamic>[];
    final countsJson = json['counts'] as Map<String, dynamic>? ?? <String, dynamic>{};
    return DeptPool(
      items: itemsJson
          .map((entry) => PoolItem.fromJson(entry as Map<String, dynamic>))
          .toList(),
      counts: countsJson.map((key, value) => MapEntry(key, value as int)),
      activeStaffCount: json['activeStaffCount'] as int? ?? 0,
    );
  }
}

/// `PoolItem`：池中一張單卡需要的欄位。
class PoolItem {
  const PoolItem({
    required this.taskId,
    required this.roomNo,
    required this.floor,
    required this.title,
    required this.category,
    required this.source,
    required this.urgent,
    required this.priorityNotify,
    required this.sameFloor,
    required this.createdAt,
    this.ticketNo,
    this.reminderStage,
    this.returnReason,
    this.handoverNote,
  });

  final String taskId;

  /// 只有工務單有值，例如 W-0412。
  final String? ticketNo;
  final String roomNo;
  final int floor;
  final String title;

  /// cleaning｜amenity｜repair｜other。
  final String category;

  /// guest_scan｜desk_forward｜manager_requeue｜leave_return｜
  /// shift_end_return｜inspection_return｜staff_report｜system。
  final String source;
  final bool urgent;
  final bool priorityNotify;
  final bool sameFloor;

  /// 未接提醒階段：staff_reminded｜manager_notified；還沒進入提醒為 null。
  final String? reminderStage;
  final String? returnReason;
  final String? handoverNote;
  final String createdAt;

  factory PoolItem.fromJson(Map<String, dynamic> json) {
    return PoolItem(
      taskId: json['taskId'] as String,
      ticketNo: json['ticketNo'] as String?,
      roomNo: json['roomNo'] as String,
      floor: json['floor'] as int,
      title: json['title'] as String,
      category: json['category'] as String,
      source: json['source'] as String,
      urgent: json['urgent'] as bool,
      priorityNotify: json['priorityNotify'] as bool,
      sameFloor: json['sameFloor'] as bool,
      reminderStage: json['reminderStage'] as String?,
      returnReason: json['returnReason'] as String?,
      handoverNote: json['handoverNote'] as String?,
      createdAt: json['createdAt'] as String,
    );
  }
}

/// 通知中心單筆通知（`specs/truth/contracts/components/schemas/通知中心.yaml`
/// 的 notifications schema；後端回應不帶 tenantId／recipientStaffId）。
class NotificationItem {
  const NotificationItem({
    required this.id,
    required this.event,
    required this.title,
    required this.target,
    required this.createdAt,
    this.body,
    this.readAt,
  });

  final String id;

  /// 這則通知是從哪一個推播事件落地的（shared.yaml 的 `PushPayload.event`，
  /// 十七種事件值）。通知中心靠它決定每一列的分類圖示與顏色——標題只是文案，
  /// 改一個字不該讓整批通知換顏色。沒見過的值照樣帶進來，由畫面決定怎麼畫。
  final String event;

  final String title;
  final String? body;

  /// 點開後的去處（shared.yaml 的 PushTarget）：`kind` 是 task｜pool｜home，
  /// kind=task 帶 `taskId`，kind=pool 可帶 `highlightTaskId`。
  final Map<String, dynamic> target;

  final DateTime createdAt;

  /// 已讀時間；未讀為 null——未讀藍點就看這個欄位。
  final DateTime? readAt;

  bool get isUnread => readAt == null;

  factory NotificationItem.fromJson(Map<String, dynamic> json) {
    final readAt = json['readAt'] as String?;
    return NotificationItem(
      id: json['id'] as String,
      // 契約把 event 列為必填，但舊版後端還沒帶這個欄位時，整份清單因為少一個
      // 欄位就載入失敗太過頭——缺值落空字串，畫面把它當成分不出類別的一般通知。
      event: json['event'] as String? ?? '',
      title: json['title'] as String,
      body: json['body'] as String?,
      target: json['target'] as Map<String, dynamic>? ?? <String, dynamic>{},
      createdAt: DateTime.parse(json['createdAt'] as String),
      readAt: readAt == null ? null : DateTime.parse(readAt),
    );
  }
}

/// `HandoverEntry`：任務頁交辦區的一則交辦。四個欄位全部來自後端——它已經把
/// 交班單、櫃檯備註、接手進度三個來源併成同一份清單，App 只負責照單呈現。
class HandoverEntry {
  const HandoverEntry({
    required this.kind,
    required this.author,
    required this.content,
    this.at,
  });

  /// 來源，照契約 enum：shift_handover｜desk_note｜requeue_progress｜manager_note。
  final String kind;

  /// 寫下這則交辦的人。
  final String author;

  /// 交辦內容。
  final String content;

  /// 寫下的時刻；交班單以外的來源不一定有。
  final DateTime? at;

  factory HandoverEntry.fromJson(Map<String, dynamic> json) {
    final at = json['at'] as String?;
    return HandoverEntry(
      kind: json['kind'] as String? ?? '',
      author: json['author'] as String? ?? '',
      content: json['content'] as String? ?? '',
      at: at == null ? null : DateTime.parse(at).toLocal(),
    );
  }
}

/// `KbSuggestion`：查一下的一則推薦問題。[scope] 是這個問題會查到哪一類資料
/// （例如「房間記錄」「SOP · 您剛剛跳過這項」），和問題本身一樣由後端給定。
class AskSuggestion {
  const AskSuggestion({required this.question, required this.scope});

  final String question;
  final String scope;

  factory AskSuggestion.fromJson(Map<String, dynamic> json) {
    return AskSuggestion(
      question: json['question'] as String? ?? '',
      scope: json['scope'] as String? ?? '',
    );
  }
}

/// `KbSource`：查一下回答的一筆出處。[excerpt] 是被引用資料的原文，
/// [updatedOn] 是 ISO 日期字串（`yyyy-MM-dd`）。
class AskSource {
  const AskSource({
    required this.kind,
    required this.title,
    required this.excerpt,
    required this.updatedOn,
  });

  /// 出處種類，照契約 enum：sop｜room_memory。
  final String kind;

  final String title;
  final String excerpt;
  final String updatedOn;

  factory AskSource.fromJson(Map<String, dynamic> json) {
    return AskSource(
      kind: json['kind'] as String? ?? '',
      title: json['title'] as String? ?? '',
      excerpt: json['excerpt'] as String? ?? '',
      updatedOn: json['updatedOn'] as String? ?? '',
    );
  }
}

/// `KbAnswer`：查一下的一次回答。查不到時 [found] 是 false、[answer] 是 null、
/// [sources] 是空的，只有 [notFoundMessage] 有文字——畫面要照這個分野決定
/// 「有沒有東西可顯示」，不能把 null 當成空字串畫一個空的回答區。
class AskAnswer {
  const AskAnswer({
    required this.answerId,
    required this.found,
    required this.sources,
    this.answer,
    this.notFoundMessage,
  });

  /// 稽核紀錄 id，之後的回饋與轉問主管都拿它當位址。
  final String answerId;

  final bool found;
  final String? answer;
  final String? notFoundMessage;
  final List<AskSource> sources;

  factory AskAnswer.fromJson(Map<String, dynamic> json) {
    return AskAnswer(
      answerId: json['answerId'] as String? ?? '',
      found: json['found'] as bool? ?? false,
      answer: json['answer'] as String?,
      notFoundMessage: json['notFoundMessage'] as String?,
      sources: (json['sources'] as List<dynamic>? ?? <dynamic>[])
          .map((entry) => AskSource.fromJson(entry as Map<String, dynamic>))
          .toList(),
    );
  }
}

/// `ReturnInfo`：查房退回後要給接手的人看的那段說明。
///
/// 後端把「不合格項」「第幾次重做」「為什麼換人」合成一句 [summary]，依序以
/// ` · ` 串接，第一段是標題（查房退回：{SOP 項全名}不合格），其餘合起來是原因
/// （第 N 次重做，換人時再接換人原因）。任務頁要把這兩段分行顯示，所以在這裡
/// 就地切開——切法與後端組字的規則同一套，App 不自己重組任何一段文字。
class ReturnInfo {
  const ReturnInfo({required this.summary});

  final String summary;

  /// 標題段：`查房退回：換床單鋪床不合格`。
  String get title => summary.split(' · ').first;

  /// 原因段：`第 1 次重做 · 原清房者小林已下班，改由您接手`。原因本身內部也用
  /// ` · ` 接，所以只切第一刀，不把原因再拆開。
  String get reason {
    final cut = summary.indexOf(' · ');
    return cut < 0 ? '' : summary.substring(cut + 3);
  }

  static ReturnInfo? fromJson(Object? json) {
    if (json is! Map<String, dynamic>) {
      return null;
    }
    final summary = json['summary'] as String?;
    if (summary == null || summary.isEmpty) {
      return null;
    }
    return ReturnInfo(summary: summary);
  }
}

/// `TaskDetail`：任務頁需要的最小欄位集（契約還有 SOP 勾記等欄位，等對應畫面
/// 實作時再補進來，不預先鋪沒人讀的欄位）。
class TaskDetail {
  const TaskDetail({
    required this.id,
    required this.state,
    this.type = 'cleaning',
    this.inspection,
    this.roomId,
    this.roomNo,
    this.title,
    this.note,
    this.inspectionWaived = false,
    this.estMin,
    this.elapsedMinutes = 0,
    this.checkedSopItems = const <String>[],
    this.skippedSopItems = const <String>[],
    this.holdReason,
    this.deferredBySelf = false,
    this.handovers = const <HandoverEntry>[],
    this.handoverRead = false,
    this.returnInfo,
  });

  final String id;

  /// 任務狀態，照契約 enum：triage｜pending_review｜rejected｜pool｜
  /// accepted｜doing｜done｜inspected｜deferred｜interrupted｜cancelled。
  final String state;

  /// 任務類別，照契約 enum：cleaning｜amenity｜repair｜other。狀態文字要靠它
  /// 分流（`done` 在打掃類是「待查房」，其他類是「已送達」）。
  final String type;

  /// 打掃任務的查房結果：passed｜waived｜failed；還沒查房時為 null。
  final String? inspection;

  /// 房間 id。同房工務單要靠它向 `GET /repair-tickets?roomId=` 取。
  final String? roomId;
  final String? roomNo;
  final String? title;
  final String? note;

  /// 開工那一刻鎖定的免查房旗標。打掃類鎖定免查時，完成就直接結案成
  /// `inspected`／`waived`，不經「待查房」——步驟條最後一段因此是「免查房」
  /// 而不是「查房」。
  final bool inspectionWaived;

  /// 預估工時（分鐘）；沒有估時的任務為 null。超時提示與完成慶祝頁的
  /// 「預估 N 分」都讀它。
  final int? estMin;

  /// 已做分鐘。
  final int elapsedMinutes;

  /// 已勾選的 SOP 項（`sop_items.id`）。
  final List<String> checkedSopItems;

  /// 已跳過的 SOP 項（`sop_items.id`）。跳過一樣計入 SOP 進度的分子。
  final List<String> skippedSopItems;

  /// 停住原因，例如「等工務：水電」或「工務已修好：水電」。
  final String? holdReason;

  /// 延後時是否為本人延後（有負責人）。主管延後為 false——那種單的負責人
  /// 已經被拿掉，任務頁不給「開始」。
  final bool deferredBySelf;

  /// 合併後的交辦清單，順序照後端給的順序。
  final List<HandoverEntry> handovers;

  /// 這位員工看過這張任務的交辦了沒有。開工那一刻後端會自己記下已讀。
  final bool handoverRead;

  /// 查房退回資訊；不在退回重做中的任務為 null。
  final ReturnInfo? returnInfo;

  factory TaskDetail.fromJson(Map<String, dynamic> json) {
    return TaskDetail(
      id: json['id'] as String,
      state: json['state'] as String,
      type: json['type'] as String? ?? 'cleaning',
      inspection: json['inspection'] as String?,
      inspectionWaived: json['inspectionWaived'] as bool? ?? false,
      roomId: json['roomId'] as String?,
      roomNo: json['roomNo'] as String?,
      title: json['title'] as String?,
      note: json['note'] as String?,
      estMin: json['estMin'] as int?,
      elapsedMinutes: json['elapsedMinutes'] as int? ?? 0,
      checkedSopItems: _stringList(json['checkedSopItems']),
      skippedSopItems: _stringList(json['skippedSopItems']),
      holdReason: json['holdReason'] as String?,
      deferredBySelf: json['deferredBySelf'] as bool? ?? false,
      handovers: (json['handovers'] as List<dynamic>? ?? <dynamic>[])
          .whereType<Map<String, dynamic>>()
          .map(HandoverEntry.fromJson)
          .toList(),
      handoverRead: json['handoverRead'] as bool? ?? false,
      returnInfo: ReturnInfo.fromJson(json['returnInfo']),
    );
  }
}

/// 契約 `SopItem`：打掃 SOP 項與查房項目同構共表，差別在 [appliesTo]。
class SopItem {
  const SopItem({
    required this.id,
    required this.appliesTo,
    required this.itemKey,
    required this.label,
    this.sortOrder = 0,
    this.active = true,
  });

  final String id;

  /// `cleaning`（打掃 SOP）或 `inspection`（查房項目）。
  final String appliesTo;
  final String itemKey;
  final String label;
  final int sortOrder;
  final bool active;

  factory SopItem.fromJson(Map<String, dynamic> json) {
    return SopItem(
      id: json['id'] as String,
      appliesTo: json['appliesTo'] as String? ?? 'cleaning',
      itemKey: json['itemKey'] as String? ?? '',
      label: json['label'] as String? ?? '',
      sortOrder: json['sortOrder'] as int? ?? 0,
      active: json['active'] as bool? ?? true,
    );
  }
}

/// 契約裡的 id 陣列欄位（`checkedSopItems`／`skippedSopItems`）解析成字串清單。
/// 欄位缺席或為 null 時當成空清單，不讓畫面因為後端少回一欄就整頁打不開。
List<String> _stringList(Object? value) {
  if (value is! List) {
    return const <String>[];
  }
  return value.whereType<String>().toList(growable: false);
}
