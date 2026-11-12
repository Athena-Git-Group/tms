import 'dart:io' show Platform;

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_client.dart';
import 'push_gateway.dart';
import 'push_token_service.dart';

/// firebase_messaging 17 把 `getToken`、`onTokenRefresh`、`deleteToken` 標成過時，
/// 但取代它們的 `register()` 回的是 Firebase Installation ID，不是 FCM 註冊 token。
/// 後端 `PUT /me/push-token` 收的是註冊 token，所以下面三處各壓掉一條過時警告。
class FirebasePushMessaging implements PushMessaging {
  @override
  Future<void> initialize() async {
    await Firebase.initializeApp();
  }

  @override
  Future<bool> requestPermission() async {
    final settings = await FirebaseMessaging.instance.requestPermission();
    return settings.authorizationStatus == AuthorizationStatus.authorized ||
        settings.authorizationStatus == AuthorizationStatus.provisional;
  }

  @override
  // ignore: deprecated_member_use
  Future<String?> getToken() => FirebaseMessaging.instance.getToken();

  @override
  Stream<String> get onTokenRefresh =>
      // ignore: deprecated_member_use
      FirebaseMessaging.instance.onTokenRefresh;

  @override
  Stream<Map<String, dynamic>> get onMessageOpenedApp =>
      FirebaseMessaging.onMessageOpenedApp.map(_dataOf);

  @override
  Future<Map<String, dynamic>?> getInitialMessageData() async {
    final message = await FirebaseMessaging.instance.getInitialMessage();
    return message == null ? null : _dataOf(message);
  }

  @override
  // ignore: deprecated_member_use
  Future<void> deleteToken() => FirebaseMessaging.instance.deleteToken();

  static Map<String, dynamic> _dataOf(RemoteMessage message) {
    return <String, dynamic>{
      ...message.data,
      if (message.messageId != null) 'gcm.message_id': message.messageId,
    };
  }
}

class SharedPreferencesFingerprintStore implements DeviceFingerprintStore {
  static const String _key = 'push.deviceFingerprint';

  String? _cached;

  @override
  Future<String> fingerprint() async {
    final cached = _cached;
    if (cached != null) {
      return cached;
    }
    final prefs = await SharedPreferences.getInstance();
    var value = prefs.getString(_key);
    if (value == null) {
      value = ApiClient.newIdempotencyKey();
      await prefs.setString(_key, value);
    }
    _cached = value;
    return value;
  }
}

void installFirebasePushTokenService() {
  PushTokenService.instance = PushTokenService(
    messaging: FirebasePushMessaging(),
    uploader: ApiClientPushTokenUploader(
      platform: Platform.isIOS ? 'ios' : 'android',
    ),
    fingerprintStore: SharedPreferencesFingerprintStore(),
    onMessageOpened: PushGateway.instance.handleRemoteMessageData,
  );
}
