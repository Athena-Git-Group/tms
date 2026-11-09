import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/celebrate/celebrate_screen.dart';

/// 驗慶祝頁三種接續分支各自長什麼樣、每個要被讀值的節點讀到的字跟畫面上
/// 一致，而且沒有資料的那一段整段不出現。
///
/// 這幾個節點的 label 是驗收的斷言目標，版面怎麼排都不能動到它們。版面本身
/// 的數值對照設計原型 `Claude Design/Main.dc.html:1372-1409`。
void main() {
  // 彩屑是無限循環的飄浮動畫（`Main.dc.html:36` 的 `tmsFloat`），動態開著就
  // 永遠排得出下一幀，`pumpAndSettle` 會等到逾時而不是等到畫面靜止。
  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized()
        .platformDispatcher
        .accessibilityFeaturesTestValue = const FakeAccessibilityFeatures(
      disableAnimations: true,
    );
  });

  tearDown(() {
    TestWidgetsFlutterBinding.ensureInitialized().platformDispatcher
        .clearAccessibilityFeaturesTestValue();
  });

  String labelOf(WidgetTester tester, String id) =>
      tester.getSemantics(find.bySemanticsIdentifier(id)).label;

  Future<void> pumpCelebrate(
    WidgetTester tester, {
    String? roomNo = '305',
    String subline = '已通知主管查房',
    int actualMinutes = 25,
    int? estimateMinutes = 30,
    int? todayNth = 4,
    TaskNextStep? nextStep,
    bool queued = false,
    void Function(TaskNextStep step)? onNextStep,
  }) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: CelebrateScreen(
          roomNo: roomNo,
          subline: subline,
          actualMinutes: actualMinutes,
          estimateMinutes: estimateMinutes,
          todayNth: todayNth,
          nextStep: nextStep,
          queued: queued,
          onNextStep: onNextStep,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('還有下一間：標題帶房號、兩塊統計磚、實心鍵寫著下一間的房號', (tester) async {
    final handle = tester.ensureSemantics();

    await pumpCelebrate(
      tester,
      nextStep: const TaskNextStep(
        kind: TaskNextStepKind.nextTask,
        taskId: 't-306',
        roomNo: '306',
      ),
    );

    expect(find.bySemanticsIdentifier('celebrate.screen'), findsOneWidget);
    expect(labelOf(tester, 'celebrate.title'), '305 完成！');
    expect(labelOf(tester, 'celebrate.subline'), '已通知主管查房');
    expect(labelOf(tester, 'celebrate.actual-minutes'), '25 / 30 分');
    expect(labelOf(tester, 'celebrate.today-progress'), '第 4 單');
    expect(labelOf(tester, 'celebrate.next-button'), '下一間：306');
    expect(find.bySemanticsIdentifier('celebrate.all-done-note'), findsNothing);
    expect(find.bySemanticsIdentifier('celebrate.queued-note'), findsNothing);
    expect(find.text('回到首頁'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('都清完了：多一句提示，主按鈕改成去待認領且不帶房號', (tester) async {
    final handle = tester.ensureSemantics();

    await pumpCelebrate(
      tester,
      nextStep: const TaskNextStep(kind: TaskNextStepKind.allClear),
    );

    expect(
      labelOf(tester, 'celebrate.all-done-note'),
      '排定的房都清完了，有空可以到「待認領」幫忙接單。',
    );
    expect(labelOf(tester, 'celebrate.next-button'), '去「待認領」看看');

    handle.dispose();
  });

  testWidgets('離線完成：多一行說這一筆還押在手機上', (tester) async {
    final handle = tester.ensureSemantics();

    await pumpCelebrate(tester, todayNth: null, queued: true);

    expect(
      labelOf(tester, 'celebrate.queued-note'),
      '目前離線，已先記錄在手機，恢復連線後自動送出。',
    );
    // 離線時後端還沒算出去向，接續那一段整段不出現。
    expect(find.bySemanticsIdentifier('celebrate.next-button'), findsNothing);

    handle.dispose();
  });

  testWidgets('沒有預估分鐘時，用時那一塊不提預估兩個字', (tester) async {
    final handle = tester.ensureSemantics();

    await pumpCelebrate(tester, estimateMinutes: null);

    expect(labelOf(tester, 'celebrate.actual-minutes'), '25 分');
    expect(find.text('實際'), findsOneWidget);
    expect(find.text('實際／預估'), findsNothing);

    handle.dispose();
  });

  testWidgets('非打掃類沒有今日進度，右邊那一塊整個不出現', (tester) async {
    final handle = tester.ensureSemantics();

    await pumpCelebrate(
      tester,
      subline: '住客會收到完成通知',
      todayNth: null,
    );

    expect(find.bySemanticsIdentifier('celebrate.today-progress'), findsNothing);
    expect(find.text('今日進度'), findsNothing);

    handle.dispose();
  });

  testWidgets('按下接續鍵時，把後端給的那一份去向交回呼叫端', (tester) async {
    final handle = tester.ensureSemantics();
    const step = TaskNextStep(
      kind: TaskNextStepKind.nextTask,
      taskId: 't-306',
      roomNo: '306',
    );
    final taken = <TaskNextStep>[];

    await pumpCelebrate(tester, nextStep: step, onNextStep: taken.add);
    await tester.tap(find.bySemanticsIdentifier('celebrate.next-button'));
    await tester.pumpAndSettle();

    expect(taken, [step]);

    handle.dispose();
  });
}
