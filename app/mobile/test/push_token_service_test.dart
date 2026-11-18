import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/push_token_service.dart';

void main() {
  late _FakeMessaging messaging;
  late _FakeUploader uploader;
  late List<Map<String, dynamic>> openedMessages;
  late PushTokenService service;

  setUp(() {
    messaging = _FakeMessaging();
    uploader = _FakeUploader();
    openedMessages = [];
    service = PushTokenService(
      messaging: messaging,
      uploader: uploader,
      fingerprintStore: _FixedFingerprint('device-1'),
      onMessageOpened: (data) async => openedMessages.add(data),
    );
  });

  tearDown(() async {
    await service.dispose();
    await messaging.close();
  });

  test('登入後取到 token 就以裝置識別上傳', () async {
    messaging.token = 'fcm-token-1';

    await service.start();

    expect(uploader.registered, [
      (deviceFingerprint: 'device-1', pushToken: 'fcm-token-1'),
    ]);
    expect(messaging.permissionRequests, 1);
    expect(service.isDisabled, isFalse);
  });

  test('底層換了 token 會再上傳一次新的', () async {
    messaging.token = 'fcm-token-1';
    await service.start();

    messaging.tokenRefreshController.add('fcm-token-2');
    await _flush();

    expect(uploader.registered.map((r) => r.pushToken), [
      'fcm-token-1',
      'fcm-token-2',
    ]);
    expect(uploader.registered.last.deviceFingerprint, 'device-1');
  });

  test('Firebase 初始化失敗：不炸、不上傳、之後 start／stop 都直接略過', () async {
    messaging.initializeError = StateError('core/not-initialized');

    await service.start();
    await service.stop();
    await service.start();

    expect(service.isDisabled, isTrue);
    expect(messaging.initializeCalls, 1);
    expect(messaging.permissionRequests, 0);
    expect(uploader.registered, isEmpty);
    expect(uploader.unregistered, isEmpty);
  });

  test('使用者拒絕通知權限時不上傳 token', () async {
    messaging
      ..token = 'fcm-token-1'
      ..permissionGranted = false;

    await service.start();

    expect(uploader.registered, isEmpty);
  });

  test('上傳失敗只記 log，不讓登入流程炸掉', () async {
    messaging.token = 'fcm-token-1';
    uploader.registerError = Exception('500');

    await expectLater(service.start(), completes);
  });

  test('登出：先撤後端登記、再作廢本機 token', () async {
    messaging.token = 'fcm-token-1';
    await service.start();

    await service.stop();

    expect(uploader.unregistered, ['device-1']);
    expect(messaging.deleteTokenCalls, 1);
  });

  test('冷啟動帶的推播與背景點開的推播都交給同一個導頁入口', () async {
    messaging
      ..token = 'fcm-token-1'
      ..initialMessageData = {'target': '{"kind":"home"}'};
    await service.start();

    messaging.messageOpenedController
        .add({'target': '{"kind":"task","taskId":"t-1"}'});
    await _flush();

    expect(openedMessages, [
      {'target': '{"kind":"home"}'},
      {'target': '{"kind":"task","taskId":"t-1"}'},
    ]);
  });
}

Future<void> _flush() => Future<void>.delayed(Duration.zero);

class _FakeMessaging implements PushMessaging {
  String? token;
  bool permissionGranted = true;
  Object? initializeError;
  Map<String, dynamic>? initialMessageData;

  int initializeCalls = 0;
  int permissionRequests = 0;
  int deleteTokenCalls = 0;

  final tokenRefreshController = StreamController<String>.broadcast();
  final messageOpenedController =
      StreamController<Map<String, dynamic>>.broadcast();

  @override
  Future<void> initialize() async {
    initializeCalls++;
    final error = initializeError;
    if (error != null) {
      throw error;
    }
  }

  @override
  Future<bool> requestPermission() async {
    permissionRequests++;
    return permissionGranted;
  }

  @override
  Future<String?> getToken() async => token;

  @override
  Stream<String> get onTokenRefresh => tokenRefreshController.stream;

  @override
  Stream<Map<String, dynamic>> get onMessageOpenedApp =>
      messageOpenedController.stream;

  @override
  Future<Map<String, dynamic>?> getInitialMessageData() async =>
      initialMessageData;

  @override
  Future<void> deleteToken() async {
    deleteTokenCalls++;
  }

  Future<void> close() async {
    await tokenRefreshController.close();
    await messageOpenedController.close();
  }
}

class _FakeUploader implements PushTokenUploader {
  final registered = <({String deviceFingerprint, String pushToken})>[];
  final unregistered = <String>[];
  Object? registerError;

  @override
  Future<void> register({
    required String deviceFingerprint,
    required String pushToken,
  }) async {
    final error = registerError;
    if (error != null) {
      throw error;
    }
    registered.add((deviceFingerprint: deviceFingerprint, pushToken: pushToken));
  }

  @override
  Future<void> unregister({required String deviceFingerprint}) async {
    unregistered.add(deviceFingerprint);
  }
}

class _FixedFingerprint implements DeviceFingerprintStore {
  _FixedFingerprint(this.value);

  final String value;

  @override
  Future<String> fingerprint() async => value;
}
