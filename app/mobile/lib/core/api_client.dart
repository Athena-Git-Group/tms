import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

class ApiClient {
  ApiClient({String? baseUrl}) : baseUrl = baseUrl ?? _defaultBaseUrl;

  static const String _defaultBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080/api/v1',
  );

  static ApiClient instance = ApiClient();

  static int dropWriteResponseCount = 0;

  static String dropWriteResponsePathPrefix = '';

  final String baseUrl;

  String? _token;

  LoginResult? _session;

  bool get isLoggedIn => _token != null;

  LoginResult? get session => _session;

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

  MyToday? get lastKnownToday => _lastKnownToday;

  MyToday? _lastKnownToday;

  Future<MyToday> getMyToday() async {
    final json = await _requestJson('GET', '/me/today');
    final today = MyToday.fromJson(json as Map<String, dynamic>);
    _lastKnownToday = today;
    return today;
  }

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

  Future<List<TaskSummary>> listTasks() async {
    final json = await _requestJson('GET', '/tasks');
    return (json as List<dynamic>? ?? <dynamic>[])
        .map((entry) => TaskSummary.fromJson(entry as Map<String, dynamic>))
        .toList();
  }

  Future<void> claimTask(String taskId) async {
    await _requestJson('POST', '/tasks/$taskId/claim', idempotent: true);
  }

  Future<TaskCompletion?> completeTask(
    String taskId, {
    List<String>? photoIds,
    String? resolutionType,
    String? idempotencyKey,
  }) async {
    final json = await _requestJson(
      'POST',
      '/tasks/$taskId/complete',
      idempotent: true,
      idempotencyKey: idempotencyKey,
      body: {
        if (photoIds != null) 'photoIds': photoIds,
        if (resolutionType != null) 'resolutionType': resolutionType,
      },
    );
    return json is Map<String, dynamic> ? TaskCompletion.fromJson(json) : null;
  }

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

  Future<ManagerQueues> getManagerQueues() async {
    final json = await _requestJson('GET', '/me/manager-queues');
    return ManagerQueues.fromJson(json as Map<String, dynamic>);
  }

  Future<InspectionOutcome> createInspection({
    required String taskId,
    required List<InspectionItemJudgement> items,
    required String idempotencyKey,
  }) async {
    final json = await _requestJson(
      'POST',
      '/inspections',
      idempotent: true,
      idempotencyKey: idempotencyKey,
      body: {
        'taskId': taskId,
        'items': [for (final item in items) item.toJson()],
      },
    );
    return InspectionOutcome.fromJson(json as Map<String, dynamic>);
  }

  Future<RepairTicketDetail> readRepairTicket(String taskId) async {
    final json = await _requestJson('GET', '/repair-tickets/$taskId');
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

  Future<RepairVerificationOutcome> verifyRepairTicket({
    required String taskId,
    required String result,
    String? reason,
    required String idempotencyKey,
  }) async {
    final json = await _requestJson(
      'POST',
      '/repair-tickets/$taskId/verify',
      idempotent: true,
      idempotencyKey: idempotencyKey,
      body: {
        'result': result,
        if (reason != null) 'reason': reason,
      },
    );
    return RepairVerificationOutcome.fromJson(json as Map<String, dynamic>);
  }

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

  Future<RepairTicketDetail> withdrawRepairTicket(String taskId) async {
    final json = await _requestJson(
      'POST',
      '/repair-tickets/$taskId/withdraw',
      idempotent: true,
    );
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

  Future<RepairTicketDetail> resubmitRepairTicket(String taskId) async {
    final json = await _requestJson(
      'POST',
      '/repair-tickets/$taskId/resubmit',
      idempotent: true,
    );
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

  Future<RepairTicketDetail> releaseRepairTicketToPool(String taskId) async {
    final json = await _requestJson(
      'POST',
      '/repair-tickets/$taskId/release-to-pool',
      idempotent: true,
    );
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

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

  Future<void> uploadRepairPhotoContent(String photoId, List<int> jpegBytes) async {
    await _requestJson(
      'PUT',
      '/repair-photos/$photoId/content',
      binaryBody: jpegBytes,
    );
  }

  Future<RepairTicketDetail> createRepairTicket({
    String? sourceTaskId,
    String? roomId,
    bool isPublicArea = false,
    required String category,
    String? description,
    required List<String> photoIds,
    bool interruptSourceTask = false,
    String? assigneeId,
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
        if (description != null && description.isNotEmpty)
          'description': description,
        'photoIds': photoIds,
        if (interruptSourceTask) 'interruptSourceTask': true,
        'assigneeId': ?assigneeId,
      },
    );
    return RepairTicketDetail.fromJson(json as Map<String, dynamic>);
  }

  Future<List<RoomSummary>> listRooms() async {
    final json = await _requestJson('GET', '/rooms') as List<Object?>;
    return [
      for (final row in json)
        RoomSummary.fromJson(row! as Map<String, dynamic>),
    ];
  }

  Future<TaskDetail> startTask(String taskId) async {
    final json = await _requestJson('POST', '/tasks/$taskId/start', idempotent: true);
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  Future<TaskDetail> deferTask(String taskId, String reason) async {
    final json = await _requestJson(
      'POST',
      '/tasks/$taskId/defer',
      body: {'reason': reason},
      idempotent: true,
    );
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  Future<TaskDetail> interruptTask(String taskId, String reason) async {
    final json = await _requestJson(
      'POST',
      '/tasks/$taskId/interrupt',
      body: {'reason': reason},
      idempotent: true,
    );
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  Future<TaskDetail> resumeTask(String taskId) async {
    final json = await _requestJson(
      'POST',
      '/tasks/$taskId/resume',
      idempotent: true,
    );
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  Future<TaskDetail> getTask(String taskId) async {
    final json = await _requestJson('GET', '/tasks/$taskId');
    return TaskDetail.fromJson(json as Map<String, dynamic>);
  }

  Future<List<SopItem>> listSopItems() async {
    final json = await _requestJson('GET', '/sop-items');
    return (json as List<dynamic>? ?? <dynamic>[])
        .map((entry) => SopItem.fromJson(entry as Map<String, dynamic>))
        .toList();
  }

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

  Future<void> submitAskFeedback({
    required String answerId,
    required bool helpful,
  }) async {
    await _requestJson(
      'POST',
      '/room-memories/answers/$answerId/feedback',
      body: {'helpful': helpful},
      idempotent: true,
    );
  }

  Future<void> escalateAskQuestion(String answerId) async {
    await _requestJson(
      'POST',
      '/room-memories/answers/$answerId/escalate',
      idempotent: true,
    );
  }

  Future<List<NotificationItem>> listNotifications() async {
    final json = await _requestJson('GET', '/notifications');
    return _asNotificationList(json);
  }

  Future<NotificationItem> markNotificationRead(String notificationId) async {
    final json = await _requestJson('POST', '/notifications/$notificationId/read');
    return NotificationItem.fromJson(json as Map<String, dynamic>);
  }

  Future<List<NotificationItem>> markAllNotificationsRead() async {
    final json = await _requestJson('POST', '/notifications/read-all');
    return _asNotificationList(json);
  }

  Future<void> registerPushToken({
    required String deviceFingerprint,
    required String pushToken,
    required String platform,
  }) async {
    await _requestJson(
      'PUT',
      '/me/push-token',
      body: {
        'deviceFingerprint': deviceFingerprint,
        'pushToken': pushToken,
        'platform': platform,
      },
    );
  }

  Future<void> unregisterPushToken({required String deviceFingerprint}) async {
    await _requestJson(
      'DELETE',
      '/me/push-token',
      body: {'deviceFingerprint': deviceFingerprint},
    );
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
      if (idempotent &&
          dropWriteResponseCount > 0 &&
          path.startsWith(dropWriteResponsePathPrefix)) {
        dropWriteResponseCount--;
        throw TimeoutException('送出後沒有收到回應：$method $path');
      }
      return text.isEmpty ? null : jsonDecode(text);
    } finally {
      client.close();
    }
  }

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

class ApiException implements Exception {
  const ApiException({required this.statusCode, required this.body});

  final int statusCode;
  final String body;

  String? get errorCode => _bodyJson()?['code'] as String?;

  String? get errorMessage => _bodyJson()?['message'] as String?;

  Map<String, dynamic>? get errorContext =>
      _bodyJson()?['context'] as Map<String, dynamic>?;

  int? get lockoutRemainingMinutes => statusCode == 429
      ? (errorContext?['remainingMinutes'] as num?)?.toInt()
      : null;

  List<InspectionItemRef>? get inspectionCurrentItems {
    if (errorCode != 'inspection_items_mismatch') {
      return null;
    }
    final raw = errorContext?['currentItems'];
    if (raw is! List<dynamic>) {
      return null;
    }
    return [
      for (final item in raw)
        InspectionItemRef.fromJson(item as Map<String, dynamic>),
    ];
  }

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
    this.engineeringSummary,
    this.shift,
    this.floors,
    this.poolBadgeCount = 0,
    this.unreadNotificationBadgeCount = 0,
  });

  final int cleaningDone;
  final int cleaningTotal;
  final int inspectedCount;
  final int completedCount;
  final int adHocCompleted;
  final int remainingMinutes;

  final List<TodayTask> todayTasks;

  final List<NextTask> nextTasks;

  final EngineeringSummary? engineeringSummary;

  final String? shift;

  final List<int>? floors;

  final int poolBadgeCount;

  final int unreadNotificationBadgeCount;

  factory MyToday.fromJson(Map<String, dynamic> json) {
    final todayTasksJson = json['todayTasks'] as List<dynamic>? ?? <dynamic>[];
    final nextTasksJson = json['nextTasks'] as List<dynamic>? ?? <dynamic>[];
    final engineeringJson = json['engineeringSummary'] as Map<String, dynamic>?;
    final floorsJson = json['floors'] as List<dynamic>?;
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
      engineeringSummary: engineeringJson == null
          ? null
          : EngineeringSummary.fromJson(engineeringJson),
      shift: json['shift'] as String?,
      floors: floorsJson?.map((entry) => entry as int).toList(),
      poolBadgeCount: json['poolBadgeCount'] as int? ?? 0,
      unreadNotificationBadgeCount:
          json['unreadNotificationBadgeCount'] as int? ?? 0,
    );
  }
}

class EngineeringSummary {
  const EngineeringSummary({
    required this.openCount,
    required this.completedToday,
    required this.urgentCount,
    required this.tickets,
    this.plannedToday,
    this.maintenanceDone,
    this.maintenanceTotal,
  });

  static const EngineeringSummary none = EngineeringSummary(
    openCount: 0,
    completedToday: 0,
    urgentCount: 0,
    tickets: <EngineeringTicket>[],
  );

  final int openCount;

  final int completedToday;

  final int urgentCount;

  final List<EngineeringTicket> tickets;

  final int? plannedToday;

  final int? maintenanceDone;

  final int? maintenanceTotal;

  factory EngineeringSummary.fromJson(Map<String, dynamic> json) {
    final ticketsJson = json['tickets'] as List<dynamic>? ?? <dynamic>[];
    return EngineeringSummary(
      openCount: json['openCount'] as int,
      completedToday: json['completedToday'] as int,
      urgentCount: json['urgentCount'] as int,
      tickets: ticketsJson
          .map((entry) => EngineeringTicket.fromJson(entry as Map<String, dynamic>))
          .toList(),
      plannedToday: json['plannedToday'] as int?,
      maintenanceDone: json['maintenanceDone'] as int?,
      maintenanceTotal: json['maintenanceTotal'] as int?,
    );
  }
}

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
    this.workedMinutes,
    this.verificationReturnReason,
  });

  final String? ticketNo;
  final String taskId;

  final String? roomNo;

  final bool isPublicArea;
  final String title;

  final String state;
  final bool isUrgent;

  final String? source;

  final int? workedMinutes;

  final String? verificationReturnReason;

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
      workedMinutes: json['workedMinutes'] as int?,
      verificationReturnReason: json['verificationReturnReason'] as String?,
    );
  }
}

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

  final String type;

  final String state;

  final int redoCount;

  final int? estMin;

  final int workedMinutes;

  final String? holdReason;

  final String? inspection;

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

class TaskSummary {
  const TaskSummary({
    required this.id,
    required this.type,
    required this.state,
    this.assigneeId,
  });

  final String id;

  final String type;

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

class RepairTicketDetail {
  const RepairTicketDetail({
    required this.taskId,
    required this.ticketNo,
    required this.roomNo,
    required this.category,
    required this.isUrgent,
    required this.state,
    this.isPublicArea = false,
    this.origin = 'reported',
    this.reporterName,
    this.createdAt,
    this.description,
    this.assigneeName,
    this.urgentRule,
    this.verifiedBy,
    this.verifiedAt,
    this.resolutionNote,
    this.resolutionType,
    this.doneAt,
    this.reviewRequestedAt,
    this.rejectReason,
    this.urgentDowngradeReason,
    this.originTicketNo,
    this.photos = const [],
    this.photoCount = 0,
    this.completionPhotos = const [],
    this.lastVerifyFailedReason,
    this.lastVerifyFailedBy,
    this.lastVerifyFailedAt,
    this.viewerIsAssignee = false,
  });

  final String taskId;

  final String ticketNo;

  final String? roomNo;

  final bool isPublicArea;

  final String category;
  final bool isUrgent;

  final String state;

  final String origin;

  final String? reporterName;

  String get whereText => roomNo ?? '公共區域';

  String get reporterNameText => reporterName ?? '—';

  final DateTime? createdAt;

  final String? description;

  final String? assigneeName;

  final String? urgentRule;

  final String? verifiedBy;
  final String? verifiedAt;
  final String? resolutionNote;

  final String? resolutionType;

  final String? doneAt;

  final String? reviewRequestedAt;
  final String? rejectReason;
  final String? urgentDowngradeReason;
  final String? originTicketNo;

  final List<RepairTicketPhoto> photos;

  final int photoCount;

  final List<RepairTicketPhoto> completionPhotos;

  final String? lastVerifyFailedReason;
  final String? lastVerifyFailedBy;
  final String? lastVerifyFailedAt;

  final bool viewerIsAssignee;

  factory RepairTicketDetail.fromJson(Map<String, dynamic> json) {
    final roomNo = json['roomNo'] as String?;
    return RepairTicketDetail(
      taskId: json['taskId'] as String,
      ticketNo: json['ticketNo'] as String,
      roomNo: roomNo == '公共區域' ? null : roomNo,
      isPublicArea: json['isPublicArea'] as bool? ?? roomNo == '公共區域',
      category: json['category'] as String,
      isUrgent: json['isUrgent'] as bool? ?? false,
      state: json['state'] as String,
      origin: json['origin'] as String? ?? 'reported',
      reporterName: json['reporterName'] as String?,
      createdAt: DateTime.tryParse(json['createdAt'] as String? ?? ''),
      description: json['description'] as String?,
      assigneeName: json['assigneeName'] as String?,
      urgentRule:
          (json['urgentJudgement'] as Map<String, dynamic>?)?['rule'] as String?,
      verifiedBy: json['verifiedBy'] as String?,
      verifiedAt: json['verifiedAt'] as String?,
      resolutionNote: json['resolutionNote'] as String?,
      resolutionType: json['resolutionType'] as String?,
      doneAt: json['doneAt'] as String?,
      reviewRequestedAt: json['reviewRequestedAt'] as String?,
      rejectReason: json['rejectReason'] as String?,
      urgentDowngradeReason: json['urgentDowngradeReason'] as String?,
      originTicketNo: json['originTicketNo'] as String?,
      photos: [
        for (final photo in (json['photos'] as List<dynamic>? ?? const []))
          RepairTicketPhoto.fromJson(photo as Map<String, dynamic>),
      ],
      photoCount:
          (json['photoCount'] as num?)?.toInt() ??
          (json['photos'] as List<dynamic>? ?? const []).length,
      completionPhotos: [
        for (final photo
            in (json['completionPhotos'] as List<dynamic>? ?? const []))
          RepairTicketPhoto.fromJson(photo as Map<String, dynamic>),
      ],
      lastVerifyFailedReason: json['lastVerifyFailedReason'] as String?,
      lastVerifyFailedBy: json['lastVerifyFailedBy'] as String?,
      lastVerifyFailedAt: json['lastVerifyFailedAt'] as String?,
      viewerIsAssignee: json['viewerIsAssignee'] as bool? ?? false,
    );
  }
}

class RepairTicketPhoto {
  const RepairTicketPhoto({required this.id, required this.status});

  final String id;

  final String status;

  factory RepairTicketPhoto.fromJson(Map<String, dynamic> json) {
    return RepairTicketPhoto(
      id: json['id'] as String,
      status: json['status'] as String,
    );
  }
}

class RepairTicketPrecheck {
  const RepairTicketPrecheck({
    required this.urgent,
    this.urgentRule,
    this.duplicate,
  });

  final bool urgent;

  final String? urgentRule;

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

class RepairTicketSummary {
  const RepairTicketSummary({
    required this.taskId,
    required this.ticketNo,
    required this.roomNo,
    required this.category,
    required this.isUrgent,
    required this.state,
    this.isPublicArea = false,
    this.reporterName,
  });

  final String taskId;
  final String ticketNo;

  final String? roomNo;
  final bool isPublicArea;
  final String category;
  final bool isUrgent;
  final String state;

  final String? reporterName;

  factory RepairTicketSummary.fromJson(Map<String, dynamic> json) {
    final roomNo = json['roomNo'] as String?;
    return RepairTicketSummary(
      taskId: json['taskId'] as String,
      ticketNo: json['ticketNo'] as String,
      roomNo: roomNo == '公共區域' ? null : roomNo,
      isPublicArea: json['isPublicArea'] as bool? ?? roomNo == '公共區域',
      category: json['category'] as String,
      isUrgent: json['isUrgent'] as bool? ?? false,
      state: json['state'] as String,
      reporterName: json['reporterName'] as String?,
    );
  }
}

class ManagerQueues {
  const ManagerQueues({
    required this.counts,
    required this.inspection,
    required this.review,
    required this.verify,
  });

  final ManagerQueueCounts counts;
  final List<InspectionQueueItem> inspection;
  final List<ReviewQueueItem> review;
  final List<VerifyQueueItem> verify;

  factory ManagerQueues.fromJson(Map<String, dynamic> json) {
    return ManagerQueues(
      counts: ManagerQueueCounts.fromJson(
        json['counts'] as Map<String, dynamic>? ?? const {},
      ),
      inspection: [
        for (final item in (json['inspection'] as List<dynamic>? ?? const []))
          InspectionQueueItem.fromJson(item as Map<String, dynamic>),
      ],
      review: [
        for (final item in (json['review'] as List<dynamic>? ?? const []))
          ReviewQueueItem.fromJson(item as Map<String, dynamic>),
      ],
      verify: [
        for (final item in (json['verify'] as List<dynamic>? ?? const []))
          VerifyQueueItem.fromJson(item as Map<String, dynamic>),
      ],
    );
  }
}

class ManagerQueueCounts {
  const ManagerQueueCounts({
    required this.inspection,
    required this.review,
    required this.verify,
  });

  final int inspection;
  final int review;
  final int verify;

  factory ManagerQueueCounts.fromJson(Map<String, dynamic> json) {
    return ManagerQueueCounts(
      inspection: (json['inspection'] as num?)?.toInt() ?? 0,
      review: (json['review'] as num?)?.toInt() ?? 0,
      verify: (json['verify'] as num?)?.toInt() ?? 0,
    );
  }
}

class InspectionQueueItem {
  const InspectionQueueItem({
    required this.taskId,
    required this.roomNo,
    required this.isPublicArea,
    required this.title,
    required this.waitingSince,
    required this.waitingSinceIsToday,
    required this.cleanerName,
    required this.redoCount,
  });

  final String taskId;

  final String? roomNo;
  final bool isPublicArea;
  final String title;

  final String waitingSince;

  final bool waitingSinceIsToday;

  final String cleanerName;

  final int redoCount;

  factory InspectionQueueItem.fromJson(Map<String, dynamic> json) {
    return InspectionQueueItem(
      taskId: json['taskId'] as String,
      roomNo: json['roomNo'] as String?,
      isPublicArea: json['isPublicArea'] as bool? ?? false,
      title: json['title'] as String? ?? '',
      waitingSince: json['waitingSince'] as String,
      waitingSinceIsToday: json['waitingSinceIsToday'] as bool? ?? false,
      cleanerName: json['cleanerName'] as String? ?? '',
      redoCount: (json['redoCount'] as num?)?.toInt() ?? 0,
    );
  }
}

class ReviewQueueItem {
  const ReviewQueueItem({
    required this.taskId,
    required this.roomNo,
    required this.isPublicArea,
    required this.title,
    required this.waitingSince,
    required this.waitingSinceIsToday,
    required this.ticketNo,
    required this.category,
    required this.reporterName,
    this.description,
    this.photoCount = 0,
  });

  final String taskId;
  final String? roomNo;
  final bool isPublicArea;
  final String title;

  final String waitingSince;
  final bool waitingSinceIsToday;
  final String ticketNo;
  final String category;

  final String? description;
  final String reporterName;

  final int photoCount;

  factory ReviewQueueItem.fromJson(Map<String, dynamic> json) {
    return ReviewQueueItem(
      taskId: json['taskId'] as String,
      roomNo: json['roomNo'] as String?,
      isPublicArea: json['isPublicArea'] as bool? ?? false,
      title: json['title'] as String? ?? '',
      waitingSince: json['waitingSince'] as String,
      waitingSinceIsToday: json['waitingSinceIsToday'] as bool? ?? false,
      ticketNo: json['ticketNo'] as String,
      category: json['category'] as String,
      description: json['description'] as String?,
      reporterName: json['reporterName'] as String? ?? '',
      photoCount: (json['photoCount'] as num?)?.toInt() ?? 0,
    );
  }
}

class VerifyQueueItem {
  const VerifyQueueItem({
    required this.taskId,
    required this.roomNo,
    required this.isPublicArea,
    required this.title,
    required this.waitingSince,
    required this.waitingSinceIsToday,
    required this.ticketNo,
    required this.category,
    required this.origin,
    required this.resolutionType,
    this.description,
    this.reporterName,
    this.assigneeName,
    this.wasVerificationFailed = false,
  });

  final String taskId;

  final String? roomNo;
  final bool isPublicArea;
  final String title;

  final String waitingSince;
  final bool waitingSinceIsToday;
  final String ticketNo;
  final String category;
  final String? description;

  final String? reporterName;

  final String? assigneeName;

  final String origin;

  final String? resolutionType;

  final bool wasVerificationFailed;

  factory VerifyQueueItem.fromJson(Map<String, dynamic> json) {
    return VerifyQueueItem(
      taskId: json['taskId'] as String,
      roomNo: json['roomNo'] as String?,
      isPublicArea: json['isPublicArea'] as bool? ?? false,
      title: json['title'] as String? ?? '',
      waitingSince: json['waitingSince'] as String,
      waitingSinceIsToday: json['waitingSinceIsToday'] as bool? ?? false,
      ticketNo: json['ticketNo'] as String,
      category: json['category'] as String,
      description: json['description'] as String?,
      reporterName: json['reporterName'] as String?,
      assigneeName: json['assigneeName'] as String?,
      origin: json['origin'] as String? ?? 'reported',
      resolutionType: json['resolutionType'] as String?,
      wasVerificationFailed: json['wasVerificationFailed'] as bool? ?? false,
    );
  }
}

class RepairVerificationOutcome {
  const RepairVerificationOutcome({
    required this.taskId,
    required this.ticketNo,
    required this.isPublicArea,
    required this.result,
    required this.destination,
    required this.decidedBy,
    required this.decidedAt,
    this.roomNo,
    this.assigneeName,
    this.reason,
  });

  final String taskId;
  final String ticketNo;

  final String? roomNo;
  final bool isPublicArea;

  final String result;

  final String destination;

  final String? assigneeName;

  final String? reason;

  final String decidedBy;
  final String decidedAt;

  factory RepairVerificationOutcome.fromJson(Map<String, dynamic> json) {
    return RepairVerificationOutcome(
      taskId: json['taskId'] as String,
      ticketNo: json['ticketNo'] as String,
      roomNo: json['roomNo'] as String?,
      isPublicArea: json['isPublicArea'] as bool? ?? false,
      result: json['result'] as String,
      destination: json['destination'] as String,
      assigneeName: json['assigneeName'] as String?,
      reason: json['reason'] as String?,
      decidedBy: json['decidedBy'] as String,
      decidedAt: json['decidedAt'] as String,
    );
  }
}

class InspectionItemJudgement {
  const InspectionItemJudgement({required this.key, required this.pass});

  final String key;
  final bool pass;

  Map<String, dynamic> toJson() => {'key': key, 'pass': pass};
}

class InspectionItemRef {
  const InspectionItemRef({
    required this.key,
    required this.label,
    required this.sortOrder,
  });

  final String key;
  final String label;
  final int sortOrder;

  factory InspectionItemRef.fromJson(Map<String, dynamic> json) {
    return InspectionItemRef(
      key: json['key'] as String,
      label: json['label'] as String? ?? '',
      sortOrder: (json['sortOrder'] as num?)?.toInt() ?? 0,
    );
  }
}

class InspectionOutcome {
  const InspectionOutcome({
    required this.inspectionId,
    required this.taskId,
    required this.roomNo,
    required this.result,
    required this.destination,
    required this.failedItems,
    required this.createdAt,
    this.cleanerName,
  });

  final String inspectionId;
  final String taskId;
  final String roomNo;

  final String result;

  final String destination;

  final String? cleanerName;

  final List<InspectionFailedItem> failedItems;
  final String createdAt;

  factory InspectionOutcome.fromJson(Map<String, dynamic> json) {
    return InspectionOutcome(
      inspectionId: json['inspectionId'] as String,
      taskId: json['taskId'] as String,
      roomNo: json['roomNo'] as String? ?? '',
      result: json['result'] as String,
      destination: json['destination'] as String,
      cleanerName: json['cleanerName'] as String?,
      failedItems: [
        for (final item in (json['failedItems'] as List<dynamic>? ?? const []))
          InspectionFailedItem.fromJson(item as Map<String, dynamic>),
      ],
      createdAt: json['createdAt'] as String? ?? '',
    );
  }
}

class InspectionFailedItem {
  const InspectionFailedItem({required this.key, required this.label});

  final String key;
  final String label;

  factory InspectionFailedItem.fromJson(Map<String, dynamic> json) {
    return InspectionFailedItem(
      key: json['key'] as String,
      label: json['label'] as String? ?? '',
    );
  }
}

class RoomSummary {
  const RoomSummary({required this.id, required this.roomNo});

  final String id;

  final String roomNo;

  factory RoomSummary.fromJson(Map<String, dynamic> json) {
    return RoomSummary(
      id: json['id'] as String,
      roomNo: json['roomNo'] as String? ?? '',
    );
  }
}

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

class DeptPool {
  const DeptPool({
    required this.items,
    required this.counts,
    required this.activeStaffCount,
  });

  final List<PoolItem> items;

  final Map<String, int> counts;

  final int activeStaffCount;

  factory DeptPool.fromJson(Map<String, dynamic> json) {
    final itemsJson = json['items'] as List<dynamic>? ?? <dynamic>[];
    final countsJson = json['counts'] as Map<String, dynamic>? ?? <String, dynamic>{};
    return DeptPool(
      items: itemsJson
          .map((entry) => PoolItem.fromJson(entry as Map<String, dynamic>))
          .toList(),
      counts: <String, int>{
        for (final entry in countsJson.entries)
          if (entry.value is int) entry.key: entry.value as int,
      },
      activeStaffCount: json['activeStaffCount'] as int? ?? 0,
    );
  }
}

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
    this.waitedMinutes,
    this.verificationReturnReason,
  });

  final String taskId;

  final String? ticketNo;
  final String roomNo;
  final int floor;
  final String title;

  final String category;

  final String source;
  final bool urgent;
  final bool priorityNotify;
  final bool sameFloor;

  final String? reminderStage;
  final String? returnReason;
  final String? handoverNote;
  final String createdAt;

  final int? waitedMinutes;

  final String? verificationReturnReason;

  static const String publicAreaRoomNo = '公共區域';

  bool get isPublicArea => roomNo == publicAreaRoomNo;

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
      waitedMinutes: json['waitedMinutes'] as int?,
      verificationReturnReason: json['verificationReturnReason'] as String?,
    );
  }
}

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

  final String event;

  final String title;
  final String? body;

  final Map<String, dynamic> target;

  final DateTime createdAt;

  final DateTime? readAt;

  bool get isUnread => readAt == null;

  factory NotificationItem.fromJson(Map<String, dynamic> json) {
    final readAt = json['readAt'] as String?;
    return NotificationItem(
      id: json['id'] as String,
      event: json['event'] as String? ?? '',
      title: json['title'] as String,
      body: json['body'] as String?,
      target: json['target'] as Map<String, dynamic>? ?? <String, dynamic>{},
      createdAt: DateTime.parse(json['createdAt'] as String),
      readAt: readAt == null ? null : DateTime.parse(readAt),
    );
  }
}

class HandoverEntry {
  const HandoverEntry({
    required this.kind,
    required this.author,
    required this.content,
    this.at,
  });

  final String kind;

  final String author;

  final String content;

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

class AskSource {
  const AskSource({
    required this.kind,
    required this.title,
    required this.excerpt,
    required this.updatedOn,
  });

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

class AskAnswer {
  const AskAnswer({
    required this.answerId,
    required this.found,
    required this.sources,
    this.answer,
    this.notFoundMessage,
  });

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

class ReturnInfo {
  const ReturnInfo({required this.summary});

  final String summary;

  String get title => summary.split(' · ').first;

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
    this.accumulatedSeconds,
    this.segmentStartedAt,
    this.source,
    this.checkedSopItems = const <String>[],
    this.skippedSopItems = const <String>[],
    this.holdReason,
    this.deferredBySelf = false,
    this.handovers = const <HandoverEntry>[],
    this.handoverRead = false,
    this.returnInfo,
    this.assigneeId,
  });

  final String id;

  final String state;

  final String type;

  final String? inspection;

  final String? assigneeId;

  final String? roomId;
  final String? roomNo;
  final String? title;
  final String? note;

  final bool inspectionWaived;

  final int? estMin;

  final int elapsedMinutes;

  final int? accumulatedSeconds;

  final DateTime? segmentStartedAt;

  final String? source;

  final List<String> checkedSopItems;

  final List<String> skippedSopItems;

  final String? holdReason;

  final bool deferredBySelf;

  final List<HandoverEntry> handovers;

  final bool handoverRead;

  final ReturnInfo? returnInfo;

  factory TaskDetail.fromJson(Map<String, dynamic> json) {
    return TaskDetail(
      id: json['id'] as String,
      state: json['state'] as String,
      type: json['type'] as String? ?? 'cleaning',
      inspection: json['inspection'] as String?,
      inspectionWaived: json['inspectionWaived'] as bool? ?? false,
      assigneeId: json['assigneeId'] as String?,
      roomId: json['roomId'] as String?,
      roomNo: json['roomNo'] as String?,
      title: json['title'] as String?,
      note: json['note'] as String?,
      estMin: json['estMin'] as int?,
      elapsedMinutes: json['elapsedMinutes'] as int? ?? 0,
      accumulatedSeconds: json['accumulatedSeconds'] as int?,
      segmentStartedAt: _dateTime(json['segmentStartedAt']),
      source: json['source'] as String?,
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

enum TaskNextStepKind {
  nextTask,

  unfinishedTask,

  allClear,
}

class TaskNextStep {
  const TaskNextStep({required this.kind, this.taskId, this.roomNo});

  final TaskNextStepKind kind;

  final String? taskId;

  final String? roomNo;

  static TaskNextStep? fromJson(Object? json) {
    if (json is! Map<String, dynamic>) {
      return null;
    }
    final kind = switch (json['kind']) {
      'next_task' => TaskNextStepKind.nextTask,
      'unfinished_task' => TaskNextStepKind.unfinishedTask,
      'all_clear' => TaskNextStepKind.allClear,
      _ => null,
    };
    if (kind == null) {
      return null;
    }
    return TaskNextStep(
      kind: kind,
      taskId: json['taskId'] as String?,
      roomNo: json['roomNo'] as String?,
    );
  }
}

class TaskCompletion {
  const TaskCompletion({
    required this.task,
    this.nextStep,
    this.cleaningDoneToday,
    this.celebrationEnabled = true,
  });

  final TaskDetail task;

  final TaskNextStep? nextStep;

  final int? cleaningDoneToday;

  final bool celebrationEnabled;

  factory TaskCompletion.fromJson(Map<String, dynamic> json) {
    return TaskCompletion(
      task: TaskDetail.fromJson(json),
      nextStep: TaskNextStep.fromJson(json['nextStep']),
      cleaningDoneToday: json['cleaningDoneToday'] as int?,
      celebrationEnabled: json['celebrationEnabled'] as bool? ?? true,
    );
  }
}

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

DateTime? _dateTime(Object? value) {
  if (value is! String) {
    return null;
  }
  return DateTime.tryParse(value);
}

List<String> _stringList(Object? value) {
  if (value is! List) {
    return const <String>[];
  }
  return value.whereType<String>().toList(growable: false);
}
