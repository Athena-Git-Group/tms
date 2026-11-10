import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import 'api_client.dart';
import '../features/pool/pool_screen.dart';
import '../features/shell/main_shell.dart';
import '../features/task/task_screen.dart';
import '../features/ticket/ticket_screen.dart';

/// 推播點開後導頁的唯一入口。
///
/// 現在收事件的管道是 iOS 原生側（`AppDelegate.swift`）透過
/// `MethodChannel('com.athena.tms/push')` 轉過來的
/// `UNUserNotificationCenter` delegate 事件；之後會換成
/// `firebase_messaging`。呼叫端（`main.dart`）只認得 [attach]、
/// [consumeInitialPush] 兩個入口，換底層實作時這兩個入口的簽章不變，
/// 只動 [_channel] 相關的內部實作——這是這個 class 存在的理由：把「怎麼
/// 收到推播事件」跟「收到之後怎麼導頁」切開。
///
/// payload 欄位約定（跟原生側共用）：
/// - `dest`：`pool` / `task` / `ticket` / `home`。
/// - `room`：`dest == 'task'` 時要帶，指定要開哪個房號的任務頁。
/// - `taskId`：`dest == 'task'` 時可選帶。帶了就直接開這張任務的詳情；
///   沒帶時任務頁只能用房號反查（反查只蓋得到待認領與下一間推薦，
///   已中斷等狀態要靠這個欄位才找得到單）。`dest == 'ticket'` 時必帶，
///   是該修繕任務的 task id（契約 `PushTarget`：kind=ticket 由後端依任務
///   類別決定，App 照 kind 機械導頁開工務單詳情，不自己查任務類別）。
/// - `highlight`：`dest == 'pool'` 時可選帶，指定待認領池要標亮哪個房號。
class PushGateway {
  PushGateway._();

  static final PushGateway instance = PushGateway._();

  static const MethodChannel _channel = MethodChannel('com.athena.tms/push');

  GlobalKey<NavigatorState>? _navigatorKey;
  bool _channelHandlerRegistered = false;

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
        handlePushPayload(_asStringKeyedMap(call.arguments));
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
      handlePushPayload(_asStringKeyedMap(result));
    } on MissingPluginException {
      // widget test 環境或原生側還沒接上 channel，視為沒有待處理的推播。
    }
  }

  /// 通知中心點一則通知後的導頁入口：通知帶的是契約 `PushTarget` 形狀的
  /// 去處描述（`kind` 是 pool／task／ticket／home，`kind == 'task'` 與
  /// `kind == 'ticket'` 帶 `taskId`，`kind == 'pool'` 可帶
  /// `highlightTaskId`），這裡把它換算成推播 payload 再交給
  /// [handlePushPayload]。去處與畫面的對照若在通知中心另寫一份，兩份遲早
  /// 長歪，同一筆事件從推播點開與從通知中心點開會導到不同畫面；對照收在
  /// 這個 class 一處，兩個入口的行為才一致。
  ///
  /// 任務頁以房號定位，去處描述卻只帶任務 id，`kind == 'task'` 要先以
  /// `GET /tasks/{id}` 查出房號才導得進去；工務單詳情頁以任務 id 定位，
  /// 沒有這個落差。依契約 `PushTarget`，App 照 kind 導頁，不自己查任務
  /// 類別推演去處。該帶任務 id 卻沒帶、或任務查不到房號時不導頁，留在
  /// 原畫面。
  Future<void> openTarget(Map<String, dynamic> target) async {
    final kind = target['kind'] as String?;
    final payload = <String, dynamic>{'dest': kind};
    if (kind == 'task' || kind == 'ticket') {
      final taskId = target['taskId'] as String?;
      if (taskId == null) {
        return;
      }
      payload['taskId'] = taskId;
      if (kind == 'task') {
        final roomNo = (await ApiClient.instance.getTask(taskId)).roomNo;
        if (roomNo == null) {
          return;
        }
        payload['room'] = roomNo;
      }
    } else if (kind == 'pool') {
      final highlightTaskId = target['highlightTaskId'] as String?;
      if (highlightTaskId != null) {
        payload['highlight'] =
            (await ApiClient.instance.getTask(highlightTaskId)).roomNo;
      }
    }
    handlePushPayload(payload);
  }

  /// 實際導頁邏輯，拆成獨立的公開方法方便 widget test 直接呼叫——不必真的
  /// 走一趟 MethodChannel 往返，也能驗證「收到 payload 之後導到哪裡」。
  void handlePushPayload(Map<String, dynamic> payload) {
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
        navigator.pushAndRemoveUntil(
          CupertinoPageRoute<void>(
            builder: (_) => PoolScreen(highlightRoomNo: highlight),
          ),
          (route) => route.isFirst,
        );
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
