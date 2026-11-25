import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/theme.dart';
import 'package:tms_staff_app/core/widgets/tms_category_square.dart';
import 'package:tms_staff_app/core/widgets/tms_repair_ticket_row.dart';

void main() {
  EngineeringTicket ticket({String? returnReason, String state = 'doing'}) =>
      EngineeringTicket(
        ticketNo: 'W-0115',
        taskId: 'task-115',
        roomNo: '210',
        isPublicArea: false,
        title: '冷氣濾網更換',
        state: state,
        isUrgent: false,
        source: 'staff_report',
        verificationReturnReason: returnReason,
      );

  Future<void> pump(WidgetTester tester, EngineeringTicket t) =>
      tester.pumpWidget(
        CupertinoApp(
          home: CupertinoPageScaffold(
            child: TmsRepairTicketRow(ticket: t, onTap: () {}),
          ),
        ),
      );

  testWidgets('一般的單：label 依序是單號、地點、說明、狀態，副文是來源', (tester) async {
    final handle = tester.ensureSemantics();
    await pump(tester, ticket());
    final node = tester.getSemantics(
      find.bySemanticsIdentifier('engHome.in-hand.W-0115.card'),
    );
    expect(node.label, 'W-0115 210 冷氣濾網更換 維修中');
    expect(find.text('房務報修'), findsOneWidget);
    expect(find.text('驗收退回'), findsNothing);
    handle.dispose();
  });

  testWidgets('驗收退回的單：卡上有「驗收退回」標籤與原因，label 也帶', (tester) async {
    final handle = tester.ensureSemantics();
    await pump(tester, ticket(returnReason: '出風仍不冷'));
    final node = tester.getSemantics(
      find.bySemanticsIdentifier('engHome.in-hand.W-0115.card'),
    );
    expect(node.label, contains('驗收退回'));
    expect(find.text('驗收退回'), findsOneWidget);
    expect(find.text('出風仍不冷'), findsOneWidget);
    handle.dispose();
  });

  testWidgets('已結案的單跟已完修一樣用收尾的灰底方塊', (tester) async {
    for (final state in ['done', 'closed']) {
      await pump(tester, ticket(state: state));
      final square = tester.widget<TmsCategorySquare>(
        find.byType(TmsCategorySquare),
      );
      expect(
        square.palette.background,
        TmsColors.groupedBackground,
        reason: state,
      );
    }
  });
}
