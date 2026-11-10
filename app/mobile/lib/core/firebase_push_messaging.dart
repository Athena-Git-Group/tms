import 'dart:io' show Platform;

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_client.dart';
import 'push_gateway.dart';
import 'push_token_service.dart';

/// `PushMessaging` 的正式實作：接 Firebase Cloud Messaging。
///
/// token 一律用 FCM 註冊 token（`getToken`／`onTokenRefresh`／`deleteToken`）。
/// firebase_messaging 17 把這三個標成過時、改推 `register()`＋`onRegistered`，
/// 但那一組回的是 Firebase Installation ID，後端 `PUT /me/push-token` 收的
/// 是 FCM Admin SDK 送訊息用的註冊 token，兩者不能互換；在後端契約換掉
/// 之前繼續用註冊 token，所以下面三處各壓掉一條過時警告。
///
/// 前景收到推播（`onMessage`）這裡刻意不接：iOS 原生側的
/// `willPresent` 已經讓系統照常顯示橫幅，Dart 再彈一次會出現兩個提示。
class FirebasePushMessaging implements PushMessaging {
  @override
  Future<void> initialize() async {
    // 沒有 GoogleService-Info.plist／google-services.json 時這一行會丟
    // FirebaseException（core/not-initialized），由 PushTokenService 接住後
    // 把推播整個停用。
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

  /// 導頁只看 data 欄位；`messageId` 另外帶上，`PushGateway` 用它認出同一則
  /// 推播從兩條管道各轉來一次的情況。
  static Map<String, dynamic> _dataOf(RemoteMessage message) {
    return <String, dynamic>{
      ...message.data,
      if (message.messageId != null) 'gcm.message_id': message.messageId,
    };
  }
}

/// 裝置識別字串存在 `shared_preferences`：第一次要用時產生一個 UUID，之後
/// 每次都讀同一個。App 重灌會換一個新的，這是可接受的——重灌後 token 也
/// 一定會換，後端把它當成一支新裝置即可。
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

/// 在 `main()` 裡呼叫一次，把接 Firebase 的 `PushTokenService` 裝成全 App
/// 共用的那一份。這裡只組裝、不初始化 Firebase；初始化留到第一次登入成功
/// 才做，沒登入就沒有人可以掛 token。
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
