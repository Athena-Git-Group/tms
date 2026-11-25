import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/manager/manager_ids.dart';
import 'package:tms_staff_app/features/ticket/ticket_screen.dart';
import 'package:tms_staff_app/features/ticket/ticket_verify_fail_sheet.dart';

class _FakeApi extends ApiClient {
  @override
  Future<RepairTicketDetail> readRepairTicket(String taskId) async =>
      const RepairTicketDetail(
        taskId: 't-0108',
        ticketNo: 'W-0108',
        roomNo: '301',
        category: 'door_lock',
        isUrgent: false,
        state: 'done',
        reporterName: '阿芳',
        assigneeName: '阿強',
        resolutionType: 'fixed',
      );

  @override
  Future<RepairVerificationOutcome> verifyRepairTicket({
    required String taskId,
    required String result,
    String? reason,
    required String idempotencyKey,
  }) async =>
      RepairVerificationOutcome(
        taskId: taskId,
        ticketNo: 'W-0108',
        roomNo: '301',
        isPublicArea: false,
        result: result,
        destination: 'assignee',
        assigneeName: '阿強',
        reason: reason,
        decidedBy: '陳主管',
        decidedAt: '2026-11-25T10:00:00+08:00',
      );
}

void main() {
  late ApiClient original;

  setUp(() {
    original = ApiClient.instance;
    ApiClient.instance = _FakeApi();
  });

  tearDown(() => ApiClient.instance = original);

  testWidgets('不通過送出後，面板收起動畫播完才換成結果卡', (tester) async {
    await tester.pumpWidget(
      const CupertinoApp(
        home: TicketScreen(view: TicketView.manager, taskId: 't-0108'),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier(ManagerIds.ticketFailButton));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(CupertinoTextField), '刷卡仍偶爾無反應');
    await tester.pump();
    await tester.tap(find.text('送出不通過'));

    var sawSheetClosing = false;
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 16));
      final sheetStillMounted =
          find.byType(TicketVerifyFailSheet).evaluate().isNotEmpty;
      final resultShown =
          find.bySemanticsIdentifier(ManagerIds.ticketResult).evaluate().isNotEmpty;
      if (sheetStillMounted) {
        sawSheetClosing = true;
        expect(resultShown, isFalse, reason: '面板還在畫面樹裡時不能先換掉底下的畫面');
      }
    }
    await tester.pumpAndSettle();

    expect(sawSheetClosing, isTrue);
    expect(find.byType(TicketVerifyFailSheet), findsNothing);
    expect(find.bySemanticsIdentifier(ManagerIds.ticketResult), findsOneWidget);
  });
}
