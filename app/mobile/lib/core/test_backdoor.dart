import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/cupertino.dart';

import '../features/login/login_screen.dart';
import '../features/manager/pool_dept_preference.dart';
import '../features/shell/main_shell.dart';
import 'api_client.dart';
import 'connection_status.dart';
import 'landing.dart';
import 'push_token_service.dart';

class TestHooks {
  TestHooks._();

  static final TestHooks instance = TestHooks._();

  String? injectedClockIso;

  int? uploadFailAtIndex;

  final ValueNotifier<RememberedAccount?> rememberedAccount =
      ValueNotifier<RememberedAccount?>(null);

  void reset() {
    injectedClockIso = null;
    uploadFailAtIndex = null;
    rememberedAccount.value = null;
    ApiClient.dropWriteResponseCount = 0;
    ApiClient.dropWriteResponsePathPrefix = '';
    ConnectionStatus.instance.reset();
  }
}

/// 回傳裝置目前時間，測試注入時鐘時改用注入的值。注入的字串帶時區位移，
/// 例如 `2026-10-22T14:32+08:00`，經 `DateTime.parse` 會得到 UTC 時間，
/// 所以要轉回裝置本地時間，否則畫面顯示的時刻會差一個時區位移。
DateTime deviceNow() {
  final injected = TestHooks.instance.injectedClockIso;
  return injected == null ? DateTime.now() : DateTime.parse(injected).toLocal();
}

class RememberedAccount {
  const RememberedAccount({required this.loginId, required this.name});

  final String loginId;

  final String name;
}

class TestBackdoor {
  TestBackdoor._();

  static const bool _enabled = bool.fromEnvironment('TEST_BACKDOOR');

  static const int port = 9099;

  static final DateTime _processStartedAt = DateTime.now();

  static HttpServer? _server;

  static GlobalKey<NavigatorState>? _navigatorKey;

  static Future<void> maybeStart({GlobalKey<NavigatorState>? navigatorKey}) async {
    if (!_enabled || _server != null) {
      return;
    }
    _navigatorKey = navigatorKey;
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
    _server = server;
    server.listen(_handleRequest);
  }

  static Future<void> _handleRequest(HttpRequest request) async {
    try {
      final body = await utf8.decoder.bind(request).join();
      File('${Directory.systemTemp.path}/tms_backdoor_log.txt').writeAsStringSync(
          '${DateTime.now().toIso8601String()} pid=$pid ${request.method} '
          '${request.uri.path} body=[$body]\n',
          mode: FileMode.append);
      final Map<String, dynamic> payload =
          body.isEmpty ? <String, dynamic>{} : jsonDecode(body) as Map<String, dynamic>;

      switch ('${request.method} ${request.uri.path}') {
        case 'GET /state':
          request.response.statusCode = HttpStatus.ok;
          request.response.headers.contentType = ContentType.json;
          request.response.write(jsonEncode({
            'processStartedAt': _processStartedAt.toIso8601String(),
            'injectedClockIso': TestHooks.instance.injectedClockIso,
            'forceOffline': ConnectionStatus.instance.isOffline,
            'uploadFailAtIndex': TestHooks.instance.uploadFailAtIndex,
            'rememberedAccount': TestHooks.instance.rememberedAccount.value?.loginId,
            'flakyConnection': ConnectionStatus.instance.isUnstable,
            'dropWriteResponseCount': ApiClient.dropWriteResponseCount,
            'dropWriteResponsePathPrefix': ApiClient.dropWriteResponsePathPrefix,
            'pendingSyncCount': ConnectionStatus.instance.pendingSyncCount.value,
          }));
          await request.response.close();
          break;
        case 'POST /clock':
          TestHooks.instance.injectedClockIso = payload['isoTime'] as String?;
          _respondOk(request);
          break;
        case 'POST /offline':
          ConnectionStatus.instance
              .setOffline(payload['offline'] as bool? ?? false);
          _respondOk(request);
          break;
        case 'POST /upload-fail':
          TestHooks.instance.uploadFailAtIndex = payload['photoIndex'] as int?;
          _respondOk(request);
          break;
        case 'POST /remembered-account':
          final employee = payload['employee'] as String?;
          TestHooks.instance.rememberedAccount.value = employee == null
              ? null
              : RememberedAccount(
                  loginId: employee,
                  name: payload['name'] as String? ?? employee,
                );
          _respondOk(request);
          break;
        case 'POST /offline-queue':
          final queued = payload['count'] as int? ?? 0;
          for (var i = 0; i < queued; i++) {
            OfflineQueue.instance.enqueue(PendingOperation(
              kind: 'test-placeholder',
              idempotencyKey: ApiClient.newIdempotencyKey(),
              send: (_) async {},
            ));
          }
          _respondOk(request);
          break;
        case 'POST /drop-write-response':
          ApiClient.dropWriteResponseCount = payload['count'] as int? ?? 0;
          ApiClient.dropWriteResponsePathPrefix =
              payload['pathPrefix'] as String? ?? '';
          _respondOk(request);
          break;
        case 'POST /session':
          final session = LoginResult.fromJson(payload);
          ApiClient.instance.adoptSession(session);
          unawaited(PushTokenService.instance?.start());
          _replaceAllRoutes(landingShellForLogin(session));
          _respondOk(request);
          break;
        case 'POST /reset':
          _resetInMemoryState();
          _replaceAllRoutes(const LoginScreen());
          _respondOk(request);
          break;
        case 'POST /flaky-connection':
          ConnectionStatus.instance
              .setUnstable(payload['flaky'] as bool? ?? false);
          _respondOk(request);
          break;
        default:
          request.response.statusCode = HttpStatus.notFound;
          await request.response.close();
      }
    } catch (_) {
      request.response.statusCode = HttpStatus.badRequest;
      await request.response.close();
    }
  }

  /// 讓測試不必重開 App 也能從乾淨狀態開場：清掉的是行程內所有會跨畫面留存的狀態，
  /// 畫面上的計時器與輪詢則隨路由一起移除。漏清任何一項，前一個場景的登入身分、
  /// 待送佇列或待切分頁就會帶進下一個場景。
  static void _resetInMemoryState() {
    TestHooks.instance.reset();
    OfflineQueue.instance.clear();
    MainShellTabRequest.pending.value = null;
    MainShellTabRequest.poolHighlightRoomNo.value = null;
    MainShellTabRequest.poolHighlightTaskId.value = null;
    PoolDeptPreference.changes.value = null;
    ApiClient.instance = ApiClient();
  }

  static void _replaceAllRoutes(Widget screen) {
    _navigatorKey?.currentState?.pushAndRemoveUntil(
      CupertinoPageRoute<void>(builder: (_) => screen),
      (route) => false,
    );
  }

  static void _respondOk(HttpRequest request) {
    request.response.statusCode = HttpStatus.ok;
    request.response.headers.contentType = ContentType.json;
    request.response.write(jsonEncode({'ok': true}));
    request.response.close();
  }
}
