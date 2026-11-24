import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'api_client.dart';
import 'landing.dart';
import 'push_target.dart';
import '../features/manager/pool_dept_preference.dart';
import '../features/shell/main_shell.dart';
import '../features/task/task_screen.dart';
import '../features/ticket/ticket_screen.dart';

const MethodChannel pushChannel = MethodChannel('com.athena.tms/push');

/// 冷啟動時「點了哪則推播把 App 打開」的來源。
abstract interface class InitialPushSource {
  Future<Map<String, dynamic>?> takeInitialPush();

  /// 只有 iOS 的 AppDelegate 註冊了 `getInitialPush`。Android 的冷啟動推播
  /// 由 firebase_messaging 的 `getInitialMessage` 交給 `PushTokenService`，
  /// 原生側沒有這個 channel，所以不去問。
  factory InitialPushSource.forPlatform() =>
      defaultTargetPlatform == TargetPlatform.iOS
          ? const IosInitialPushChannel()
          : const NoInitialPushSource();
}

class IosInitialPushChannel implements InitialPushSource {
  const IosInitialPushChannel();

  @override
  Future<Map<String, dynamic>?> takeInitialPush() async {
    final result = await pushChannel.invokeMethod<Object?>('getInitialPush');
    return result == null ? null : _asStringKeyedMap(result);
  }
}

class NoInitialPushSource implements InitialPushSource {
  const NoInitialPushSource();

  @override
  Future<Map<String, dynamic>?> takeInitialPush() async => null;
}

Map<String, dynamic> _asStringKeyedMap(Object? value) {
  if (value is Map) {
    return value.map((key, v) => MapEntry(key.toString(), v));
  }
  return <String, dynamic>{};
}

class PushGateway {
  PushGateway._();

  static final PushGateway instance = PushGateway._();

  InitialPushSource initialPushSource = InitialPushSource.forPlatform();

  static const Duration _duplicateWindow = Duration(seconds: 5);

  GlobalKey<NavigatorState>? _navigatorKey;
  bool _channelHandlerRegistered = false;
  String? _lastOpenedMessageId;
  DateTime? _lastOpenedAt;

  void attach(GlobalKey<NavigatorState> navigatorKey) {
    _navigatorKey = navigatorKey;
    if (_channelHandlerRegistered) {
      return;
    }
    _channelHandlerRegistered = true;
    pushChannel.setMethodCallHandler((call) async {
      if (call.method == 'onPushOpened') {
        await handlePushPayload(_asStringKeyedMap(call.arguments));
      }
    });
  }

  Future<void> consumeInitialPush() async {
    final payload = await initialPushSource.takeInitialPush();
    if (payload == null) {
      return;
    }
    await handlePushPayload(payload);
  }

  Future<void> handleRemoteMessageData(Map<String, dynamic> data) {
    return handlePushPayload(data);
  }

  Future<void> openTarget(Map<String, dynamic> target) async {
    final payload = await pushPayloadFromTarget(target, roomNoOf: _roomNoOf);
    if (payload == null) {
      return;
    }
    await _navigate(payload);
  }

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
    await _navigate(payload);
  }

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

  Future<void> _navigate(Map<String, dynamic> payload) async {
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
        await _rememberManagerPoolDept(payload['dept'] as String?);
        navigator.popUntil((route) => route.isFirst);
        MainShellTabRequest.goToPool(
          highlightRoomNo: highlight,
          highlightTaskId: payload['highlightTaskId'] as String?,
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
              view: TicketView.forSession(ApiClient.instance.session),
              taskId: taskId,
            ),
          ),
        );
        break;
      case 'home':
        final session = ApiClient.instance.session;
        navigator.pushAndRemoveUntil(
          CupertinoPageRoute<void>(
            builder: (_) => session == null
                ? const HousekeepingShell()
                : landingShellForLogin(session),
          ),
          (route) => false,
        );
        break;
      default:
        break;
    }
  }

  Future<void> _rememberManagerPoolDept(String? dept) async {
    if (ApiClient.instance.session?.role != 'manager' ||
        !PoolDeptPreference.isPoolDept(dept)) {
      return;
    }
    await PoolDeptPreference.write(dept!);
  }
}
