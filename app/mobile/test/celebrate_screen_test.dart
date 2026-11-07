import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/features/celebrate/celebrate_screen.dart';

/// 驗慶祝頁上的四個節點讀得到、讀到的字跟畫面上一致，而且沒有預估分鐘的
/// 任務不會多出一塊空的統計磚。
///
/// 這幾個節點的 label 是驗收的斷言目標，版面怎麼排都不能動到它們。
void main() {
  String labelOf(WidgetTester tester, String id) =>
      tester.getSemantics(find.bySemanticsIdentifier(id)).label;

  testWidgets('有預估分鐘時，實際與預估各一塊統計磚', (tester) async {
    final handle = tester.ensureSemantics();

    await tester.pumpWidget(
      const CupertinoApp(
        home: CelebrateScreen(actualMinutes: 25, estimateMinutes: 30),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('celebrate.screen'), findsOneWidget);
    expect(labelOf(tester, 'celebrate.title'), '這間做完了！');
    expect(labelOf(tester, 'celebrate.actual-minutes'), '實際 25 分');
    expect(labelOf(tester, 'celebrate.estimate-minutes'), '預估 30 分');
    expect(find.bySemanticsIdentifier('celebrate.back-home-button'),
        findsOneWidget);
    expect(find.text('回到首頁'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('沒有預估分鐘時，預估那一塊整個不出現', (tester) async {
    final handle = tester.ensureSemantics();

    await tester.pumpWidget(
      const CupertinoApp(home: CelebrateScreen(actualMinutes: 25)),
    );
    await tester.pumpAndSettle();

    expect(labelOf(tester, 'celebrate.actual-minutes'), '實際 25 分');
    expect(
      find.bySemanticsIdentifier('celebrate.estimate-minutes'),
      findsNothing,
    );

    handle.dispose();
  });
}
