import 'dart:convert';

typedef RoomNoLookup = Future<String?> Function(String taskId);

/// FCM data message 的值只能是字串，所以 `target` 是 JSON 字串。
/// iOS 原生側轉過來的 userInfo 則可能已經是 map。
Map<String, dynamic>? parsePushTarget(Object? raw) {
  if (raw is Map) {
    return raw.map((key, value) => MapEntry(key.toString(), value));
  }
  if (raw is String && raw.isNotEmpty) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map) {
        return decoded.map((key, value) => MapEntry(key.toString(), value));
      }
    } on FormatException {
      return null;
    }
  }
  return null;
}

Future<Map<String, dynamic>?> pushPayloadFromTarget(
  Map<String, dynamic> target, {
  required RoomNoLookup roomNoOf,
}) async {
  final kind = target['kind'] as String?;
  final payload = <String, dynamic>{'dest': kind};
  if (kind == 'task' || kind == 'ticket') {
    final taskId = target['taskId'] as String?;
    if (taskId == null) {
      return null;
    }
    payload['taskId'] = taskId;
    if (kind == 'task') {
      final roomNo = await roomNoOf(taskId);
      if (roomNo == null) {
        return null;
      }
      payload['room'] = roomNo;
    }
  } else if (kind == 'pool') {
    final highlightTaskId = target['highlightTaskId'] as String?;
    if (highlightTaskId != null) {
      final roomNo = await roomNoOf(highlightTaskId);
      if (roomNo != null) {
        payload['highlight'] = roomNo;
      }
    }
  }
  return payload;
}
