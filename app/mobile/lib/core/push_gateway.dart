import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import 'api_client.dart';
import 'landing.dart';
import 'push_target.dart';
import '../features/shell/main_shell.dart';
import '../features/task/task_screen.dart';
import '../features/ticket/ticket_screen.dart';

class PushGateway {
  PushGateway._();

  static final PushGateway instance = PushGateway._();

  static const MethodChannel _channel = MethodChannel('com.athena.tms/push');

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
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onPushOpened') {
        await handlePushPayload(_asStringKeyedMap(call.arguments));
      }
    });
  }

  Future<void> consumeInitialPush() async {
    try {
      final result = await _channel.invokeMethod<Object?>('getInitialPush');
      if (result == null) {
        return;
      }
      await handlePushPayload(_asStringKeyedMap(result));
    } on MissingPluginException {
      // 原生側沒有接上這個 channel 時（例如 widget test），Flutter 會丟 MissingPluginException。
    }
  }

  Future<void> handleRemoteMessageData(Map<String, dynamic> data) {
    return handlePushPayload(data);
  }

  Future<void> openTarget(Map<String, dynamic> target) async {
    final payload = await pushPayloadFromTarget(target, roomNoOf: _roomNoOf);
    if (payload == null) {
      return;
    }
    _navigate(payload);
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
    _navigate(payload);
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

  Map<String, dynamic> _asStringKeyedMap(Object? value) {
    if (value is Map) {
      return value.map((key, v) => MapEntry(key.toString(), v));
    }
    return <String, dynamic>{};
  }
}
