import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import 'api_client.dart';
import 'push_target.dart';
import '../features/shell/main_shell.dart';
import '../features/task/task_screen.dart';
import '../features/ticket/ticket_screen.dart';

/// 推播點開後導頁的唯一入口。
///
/// 收事件的管道有兩條，都匯進 [handlePushPayload]：
/// - iOS 原生側（`AppDelegate.swift`）透過 `MethodChannel('com.athena.tms/push')`
///   轉過來的 `UNUserNotificationCenter` delegate 事件（[attach]、
///   [consumeInitialPush]）。iOS 上所有「點開通知」都走這條，包含 FCM 送來
///   的，因為原生側自己處理 delegate 回呼、不轉給 Firebase 外掛。
/// - `firebase_messaging` 的 `onMessageOpenedApp`／`getInitialMessage`
///   （[handleRemoteMessageData]），由 `PushTokenService` 接上，Android 靠這條。
///
/// 呼叫端只認得這幾個入口，換底層實作時簽章不變——這是這個 class 存在的
/// 理由：把「怎麼收到推播事件」跟「收到之後怎麼導頁」切開。
///
/// payload 有兩種形狀，[handlePushPayload] 都收：
///
/// **契約形狀**：帶 `target` 欄位，內容是 `PushTarget` 的 JSON 字串（FCM data
/// message 的值只能是字串）或 map。這是後端真正送出的格式，換算規則在
/// `push_target.dart`。有 `target` 時其他欄位一律不看。
///
/// **內部形狀**（跟原生側、`xcrun simctl push` 測試腳本共用）：
/// - `dest`：`pool` / `task` / `ticket` / `home`。
/// - `room`：`dest == 'task'` 時要帶，指定要開哪個房號的任務頁。
/// - `taskId`：`dest == 'task'` 時可選帶。帶了就直接開這張任務的詳情；
///   沒帶時任務頁只能用房號反查（反查只蓋得到待認領與下一間推薦，
///   已中斷等狀態要靠這個欄位才找得到單）。`dest == 'ticket'` 時必帶，
///   是該修繕任務的 task id。
/// - `highlight`：`dest == 'pool'` 時可選帶，指定待認領池要標亮哪個房號。
class PushGateway {
  PushGateway._();

  static final PushGateway instance = PushGateway._();

  static const MethodChannel _channel = MethodChannel('com.athena.tms/push');

  /// 同一則推播只導頁一次。FCM 在 iOS 上的 userInfo 帶 `gcm.message_id`，
  /// 後端的 data 另帶 `idempotencyKey`；兩條收事件的管道要是都轉來同一則
  /// （例如日後原生側改成也轉給 Firebase 外掛），第二次會被這裡擋下，
  /// 不會把同一個畫面推兩層。
  static const Duration _duplicateWindow = Duration(seconds: 5);

  GlobalKey<NavigatorState>? _navigatorKey;
  bool _channelHandlerRegistered = false;
  String? _lastOpenedMessageId;
  DateTime? _lastOpenedAt;

  /// App 啟動時呼叫一次：記住要導頁用的 [navigatorKey]，並掛上「App 在
  /// 前景或背景時使用者點開推播」的事件監聽（冷啟動的情境不會從這個管道
  /// 來，見 [consumeInitialPush]）。
  void attach(GlobalKey<NavigatorState> navigatorKey) {
    _navigatorKey = navigatorKey;
    if (_channelHandlerRegistered) {
      return;
    }
    _channelHandlerRegistered = true;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onPushOpened') {
        await handlePushPayload(_asStringKeyedMap(call.arguments));
      }
    });
  }

  /// 冷啟動時呼叫：要等第一幀畫完、Navigator 掛上後才問原生側「啟動時
  /// 是不是帶了推播」——原生側收到使用者點開通知事件的時間點，比 Flutter
  /// 引擎、Navigator 準備好還早，順序顛倒會導到還沒 mount 的 Navigator 上。
  Future<void> consumeInitialPush() async {
    try {
      final result = await _channel.invokeMethod<Object?>('getInitialPush');
      if (result == null) {
        return;
      }
      await handlePushPayload(_asStringKeyedMap(result));
    } on MissingPluginException {
      // widget test 環境或原生側還沒接上 channel，視為沒有待處理的推播。
    }
  }

  /// `firebase_messaging` 的 `RemoteMessage.data` 入口：data 欄位裡有
  /// `target`，形狀就是契約形狀的 payload，直接交給 [handlePushPayload]。
  Future<void> handleRemoteMessageData(Map<String, dynamic> data) {
    return handlePushPayload(data);
  }

  /// 通知中心點一則通知後的導頁入口：通知帶的是 `PushTarget`，與推播的
  /// `target` 欄位同一個形狀，所以走同一份換算（`pushPayloadFromTarget`）。
  Future<void> openTarget(Map<String, dynamic> target) async {
    final payload = await pushPayloadFromTarget(target, roomNoOf: _roomNoOf);
    if (payload == null) {
      return;
    }
    _navigate(payload);
  }

  /// 實際導頁邏輯，拆成獨立的公開方法方便 widget test 直接呼叫——不必真的
  /// 走一趟 MethodChannel 往返，也能驗證「收到 payload 之後導到哪裡」。
  ///
  /// 帶 `target` 的走契約形狀（先換算、可能要查房號），其餘照內部形狀直接
  /// 導頁。
  Future<void> handlePushPayload(Map<String, dynamic> payload) async {
    if (_isDuplicateOpen(payload)) {
      return;
    }
    if (payload.containsKey('target')) {
      final target = parsePushTarget(payload['target']);
      if (target == null) {
        return;
      }
      await openTarget(target);
      return;
    }
    _navigate(payload);
  }

  /// 點開推播時任務可能已經不存在（被取消、被合併），後端回 404。這時要讓
  /// 換算端走「沒有房號」那條路、留在原畫面，而不是讓例外一路往上炸掉整個
  /// 導頁流程。
  Future<String?> _roomNoOf(String taskId) async {
    try {
      return (await ApiClient.instance.getTask(taskId)).roomNo;
    } on ApiException {
      return null;
    }
  }

  bool _isDuplicateOpen(Map<String, dynamic> payload) {
    final messageId = (payload['gcm.message_id'] ?? payload['idempotencyKey'])
        ?.toString();
    if (messageId == null) {
      return false;
    }
    final now = DateTime.now();
    final lastAt = _lastOpenedAt;
    final isDuplicate = messageId == _lastOpenedMessageId &&
        lastAt != null &&
        now.difference(lastAt) < _duplicateWindow;
    _lastOpenedMessageId = messageId;
    _lastOpenedAt = now;
    return isDuplicate;
  }

  void _navigate(Map<String, dynamic> payload) {
    final navigator = _navigatorKey?.currentState;
    if (navigator == null) {
      return;
    }

    final dest = payload['dest'] as String?;
    final room = payload['room'] as String?;
    final taskId = payload['taskId'] as String?;
    final highlight = payload['highlight'] as String?;

    switch (dest) {
      case 'pool':
        navigator.popUntil((route) => route.isFirst);
        MainShellTabRequest.goToPool(highlightRoomNo: highlight);
        break;
      case 'task':
        if (room == null) {
          return;
        }
        navigator.push(
          CupertinoPageRoute<void>(
            builder: (_) => TaskScreen(roomNo: room, taskId: taskId),
          ),
        );
        break;
      case 'ticket':
        if (taskId == null) {
          return;
        }
        navigator.push(
          CupertinoPageRoute<void>(
            builder: (_) => TicketScreen(
              // 推播是唯一事前不知道這張單要給誰看的入口：報修的人與修的人
              // 收到的是同一則推播，看到的卻是兩支不同的畫面，所以依登入帳
              // 號的部門決定，和原型「同一個 App 只依部門切換」一致
              // （`Main.dc.html:1909`）。
              view: ApiClient.instance.session?.dept == 'engineering'
                  ? TicketView.engineering
                  : TicketView.housekeeping,
              taskId: taskId,
            ),
          ),
        );
        break;
      case 'home':
        navigator.pushAndRemoveUntil(
          CupertinoPageRoute<void>(builder: (_) => const HousekeepingShell()),
          (route) => false,
        );
        break;
      default:
        break;
    }
  }

  Map<String, dynamic> _asStringKeyedMap(Object? value) {
    if (value is Map) {
      return value.map((key, v) => MapEntry(key.toString(), v));
    }
    return <String, dynamic>{};
  }
}
