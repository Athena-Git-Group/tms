import 'dart:async';
import 'dart:developer' as developer;

import 'api_client.dart';

abstract class PushMessaging {
  Future<void> initialize();

  Future<bool> requestPermission();

  Future<String?> getToken();

  Stream<String> get onTokenRefresh;

  Stream<Map<String, dynamic>> get onMessageOpenedApp;

  Future<Map<String, dynamic>?> getInitialMessageData();

  Future<void> deleteToken();
}

abstract class PushTokenUploader {
  Future<void> register({
    required String deviceFingerprint,
    required String pushToken,
  });

  Future<void> unregister({required String deviceFingerprint});
}

abstract class DeviceFingerprintStore {
  Future<String> fingerprint();
}

class PushTokenService {
  PushTokenService({
    required this._messaging,
    required this._uploader,
    required this._fingerprintStore,
    required this._onMessageOpened,
  });

  static PushTokenService? instance;

  final PushMessaging _messaging;
  final PushTokenUploader _uploader;
  final DeviceFingerprintStore _fingerprintStore;
  final Future<void> Function(Map<String, dynamic> data) _onMessageOpened;

  bool _initialized = false;
  bool _disabled = false;
  StreamSubscription<String>? _tokenRefreshSubscription;
  StreamSubscription<Map<String, dynamic>>? _messageOpenedSubscription;

  bool get isDisabled => _disabled;

  Future<void> start() async {
    if (_disabled) {
      return;
    }
    if (!await _ensureInitialized()) {
      return;
    }
    try {
      final granted = await _messaging.requestPermission();
      if (!granted) {
        developer.log('使用者未授權通知，略過推播 token 上傳', name: 'push');
        return;
      }
      final token = await _messaging.getToken();
      if (token != null) {
        await _upload(token);
      }
    } catch (error, stackTrace) {
      developer.log('推播 token 上傳失敗',
          name: 'push', error: error, stackTrace: stackTrace);
    }
  }

  Future<void> stop() async {
    if (_disabled || !_initialized) {
      return;
    }
    try {
      final fingerprint = await _fingerprintStore.fingerprint();
      await _uploader.unregister(deviceFingerprint: fingerprint);
    } catch (error, stackTrace) {
      developer.log('撤下推播 token 失敗',
          name: 'push', error: error, stackTrace: stackTrace);
    }
    try {
      await _messaging.deleteToken();
    } catch (error, stackTrace) {
      developer.log('作廢本機推播 token 失敗',
          name: 'push', error: error, stackTrace: stackTrace);
    }
  }

  Future<bool> _ensureInitialized() async {
    if (_initialized) {
      return true;
    }
    try {
      await _messaging.initialize();
    } catch (error) {
      _disabled = true;
      developer.log('Firebase 初始化失敗，推播停用：$error', name: 'push');
      return false;
    }
    _initialized = true;
    _tokenRefreshSubscription = _messaging.onTokenRefresh.listen(
      (token) => _upload(token),
      onError: (Object error) =>
          developer.log('token 更新串流錯誤', name: 'push', error: error),
    );
    _messageOpenedSubscription =
        _messaging.onMessageOpenedApp.listen(_onMessageOpened);
    final initial = await _messaging.getInitialMessageData();
    if (initial != null) {
      await _onMessageOpened(initial);
    }
    return true;
  }

  Future<void> _upload(String token) async {
    try {
      final fingerprint = await _fingerprintStore.fingerprint();
      await _uploader.register(
        deviceFingerprint: fingerprint,
        pushToken: token,
      );
    } catch (error, stackTrace) {
      developer.log('推播 token 上傳失敗',
          name: 'push', error: error, stackTrace: stackTrace);
    }
  }

  Future<void> dispose() async {
    await _tokenRefreshSubscription?.cancel();
    await _messageOpenedSubscription?.cancel();
  }
}

class ApiClientPushTokenUploader implements PushTokenUploader {
  const ApiClientPushTokenUploader({required this.platform});

  final String platform;

  @override
  Future<void> register({
    required String deviceFingerprint,
    required String pushToken,
  }) {
    return ApiClient.instance.registerPushToken(
      deviceFingerprint: deviceFingerprint,
      pushToken: pushToken,
      platform: platform,
    );
  }

  @override
  Future<void> unregister({required String deviceFingerprint}) {
    return ApiClient.instance
        .unregisterPushToken(deviceFingerprint: deviceFingerprint);
  }
}
