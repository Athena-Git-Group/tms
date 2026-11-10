import 'dart:async';
import 'dart:developer' as developer;

import 'api_client.dart';

/// 推播底層（Firebase Cloud Messaging）在這個 App 裡用得到的那幾件事。
///
/// 抽成介面是為了讓 [PushTokenService] 的流程可以在不碰 Firebase 的環境下
/// 測試：widget test 與 CI 沒有 `GoogleService-Info.plist`，真的 Firebase
/// 連初始化都過不了。正式實作在 `firebase_push_messaging.dart`。
///
/// 契約：[initialize] 在沒有 Firebase 設定檔時丟例外，[PushTokenService]
/// 以此判定整個推播功能要不要停用；其餘方法只在 [initialize] 成功後被呼叫。
abstract class PushMessaging {
  Future<void> initialize();

  Future<bool> requestPermission();

  Future<String?> getToken();

  Stream<String> get onTokenRefresh;

  Stream<Map<String, dynamic>> get onMessageOpenedApp;

  Future<Map<String, dynamic>?> getInitialMessageData();

  Future<void> deleteToken();
}

/// 把 token 送到後端、或從後端撤下的那一側，同樣抽成介面方便測試換掉。
abstract class PushTokenUploader {
  Future<void> register({
    required String deviceFingerprint,
    required String pushToken,
  });

  Future<void> unregister({required String deviceFingerprint});
}

/// 提供一個這支裝置自己的、重開 App 也不變的識別字串。
abstract class DeviceFingerprintStore {
  Future<String> fingerprint();
}

/// 讓後端知道「這個登入者在這支手機上，推播要送到哪個 token」。
///
/// 生命週期跟著登入狀態走：
/// - 登入成功後呼叫 [start]：初始化底層、要權限、拿 token 上傳；之後底層
///   換 token 就再上傳一次。同時把「點開推播」的兩個 Firebase 入口接到
///   建構時給的 `onMessageOpened`（實務上是 `PushGateway`）。
/// - 登出前呼叫 [stop]：先趁憑證還在把這支裝置從後端撤下，再作廢本機 token。
///
/// 底層初始化失敗（最常見是開發機沒放 Firebase 設定檔）時整個服務退成
/// 停用：只記一行 log，App 其餘功能照常。推播是加分項，不該因為設定檔沒
/// 到位就讓 App 起不來或測試全紅。
class PushTokenService {
  PushTokenService({
    required this._messaging,
    required this._uploader,
    required this._fingerprintStore,
    required this._onMessageOpened,
  });

  /// 全 App 共用的服務；登入與登出畫面透過它呼叫 [start]／[stop]。
  /// 為 null 代表這個建置沒有裝推播（widget test 就是），呼叫端直接略過。
  /// 正式建置由 `firebase_push_messaging.dart` 的
  /// `installFirebasePushTokenService()` 在啟動時裝上。
  static PushTokenService? instance;

  final PushMessaging _messaging;
  final PushTokenUploader _uploader;
  final DeviceFingerprintStore _fingerprintStore;
  final Future<void> Function(Map<String, dynamic> data) _onMessageOpened;

  bool _initialized = false;
  bool _disabled = false;
  StreamSubscription<String>? _tokenRefreshSubscription;
  StreamSubscription<Map<String, dynamic>>? _messageOpenedSubscription;

  /// 底層初始化失敗後為 true；之後 [start]／[stop] 都直接返回。
  bool get isDisabled => _disabled;

  /// 登入成功後呼叫。可重複呼叫（換帳號再登入），每次都會把目前 token 上傳
  /// 到新登入者名下。
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

  /// 登出前呼叫：先撤後端登記，再作廢本機 token。撤登記失敗（離線、憑證剛
  /// 好過期）不擋登出，後端那筆會在下一次送達失敗時自己清掉。
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

  /// 釋放串流訂閱；目前只有測試需要，App 本身與服務同壽命。
  Future<void> dispose() async {
    await _tokenRefreshSubscription?.cancel();
    await _messageOpenedSubscription?.cancel();
  }
}

/// 把 token 上傳交給全 App 共用的 [ApiClient]：登入換到的憑證存在那裡。
/// 每次都讀 `ApiClient.instance` 而不是建構時抓一份，因為登出會把
/// `ApiClient.instance` 整個換新。
class ApiClientPushTokenUploader implements PushTokenUploader {
  const ApiClientPushTokenUploader({required this.platform});

  /// `ios` 或 `android`，後端據此挑 APNS 或純 FCM 的送法。
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
