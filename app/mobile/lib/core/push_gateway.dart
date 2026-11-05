import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import '../features/home/home_screen.dart';
import '../features/pool/pool_screen.dart';
import '../features/task/task_screen.dart';

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
/// - `dest`：`pool` / `task` / `home`。
/// - `room`：`dest == 'task'` 時要帶，指定要開哪個房號的任務頁。
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

  /// 實際導頁邏輯，拆成獨立的公開方法方便 widget test 直接呼叫——不必真的
  /// 走一趟 MethodChannel 往返，也能驗證「收到 payload 之後導到哪裡」。
  void handlePushPayload(Map<String, dynamic> payload) {
    final navigator = _navigatorKey?.currentState;
    if (navigator == null) {
      return;
    }

    final dest = payload['dest'] as String?;
    final room = payload['room'] as String?;
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
          CupertinoPageRoute<void>(builder: (_) => TaskScreen(roomNo: room)),
        );
        break;
      case 'home':
        navigator.pushAndRemoveUntil(
          CupertinoPageRoute<void>(builder: (_) => const HomeScreen()),
          (route) => false,
        );
        break;
      default:
        // 不認得的去處：不導頁，留在原畫面，也不讓整個流程炸掉。
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
