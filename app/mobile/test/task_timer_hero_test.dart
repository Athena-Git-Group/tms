import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

/// 驗任務頁的計時主角：後端給了本段起算時刻時計時走到秒並真的每秒往前跳，
/// 沒給時退回只顯示已做分鐘，而且員工離開畫面之後那支每秒醒來的計時器要停
/// 掉——沒停的話 flutter test 會在測試結束時報「有計時器還活著」。
void main() {
  TaskDetail task({
    required String state,
    required int elapsedMinutes,
    DateTime? segmentStartedAt,
    int? estMin,
    String? source,
  }) {
    return TaskDetail(
      id: 'task-1',
      state: state,
      elapsedMinutes: elapsedMinutes,
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
    final startedAt = DateTime(2026, 11, 8, 9);
    var now = startedAt.add(const Duration(seconds: 5));

    await pumpHero(
      tester,
      task(
        state: 'doing',
        elapsedMinutes: 12,
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

  testWidgets('超時膠囊看已做分鐘，不因為秒數跨過整分而閃爍', (tester) async {
    final startedAt = DateTime(2026, 11, 8, 9);
    var now = startedAt.add(const Duration(seconds: 59));

    await pumpHero(
      tester,
      task(
        state: 'doing',
        elapsedMinutes: 40,
        segmentStartedAt: startedAt,
        estMin: 35,
      ),
      clock: () => now,
    );

    expect(find.text('超時 5 分'), findsOneWidget);

    // 秒數跨過整分，計時的字會變，超時那句話不能跟著變。
    now = now.add(const Duration(seconds: 1));
    await tester.pump(const Duration(seconds: 1));
    expect(heroText(tester), '41:00');
    expect(find.text('超時 5 分'), findsOneWidget);

    now = now.add(const Duration(seconds: 1));
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('超時 5 分'), findsOneWidget);
  });

  testWidgets('離開畫面之後，每秒醒來的計時器不再留著', (tester) async {
    await pumpHero(
      tester,
      task(
        state: 'doing',
        elapsedMinutes: 3,
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
