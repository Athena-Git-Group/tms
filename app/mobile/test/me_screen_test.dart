import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/connection_status.dart';
import 'package:tms_staff_app/core/widgets/tms_toggle.dart';
import 'package:tms_staff_app/features/me/me_screen.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';
import 'package:tms_staff_app/features/ticket/ticket_screen.dart';
import 'package:tms_staff_app/features/today/my_today_scope.dart';

/// 驗「我的」這一頁上員工自己的東西：身分卡、兩張工作磚、設定列，以及登出
/// 的三段流程。版面對照 `Main.dc.html:410-491`，登出對照
/// `Main.dc.html:4671-4674`。
void main() {
  /// 驗收機種 iPhone 17 的邏輯尺寸。測試預設的 800 × 600 比手機矮，整頁會有
  /// 一截捲在畫面外，而 `ListView` 不會建出捲到畫面外的列，登出與版本那兩列
  /// 就定位不到。
  const deviceSize = Size(402, 874);

  tearDown(() async {
    ApiClient.instance = ApiClient();
    ConnectionStatus.instance.reset();
    // 測項押進佇列的動作要在這裡送掉，否則待同步筆數會漏到下一個測項，
    // 讓它莫名其妙被登出攔阻擋住。
    await OfflineQueue.instance.flush();
  });

  const session = LoginResult(
    token: 't',
    expiresAt: '2026-11-08T00:00:00Z',
    staffId: 'staff-1',
    name: '陳小美',
    role: 'housekeeping',
    dept: 'housekeeping',
    mustChangePassword: false,
  );

  MyToday todayWith(
    List<String> states, {
    String? shift,
    List<int>? floors,
    String type = 'cleaning',
  }) =>
      MyToday(
        cleaningDone: 0,
        cleaningTotal: states.length,
        inspectedCount: 0,
        completedCount: 0,
        adHocCompleted: 0,
        remainingMinutes: 30 * states.length,
        nextTasks: const [],
        shift: shift,
        floors: floors,
        todayTasks: [
          for (var i = 0; i < states.length; i++)
            TodayTask(
              taskId: 'task-$i',
              roomNo: '30${i + 1}',
              title: '退房打掃',
              type: type,
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

  Future<void> pumpScreen(
    WidgetTester tester, {
    LoginResult? session,
    MyToday? today,
  }) async {
    await tester.binding.setSurfaceSize(deviceSize);
    ApiClient.instance = _FakeApiClient(session, today: today);
    await tester.pumpWidget(
      const CupertinoApp(home: MyTodayScope(child: MeScreen())),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('身分卡顯示名字、部門樓層與班別，點了進個人資料', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(
      tester,
      session: session,
      today: todayWith(const [], shift: 'morning', floors: const [3, 4]),
    );

    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);
    expect(find.text('陳小美'), findsOneWidget);
    // 頭像取名字的最後一個字，不是姓。
    expect(find.text('美'), findsOneWidget);
    expect(find.text('房務 · 3、4 樓'), findsOneWidget);
    expect(find.text('早班'), findsOneWidget);

    await tester.tap(find.bySemanticsIdentifier('me.identity-card'));
    await tester.pumpAndSettle();
    expect(find.text('個人資料'), findsOneWidget);
    expect(find.text('這個設定尚未開放。'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('後端沒給班別與樓層時，副標只剩部門一行', (tester) async {
    await pumpScreen(tester, session: session, today: todayWith(const []));

    expect(find.text('房務'), findsOneWidget);
    expect(find.text('早班'), findsNothing);
  });

  testWidgets('還沒有登入身分時身分卡不出現，其餘照常', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester);

    expect(find.bySemanticsIdentifier('me.identity-card'), findsNothing);
    expect(find.bySemanticsIdentifier('me.logout-button'), findsOneWidget);
    expect(find.text('帳號'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('工作磚兩張都在，數字還沒有資料所以不畫，點了說尚未開放', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester, session: session, today: todayWith(const []));

    expect(find.text('工作'), findsOneWidget);
    final handoverTile = find.bySemanticsIdentifier('me.work.handover-tile');
    expect(handoverTile, findsOneWidget);
    expect(
      find.bySemanticsIdentifier('me.work.lost-and-found-tile'),
      findsOneWidget,
    );
    // 磚上只有名稱一段文字：原型右上角那個數字與名稱底下那行說明，後端都還
    // 給不出來，填不出來就整段不畫。
    expect(
      find.descendant(of: handoverTile, matching: find.byType(Text)),
      findsOneWidget,
    );

    await tester.tap(handoverTile);
    await tester.pumpAndSettle();
    expect(find.text('交班單'), findsWidgets);
    expect(find.text('這個設定尚未開放。'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('「通知與顯示」第一列是即時動態開關，預設開、可以撥', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester, session: session, today: todayWith(const []));

    expect(find.text('即時動態'), findsOneWidget);
    final toggle = find.bySemanticsIdentifier('me.live-activity-toggle');
    expect(toggle, findsOneWidget);
    expect(tester.widget<TmsToggle>(find.byType(TmsToggle)).value, isTrue);

    await tester.tap(toggle);
    await tester.pumpAndSettle();
    expect(tester.widget<TmsToggle>(find.byType(TmsToggle)).value, isFalse);

    handle.dispose();
  });

  testWidgets('文字大小顯示跟隨 iPhone，推播通知右側留白', (tester) async {
    await pumpScreen(tester, session: session, today: todayWith(const []));

    expect(find.text('跟隨 iPhone'), findsOneWidget);
    expect(find.text('全部開啟'), findsNothing);
    expect(find.text('TMS 1.0.0（1）'), findsOneWidget);
  });

  testWidgets('離線時登出被攔住', (tester) async {
    final handle = tester.ensureSemantics();
    ConnectionStatus.instance.setOffline(true);
    await pumpScreen(tester, session: session, today: todayWith(const []));

    await tester.tap(find.bySemanticsIdentifier('me.logout-button'));
    await tester.pumpAndSettle();

    expect(find.text('還有資料沒同步'), findsOneWidget);
    expect(
      find.text('目前離線。進行中的紀錄還存在這支手機，恢復連線、同步完成後才能登出，避免資料遺失。'),
      findsOneWidget,
    );
    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('還有紀錄沒上傳時登出被攔住，而且說得出幾筆', (tester) async {
    final handle = tester.ensureSemantics();
    OfflineQueue.instance.enqueue(
      PendingOperation(
        kind: 'complete',
        idempotencyKey: 'key-1',
        send: (_) async {},
      ),
    );
    await pumpScreen(tester, session: session, today: todayWith(const []));

    await tester.tap(find.bySemanticsIdentifier('me.logout-button'));
    await tester.pumpAndSettle();

    expect(find.text('有 1 筆紀錄還在上傳，完成後才能登出，避免資料遺失。'), findsOneWidget);
    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('名下有 doing 的單時登出被攔住，可以直接前往那一間', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(
      tester,
      session: session,
      today: todayWith(const ['doing']),
    );

    await tester.tap(find.bySemanticsIdentifier('me.logout-button'));
    await tester.pumpAndSettle();

    expect(find.text('301 還在進行中'), findsOneWidget);
    expect(find.text('登出不會停止計時。請先完成或暫時中斷這張單，再登出。'), findsOneWidget);

    await tester.tap(
      find.bySemanticsIdentifier('me.logout-blocked-alert.open-task-button'),
    );
    await tester.pumpAndSettle();
    expect(find.byType(TaskScreen), findsOneWidget);

    handle.dispose();
  });

  testWidgets('名下進行中的是修繕單時，前往開的是工務單詳情而不是任務頁', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(
      tester,
      session: session,
      today: todayWith(const ['doing'], type: 'repair'),
    );

    await tester.tap(find.bySemanticsIdentifier('me.logout-button'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.bySemanticsIdentifier('me.logout-blocked-alert.open-task-button'),
    );
    await tester.pumpAndSettle();

    expect(find.byType(TicketScreen), findsOneWidget);
    expect(find.byType(TaskScreen), findsNothing);

    handle.dispose();
  });

  testWidgets('單都是待開始、延後、中斷、待查房（沒有 doing）時，確認後才登出', (tester) async {
    // 回歸：以前拿「今天還沒全做完／剩餘工時 > 0」當進行中，名下掛著延後單
    // 的人永遠登不出。
    final handle = tester.ensureSemantics();
    await pumpScreen(
      tester,
      session: session,
      today: todayWith(const ['accepted', 'deferred', 'interrupted', 'done']),
    );

    await tester.tap(find.bySemanticsIdentifier('me.logout-button'));
    await tester.pumpAndSettle();

    expect(find.text('要登出嗎？'), findsOneWidget);
    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);

    await tester.tap(
      find.bySemanticsIdentifier('me.logout-confirm-alert.confirm-button'),
    );
    await tester.pumpAndSettle();
    expect(find.bySemanticsIdentifier('me.screen'), findsNothing);

    handle.dispose();
  });

  testWidgets('確認框按取消就留在原地，沒有登出', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(
      tester,
      session: session,
      today: todayWith(const ['accepted']),
    );

    await tester.tap(find.bySemanticsIdentifier('me.logout-button'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.bySemanticsIdentifier('me.logout-confirm-alert.cancel-button'),
    );
    await tester.pumpAndSettle();

    expect(find.text('要登出嗎？'), findsNothing);
    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('點還沒做的設定列會說尚未開放，不會換頁', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester, today: todayWith(const []));

    await tester.tap(find.text('推播通知'));
    await tester.pumpAndSettle();

    expect(find.text('這個設定尚未開放。'), findsOneWidget);
    expect(find.bySemanticsIdentifier('me.screen'), findsOneWidget);

    handle.dispose();
  });
}

/// 記憶體 fake：「我的」只讀登入時留下的身分；身分卡的班別樓層與登出要問的
/// 今日工作台由測項餵進來，沒餵時表示測項不走這兩條路。
class _FakeApiClient extends ApiClient {
  _FakeApiClient(this._session, {this._today});

  final LoginResult? _session;
  final MyToday? _today;

  @override
  LoginResult? get session => _session;

  @override
  Future<MyToday> getMyToday() async => _today!;
}
