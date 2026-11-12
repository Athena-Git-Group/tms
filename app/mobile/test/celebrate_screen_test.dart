import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/celebrate/celebrate_screen.dart';

void main() {
  // 畫面上有無限循環的動畫時，`pumpAndSettle` 會等到逾時。
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
    bool celebrationEnabled = true,
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
          celebrationEnabled: celebrationEnabled,
          onNextStep: onNextStep,
          onBackHome: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('關掉氣氛呈現：飾帶與彩屑那一組不存在，今日進度跟著收起來，接續鍵照舊', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();

    await pumpCelebrate(
      tester,
      celebrationEnabled: false,
      nextStep: const TaskNextStep(
        kind: TaskNextStepKind.nextTask,
        taskId: 't-306',
        roomNo: '306',
      ),
    );

    expect(find.bySemanticsIdentifier('celebrate.celebration'), findsNothing);
    expect(find.bySemanticsIdentifier('celebrate.today-progress'), findsNothing);
    expect(labelOf(tester, 'celebrate.title'), '305 完成！');
    expect(labelOf(tester, 'celebrate.subline'), '已通知主管查房');
    expect(labelOf(tester, 'celebrate.actual-minutes'), '25 / 30 分');
    expect(labelOf(tester, 'celebrate.next-button'), '下一間：306');

    handle.dispose();
  });

  testWidgets('遮罩收下落在卡外面的點擊，底下那一頁按不到', (tester) async {
    var tappedUnderneath = false;

    await tester.pumpWidget(
      CupertinoApp(
        home: Stack(
          children: [
            Positioned.fill(
              child: GestureDetector(
                onTap: () => tappedUnderneath = true,
                child: const ColoredBox(color: CupertinoColors.white),
              ),
            ),
            Positioned.fill(
              child: CelebrateScreen(
                roomNo: '305',
                subline: '已通知主管查房',
                actualMinutes: 25,
                onBackHome: () {},
              ),
            ),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tapAt(const Offset(200, 8));
    await tester.pumpAndSettle();

    expect(tappedUnderneath, isFalse);
  });

  testWidgets('氣氛開著時飾帶與彩屑那一組在畫面上', (tester) async {
    final handle = tester.ensureSemantics();

    await pumpCelebrate(tester);

    expect(find.bySemanticsIdentifier('celebrate.celebration'), findsOneWidget);

    handle.dispose();
  });

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
