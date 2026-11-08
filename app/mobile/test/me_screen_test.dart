import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/me/me_screen.dart';

/// 驗「我的」長出身分卡與兩組設定列之後，登出入口還在原地、還叫同一個名字。
///
/// 登出是整支 App 唯一一條離開已登入狀態的路，驗收以 `me.logout-button`
/// 定位；這一頁從一顆按鈕長成一整頁，最容易把它包進某一層而定位不到。
void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  Future<void> pumpScreen(WidgetTester tester, {LoginResult? session}) async {
    ApiClient.instance = _FakeApiClient(session);
    await tester.pumpWidget(const CupertinoApp(home: MeScreen()));
    await tester.pumpAndSettle();
  }

  testWidgets('身分卡顯示登入者的名字與部門，設定列與登出都在', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(
      tester,
      session: const LoginResult(
        token: 't',
        expiresAt: '2026-11-08T00:00:00Z',
        staffId: 'staff-1',
        name: '陳小美',
        role: 'housekeeping',
        dept: 'housekeeping',
        mustChangePassword: false,
      ),
    );

    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);
    expect(find.text('陳小美'), findsOneWidget);
    // 頭像取名字的最後一個字，不是姓。
    expect(find.text('美'), findsOneWidget);
    expect(find.text('房務'), findsOneWidget);

    expect(find.text('通知與顯示'), findsOneWidget);
    expect(find.text('帳號'), findsOneWidget);
    expect(find.text('語言'), findsOneWidget);
    expect(find.text('繁體中文'), findsOneWidget);

    expect(find.bySemanticsIdentifier('me.logout-button'), findsOneWidget);
    expect(find.text('登出'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('還沒有登入身分時身分卡不出現，其餘照常', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester);

    expect(find.bySemanticsIdentifier('me.logout-button'), findsOneWidget);
    expect(find.text('帳號'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('點還沒做的設定列會說尚未開放，不會換頁', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester);

    await tester.tap(find.text('推播通知'));
    await tester.pumpAndSettle();

    expect(find.text('這個設定尚未開放。'), findsOneWidget);
    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);

    handle.dispose();
  });
}

/// 記憶體 fake：「我的」只讀登入時留下的身分，不打任何端點。
class _FakeApiClient extends ApiClient {
  _FakeApiClient(this._session);

  final LoginResult? _session;

  @override
  LoginResult? get session => _session;
}
