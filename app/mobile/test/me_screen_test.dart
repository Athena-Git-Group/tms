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

  MyToday todayWith(List<String> states) => MyToday(
    cleaningDone: 0,
    cleaningTotal: states.length,
    inspectedCount: 0,
    completedCount: 0,
    adHocCompleted: 0,
    remainingMinutes: 30 * states.length,
    zeroReturnStreakDays: 0,
    nextTasks: const [],
    todayTasks: [
      for (var i = 0; i < states.length; i++)
        TodayTask(
          taskId: 'task-$i',
          roomNo: '30${i + 1}',
          title: '退房打掃',
          type: 'cleaning',
          state: states[i],
          redoCount: 0,
          estMin: 30,
          workedMinutes: 0,
          holdReason: null,
          inspection: null,
          inspectionWaived: false,
        ),
    ],
  );

  testWidgets('名下有 doing 的單時登出被攔住', (tester) async {
    final handle = tester.ensureSemantics();
    ApiClient.instance = _FakeApiClient(null, today: todayWith(['doing']));
    await tester.pumpWidget(const CupertinoApp(home: MeScreen()));
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier('me.logout-button'));
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('me.logout-blocked-alert'), findsOneWidget);
    expect(find.text('還有進行中的任務，請先完成或中斷再登出'), findsOneWidget);
    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('單都是待開始、延後、中斷、待查房（沒有 doing）時可以登出', (tester) async {
    // 回歸：以前拿「今天還沒全做完／剩餘工時 > 0」當進行中，名下掛著延後單
    // 的人永遠登不出。
    final handle = tester.ensureSemantics();
    ApiClient.instance = _FakeApiClient(
      null,
      today: todayWith(['accepted', 'deferred', 'interrupted', 'done']),
    );
    await tester.pumpWidget(const CupertinoApp(home: MeScreen()));
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier('me.logout-button'));
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('me.logout-blocked-alert'), findsNothing);
    expect(find.bySemanticsIdentifier('me.screen'), findsNothing);

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

/// 記憶體 fake：「我的」只讀登入時留下的身分；登出守衛要問的今日工作台
/// 由測項餵進來，沒餵時表示測項不走登出。
class _FakeApiClient extends ApiClient {
  _FakeApiClient(this._session, {this._today});

  final LoginResult? _session;
  final MyToday? _today;

  @override
  LoginResult? get session => _session;

  @override
  Future<MyToday> getMyToday() async => _today!;
}
