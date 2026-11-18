import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/test_backdoor.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

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

    await tester.pump(const Duration(seconds: 1));
    expect(heroText(tester), '12 分');
  });

  testWidgets('有起算時刻時，計時走到秒而且每秒往前跳', (tester) async {
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

  testWidgets('超時膠囊跟著計時同一套捨去，不因為秒數跳動忽隱忽現', (tester) async {
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

    expect(find.text('超時 5 分'), findsOneWidget);

    now = now.add(const Duration(seconds: 20));
    await tester.pump(const Duration(seconds: 1));
    expect(heroText(tester), '40:30');
    expect(find.text('超時 5 分'), findsOneWidget);

    now = now.add(const Duration(seconds: 31));
    await tester.pump(const Duration(seconds: 1));
    expect(heroText(tester), '41:01');
    expect(find.text('超時 6 分'), findsOneWidget);
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

    expect(find.text('別的畫面'), findsOneWidget);
  });

  testWidgets('四個來源 enum 都有對應的白話說法', (tester) async {
    expect(taskSourceText('guest'), '住客掃碼');
    expect(taskSourceText('desk'), '櫃檯轉單');
    expect(taskSourceText('system'), '自動化規則');
    expect(taskSourceText('staff'), '房務報修');
  });

  testWidgets('計時以 accumulatedSeconds 當基準，正在跑的這一段只算一次', (tester) async {
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
