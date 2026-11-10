import Flutter
import UIKit
import UserNotifications

/// 跟 Flutter 側 `lib/core/push_gateway.dart` 的 `PushGateway` 對接的
/// MethodChannel 名稱——改名要雙邊一起改。
private let pushChannelName = "com.athena.tms/push"

/// 把「使用者點開一則推播通知」的原生事件轉成 Flutter 側能用的
/// MethodChannel 呼叫，交給 Dart 的 `PushGateway` 決定要導去哪個畫面。
///
/// 這個類別自己實作 `UNUserNotificationCenterDelegate` 的兩個回呼，而且
/// 不呼叫 super，所以 Flutter 不會把它們再轉給 firebase_messaging 外掛：
/// iOS 上每一次「點開通知」（含 FCM 送來的）都只從這裡經 MethodChannel
/// 進 Dart 一次，不會在 Firebase 的 `onMessageOpenedApp` 再出現第二次。
/// 要改成走 Firebase 那條路，Dart 側的 `PushGateway` 已經依訊息 id 擋重複，
/// 但仍以單一管道為準。
///
/// 點開事件送達的時間點可能早於 `pushChannel` 建立完成。這裡保證事件
/// 不論早晚都不丟：`pendingInitialPush` 暫存 `pushChannel` 還不存在時
/// 收到的 payload，等 Flutter 引擎跑起來、Dart 側主動取用才交出去；少了
/// 這層暫存，提早送達的事件會直接消失，使用者點了通知卻什麼都不會發生。
@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {

  private var pushChannel: FlutterMethodChannel?

  private var pendingInitialPush: [String: Any]?

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    UNUserNotificationCenter.current().delegate = self
    UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    // 向 APNS 要裝置 token。拿到的 token 由 firebase_messaging 外掛接走
    // （它透過 FlutterAppDelegate 的外掛生命週期轉發收到
    // didRegisterForRemoteNotificationsWithDeviceToken），再換成 FCM token；
    // 這個檔案不自己處理 APNS token。沒有 Firebase 設定檔時這一行無害，
    // token 收下來沒人用而已。
    application.registerForRemoteNotifications()

    if let remoteNotification = launchOptions?[.remoteNotification] as? [String: Any] {
      pendingInitialPush = remoteNotification
    }

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)

    let channel = FlutterMethodChannel(
      name: pushChannelName,
      binaryMessenger: engineBridge.applicationRegistrar.messenger()
    )
    channel.setMethodCallHandler { [weak self] call, result in
      guard call.method == "getInitialPush" else {
        result(FlutterMethodNotImplemented)
        return
      }
      // 交付一次就清空：避免同一筆冷啟動推播被問第二次、導頁觸發兩次。
      result(self?.pendingInitialPush)
      self?.pendingInitialPush = nil
    }
    pushChannel = channel
  }

  override func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
  ) {
    let userInfo = response.notification.request.content.userInfo as? [String: Any] ?? [:]
    if pushChannel != nil {
      pushChannel?.invokeMethod("onPushOpened", arguments: userInfo)
    } else {
      pendingInitialPush = userInfo
    }
    completionHandler()
  }

  /// 導頁只需要在使用者點開通知時發生；App 在前景收到推播時，讓系統照常
  /// 顯示通知橫幅即可，不在這裡多做處理。
  override func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    completionHandler([.banner, .list, .sound])
  }
}
