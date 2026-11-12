import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_card.dart';
import 'package:tms_staff_app/core/widgets/tms_content_unavailable.dart';

void main() {
  Widget host(Widget child) {
    return CupertinoApp(home: CupertinoPageScaffold(child: child));
  }

  group('版型', () {
    testWidgets('不包卡片的版型不套白卡，而且把 minHeight 撐開', (tester) async {
      await tester.pumpWidget(
        host(
          const TmsContentUnavailable(
            id: 'notification.empty-state',
            copy: TmsEmptyStates.notifications,
            minHeight: TmsContentUnavailable.notificationMinHeight,
          ),
        ),
      );

      expect(find.byType(TmsCard), findsNothing);
      expect(find.text('目前沒有通知'), findsOneWidget);
      expect(find.text('派單、急件與退回都會出現在這裡。'), findsOneWidget);
      final box = tester.renderObject<RenderBox>(
        find.byType(TmsContentUnavailable),
      );
      expect(box.size.height, greaterThanOrEqualTo(460));
    });

    testWidgets('首頁版型包一張圓角 20 的白卡，上下內距 28、左右 20', (tester) async {
      await tester.pumpWidget(
        host(
          TmsContentUnavailable.inCard(
            id: 'home.empty-state',
            copy: TmsEmptyStates.housekeepingHome(
              snoozedCount: 0,
              doneCount: 0,
              poolHasItems: true,
            ),
          ),
        ),
      );

      final card = tester.widget<TmsCard>(find.byType(TmsCard));
      expect(card.style, TmsCardStyle.hero);
      expect(
        card.padding,
        const EdgeInsets.symmetric(vertical: 28, horizontal: 20),
      );
    });

    testWidgets('正向回饋的圖示是成功綠，其餘空狀態是灰的', (tester) async {
      await tester.pumpWidget(
        host(
          TmsContentUnavailable.inCard(
            id: 'home.empty-state',
            copy: TmsEmptyStates.housekeepingHome(
              snoozedCount: 0,
              doneCount: 8,
              poolHasItems: true,
            ),
          ),
        ),
      );
      expect(
        tester.widget<Icon>(find.byType(Icon)).color,
        TmsColors.systemGreen,
      );

      await tester.pumpWidget(
        host(
          TmsContentUnavailable.inCard(
            id: 'home.empty-state',
            copy: TmsEmptyStates.housekeepingHome(
              snoozedCount: 0,
              doneCount: 0,
              poolHasItems: true,
            ),
          ),
        ),
      );
      expect(
        tester.widget<Icon>(find.byType(Icon)).color,
        TmsColors.quaternaryLabel,
      );
    });

    testWidgets('文字與動作都有才畫按鈕，按下去會呼叫 onAction', (tester) async {
      var tapped = 0;
      final copy = TmsEmptyStates.housekeepingPool(
        filterApplied: true,
        poolHasItems: true,
      );

      await tester.pumpWidget(
        host(TmsContentUnavailable(id: 'pool.empty-state', copy: copy)),
      );
      expect(find.text(TmsEmptyStates.showAllCategoriesLabel), findsNothing);

      await tester.pumpWidget(
        host(
          TmsContentUnavailable(
            id: 'pool.empty-state',
            copy: copy,
            onAction: () => tapped++,
          ),
        ),
      );
      await tester.tap(find.text(TmsEmptyStates.showAllCategoriesLabel));
      expect(tapped, 1);
    });

    testWidgets('整塊的字讀成一句話，掛在空狀態自己的 semantics 節點上', (tester) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(
        host(
          const TmsContentUnavailable(
            id: 'notification.empty-state',
            copy: TmsEmptyStates.notifications,
          ),
        ),
      );

      final node = tester.getSemantics(
        find.bySemanticsIdentifier('notification.empty-state'),
      );
      expect(node.label, '目前沒有通知。派單、急件與退回都會出現在這裡。');
      handle.dispose();
    });
  });

  group('待認領文案', () {
    test('有篩選而整池還有單時，請員工換個分類並給切回全部的按鈕（Main.dc.html:5121）', () {
      final copy = TmsEmptyStates.housekeepingPool(
        filterApplied: true,
        poolHasItems: true,
      );
      expect(copy.title, '這個分類沒有待認領的單');
      expect(copy.description, '換個分類看看，或等新需求進來。');
      expect(copy.actionLabel, '顯示全部分類');
    });

    test('整池都是空的時候不給按鈕（Main.dc.html:5121）', () {
      final copy = TmsEmptyStates.housekeepingPool(
        filterApplied: true,
        poolHasItems: false,
      );
      expect(copy.title, '目前沒有待認領的單');
      expect(copy.description, '新需求進來時會推播通知你。');
      expect(copy.actionLabel, isNull);
    });

    test('工務兩套文案把「新需求」換成「新的工務單」（Main.dc.html:4093）', () {
      expect(
        TmsEmptyStates.engineeringPool(
          filterApplied: true,
          poolHasItems: true,
        ).description,
        '換個分類看看，或等新的工務單進來。',
      );
      expect(
        TmsEmptyStates.engineeringPool(
          filterApplied: false,
          poolHasItems: false,
        ).description,
        '新的工務單進來會推播通知你。',
      );
    });
  });

  group('首頁文案', () {
    test('手上沒有任務，池裡還有單時指路到待認領（Main.dc.html:5109）', () {
      final copy = TmsEmptyStates.housekeepingHome(
        snoozedCount: 0,
        doneCount: 0,
        poolHasItems: true,
      );
      expect(copy.title, '手上沒有任務');
      expect(copy.description, '到「待認領」認領新的單。');
      expect(copy.isPositive, isFalse);
    });

    test('今天的任務都完成了，說出完成單數並標成正向回饋（Main.dc.html:5109）', () {
      final copy = TmsEmptyStates.housekeepingHome(
        snoozedCount: 0,
        doneCount: 8,
        poolHasItems: true,
      );
      expect(copy.title, '今天的任務都完成了');
      expect(copy.description, '今天完成 8 單，到「待認領」認領新的單。');
      expect(copy.isPositive, isTrue);
    });

    test('池裡也沒有單時改講會推播通知（Main.dc.html:5113）', () {
      expect(
        TmsEmptyStates.housekeepingHome(
          snoozedCount: 0,
          doneCount: 0,
          poolHasItems: false,
        ).description,
        '新需求進來時會推播通知你。',
      );
    });

    test('手上還有延後的單時指路到我的任務，不講完成數（Main.dc.html:5114）', () {
      final copy = TmsEmptyStates.housekeepingHome(
        snoozedCount: 2,
        doneCount: 8,
        poolHasItems: true,
      );
      expect(copy.title, '目前沒有可以開始的單');
      expect(copy.description, '延後的單在下方「我的任務」，準備好再開始。');
      expect(copy.isPositive, isFalse);
    });

    test('工務首頁只分完修過與沒事兩種（Main.dc.html:4080）', () {
      final done = TmsEmptyStates.engineeringHome(
        doneCount: 3,
        poolHasItems: false,
      );
      expect(done.title, '今天的工務單都完修了');
      expect(done.description, '今天完修 3 張，新的工務單進來會推播通知你。');
      expect(done.isPositive, isTrue);

      final idle = TmsEmptyStates.engineeringHome(
        doneCount: 0,
        poolHasItems: true,
      );
      expect(idle.title, '手上沒有工務單');
      expect(idle.description, '到「待認領」認領新的單。');
    });
  });
}
