import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/test_backdoor.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

/// 驗任務頁的計時主角：後端給了本段起算時刻時計時走到秒並真的每秒往前跳，
/// 沒給時退回只顯示已做分鐘，而且員工離開畫面之後那支每秒醒來的計時器要停
/// 掉——沒停的話 flutter test 會在測試結束時報「有計時器還活著」。
void main() {
  TaskDetail task({
    required String state,
    required int elapsedMinutes,
    int? accumulatedSeconds,
    DateTime? segmentStartedAt,
    int? estMin,
    String? source,
  }) {
    return TaskDetail(
      id: 'task-1',
      state: state,
      elapsedMinutes: elapsedMinutes,
      accumulatedSeconds: accumulatedSeconds,
      segmentStartedAt: segmentStartedAt,
      estMin: estMin,
      source: source,
    );
  }

  Future<void> pumpHero(
    WidgetTester tester,
    TaskDetail detail, {
    DateTime Function()? clock,
  }) {
    return tester.pumpWidget(
      CupertinoApp(
        home: CupertinoPageScaffold(
          child: TaskTimerHero(task: detail, clock: clock),
        ),
      ),
    );
  }

  String heroText(WidgetTester tester) {
    return tester
        .widgetList<Text>(find.byType(Text))
        .first
        .data!;
  }

  testWidgets('後端還沒回起算時刻時，計時維持只顯示已做分鐘', (tester) async {
    await pumpHero(
      tester,
      task(state: 'doing', elapsedMinutes: 12, estMin: 30),
    );

    expect(heroText(tester), '12 分');

    // 時間過去一秒，字仍然不動：沒有起算時刻就沒有要逐秒重算的東西。
    await tester.pump(const Duration(seconds: 1));
    expect(heroText(tester), '12 分');
  });

  testWidgets('有起算時刻時，計時走到秒而且每秒往前跳', (tester) async {
    // 進入本段之前已累計 12 分鐘（720 秒），本段剛走 5 秒。
    final startedAt = DateTime(2026, 11, 8, 9);
    var now = startedAt.add(const Duration(seconds: 5));

    await pumpHero(
      tester,
      task(
        state: 'doing',
        elapsedMinutes: 12,
        accumulatedSeconds: 720,
        segmentStartedAt: startedAt,
        estMin: 30,
      ),
      clock: () => now,
    );

    expect(heroText(tester), '12:05');

    now = now.add(const Duration(seconds: 1));
    await tester.pump(const Duration(seconds: 1));
    expect(heroText(tester), '12:06');

    now = now.add(const Duration(seconds: 1));
    await tester.pump(const Duration(seconds: 1));
    expect(heroText(tester), '12:07');
  });

  testWidgets('超時膠囊整分才進位，不因為秒數跳動忽隱忽現', (tester) async {
    // 超時分鐘數照原型的 `overInfo` 無條件進位（`Main.dc.html:3155` 的
    // `max(1, ceil((sec - est * 60) / 60))`）：本段已走 40 分 10 秒、預估 35 分，
    // 超出去的 310 秒進位成 6 分。首頁那張進行中的卡用的是同一條式子。
    final startedAt = DateTime(2026, 11, 8, 9);
    var now = startedAt.add(const Duration(minutes: 40, seconds: 10));

    await pumpHero(
      tester,
      task(
        state: 'doing',
        elapsedMinutes: 40,
        accumulatedSeconds: 0,
        segmentStartedAt: startedAt,
        estMin: 35,
      ),
      clock: () => now,
    );

    expect(find.text('超時 6 分'), findsOneWidget);

    // 同一分鐘內秒數再怎麼跳，超時那句話不變也不消失。
    now = now.add(const Duration(seconds: 20));
    await tester.pump(const Duration(seconds: 1));
    expect(heroText(tester), '40:30');
    expect(find.text('超時 6 分'), findsOneWidget);

    // 超出去的秒數跨過下一個整分才進位成 7 分，膠囊始終都在。
    now = now.add(const Duration(seconds: 31));
    await tester.pump(const Duration(seconds: 1));
    expect(heroText(tester), '41:01');
    expect(find.text('超時 7 分'), findsOneWidget);
  });

  testWidgets('離開畫面之後，每秒醒來的計時器不再留著', (tester) async {
    await pumpHero(
      tester,
      task(
        state: 'doing',
        elapsedMinutes: 3,
        accumulatedSeconds: 180,
        segmentStartedAt: DateTime.now(),
        estMin: 30,
      ),
    );

    await tester.pumpWidget(
      const CupertinoApp(
        home: CupertinoPageScaffold(child: Text('別的畫面')),
      ),
    );
    await tester.pump(const Duration(seconds: 2));

    // 計時器沒取消的話，這個測試會在結束時被 flutter test 判定有未取消的
    // Timer 而失敗。
    expect(find.text('別的畫面'), findsOneWidget);
  });

  testWidgets('四個來源 enum 都有對應的白話說法', (tester) async {
    expect(taskSourceText('guest'), '住客掃碼');
    expect(taskSourceText('desk'), '櫃檯轉單');
    expect(taskSourceText('system'), '自動化規則');
    expect(taskSourceText('staff'), '房務報修');
  });

  testWidgets('計時以 accumulatedSeconds 當基準，正在跑的這一段只算一次', (tester) async {
    // 契約的走秒公式是 accumulatedSeconds + (現在 − segmentStartedAt)。進行中
    // 的單 elapsedMinutes 已含本段，拿它當基準再加本段會把本段算兩次：這個
    // 情境（本段之前累計 0 秒、本段已走 25 分鐘）就會顯示約兩倍的 50:00。
    final startedAt = DateTime(2026, 11, 9, 9);
    final now = startedAt.add(const Duration(minutes: 25));
    final detail = TaskDetail.fromJson(<String, dynamic>{
      'id': 'task-1',
      'state': 'doing',
      'elapsedMinutes': 25,
      'accumulatedSeconds': 0,
      'segmentStartedAt': startedAt.toIso8601String(),
      'estMin': 35,
    });

    await pumpHero(tester, detail, clock: () => now);

    expect(heroText(tester), '25:00');
  });

  testWidgets('超時提示跟著目前已做時間走，不停在上次取資料那一刻', (tester) async {
    // 後端回資料那一刻已做 40 分、預估 35 分，之後畫面開著不再向後端取資料。
    // 時間再走 10 分鐘，超時要跟著變成 15 分；停在取資料當下 elapsedMinutes
    // 算出來的 5 分就是這次要抓的凍結。
    final startedAt = DateTime(2026, 11, 9, 9);
    var now = startedAt.add(const Duration(minutes: 40));
    final detail = TaskDetail.fromJson(<String, dynamic>{
      'id': 'task-1',
      'state': 'doing',
      'elapsedMinutes': 40,
      'accumulatedSeconds': 0,
      'segmentStartedAt': startedAt.toIso8601String(),
      'estMin': 35,
    });

    await pumpHero(tester, detail, clock: () => now);
    expect(find.text('超時 5 分'), findsOneWidget);

    now = now.add(const Duration(minutes: 10));
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('超時 15 分'), findsOneWidget);
  });

  testWidgets('沒有外掛時鐘時，計時讀裝置生效的時間，測試注入的時刻也算數', (tester) async {
    // 自動化測試靠後門把裝置時間撥到指定那一刻（deviceNow）。計時若直接讀
    // DateTime.now()，撥快的那一段就不會反映在畫面上。
    addTearDown(TestHooks.instance.reset);
    final startedAt = DateTime(2026, 11, 9, 9);
    TestHooks.instance.injectedClockIso =
        startedAt.add(const Duration(minutes: 10)).toIso8601String();
    final detail = TaskDetail.fromJson(<String, dynamic>{
      'id': 'task-1',
      'state': 'doing',
      'elapsedMinutes': 10,
      'accumulatedSeconds': 0,
      'segmentStartedAt': startedAt.toIso8601String(),
    });

    expect(taskTimerText(detail), '10:00');
  });

  testWidgets('契約少了 segmentStartedAt 欄位時，任務詳情照樣解析得出來', (tester) async {
    final parsed = TaskDetail.fromJson(<String, dynamic>{
      'id': 'task-1',
      'state': 'doing',
      'elapsedMinutes': 12,
      'source': 'guest',
    });

    expect(parsed.segmentStartedAt, isNull);
    expect(parsed.source, 'guest');
    expect(taskTimerText(parsed), '12 分');
  });
}
