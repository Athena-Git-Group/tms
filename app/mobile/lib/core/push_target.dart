import 'dart:convert';

/// 把後端描述「推播要帶使用者去哪」的 `PushTarget`，換算成 App 內部導頁用的
/// payload。推播點開與通知中心點開共用這一份換算，兩個入口才會把同一筆
/// 事件導到同一個畫面。
///
/// `PushTarget` 的形狀（契約 `shared.yaml`）：
/// - `{"kind":"task","taskId":...}`／`{"kind":"ticket","taskId":...}`
/// - `{"kind":"pool","dept":...,"highlightTaskId"?:...}`
/// - `{"kind":"home"}`
///
/// 內部 payload 的形狀（`PushGateway.handlePushPayload` 認得的）：
/// `dest`、`room`、`taskId`、`highlight`，其中 `room` 與 `highlight` 是房號。
///
/// 兩者的落差在「任務頁以房號定位，`PushTarget` 卻只帶任務 id」，所以
/// `kind == 'task'` 與帶 `highlightTaskId` 的 `kind == 'pool'` 要先查一次
/// 房號；查房號的方法由呼叫端注入（[RoomNoLookup]），這個檔案才不依賴
/// 網路，單元測試可以直接餵對照表。

/// 以任務 id 查房號；查不到回 null。
typedef RoomNoLookup = Future<String?> Function(String taskId);

/// 把推播資料裡的 `target` 欄位還原成 map。
///
/// FCM data message 的值只能是字串，所以後端把 `PushTarget` 以 JSON 字串
/// 放進 `target`；iOS 原生側轉過來的 userInfo 有時已經是 map。兩種都收，
/// 不是合法 JSON 物件時回 null，讓呼叫端把它當成「沒有去處」處理，而不是
/// 讓整個導頁流程丟例外。
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

/// 把 `PushTarget` 換算成導頁 payload。
///
/// 回 null 代表「不要導頁，留在原畫面」：該帶任務 id 卻沒帶、或任務查不到
/// 房號。待認領池要標亮的那間查不到房號時仍然導到池子，只是不標亮——
/// 標亮是錦上添花，不該因為它失敗就讓使用者點了推播沒反應。
///
/// 依契約，App 照 `kind` 機械導頁，不自己查任務類別推演去處；`kind` 不認得
/// 時照樣回一份 payload，由導頁端用 default 分支忽略。
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
