import Flutter
import UIKit
import UserNotifications

private let pushChannelName = "com.athena.tms/push"

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
    // APNs 裝置 token 由 firebase_messaging 外掛經 FlutterAppDelegate 的外掛生命週期接收，
    // 再換成 FCM token，所以這個檔案不實作 didRegisterForRemoteNotificationsWithDeviceToken。
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

  override func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    completionHandler([.banner, .list, .sound])
  }
}
