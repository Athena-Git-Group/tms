import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/manager/manager_ids.dart';
import 'package:tms_staff_app/features/ticket/ticket_manager_action_bar.dart';
import 'package:tms_staff_app/features/ticket/ticket_screen.dart';
import 'package:tms_staff_app/features/ticket/ticket_verify_fail_sheet.dart';
import 'package:tms_staff_app/features/ticket/ticket_verify_result_card.dart';

void main() {
  RepairVerificationOutcome outcome(String destination) =>
      RepairVerificationOutcome.fromJson({
        'taskId': 't-0108',
        'ticketNo': 'W-0108',
        'roomNo': '301',
        'isPublicArea': false,
        'result': 'failed',
        'destination': destination,
        'assigneeName': '阿強',
        'reason': '刷卡仍偶爾無反應',
        'decidedBy': '陳主管',
        'decidedAt': '2026-11-12T10:22:00+08:00',
      });

  test('推播與通知中心的視角：主管不分部門都是主管視角', () {
    LoginResult login(String role, String? dept) => LoginResult.fromJson({
      'token': 't',
      'expiresAt': '2026-11-12T23:00:00+08:00',
      'staffId': 's',
      'name': 'n',
      'dept': dept,
      'role': role,
      'mustChangePassword': false,
    });
    expect(
      TicketView.forSession(login('manager', 'engineering')),
      TicketView.manager,
    );
    expect(TicketView.forSession(login('manager', null)), TicketView.manager);
    expect(
      TicketView.forSession(login('staff', 'engineering')),
      TicketView.engineering,
    );
    expect(
      TicketView.forSession(login('staff', 'housekeeping')),
      TicketView.housekeeping,
    );
  });

  test('主管動作只在已完修出現', () {
    expect(TicketManagerActionBar.shows('done'), isTrue);
    for (final state in ['pool', 'accepted', 'doing', 'closed']) {
      expect(TicketManagerActionBar.shows(state), isFalse, reason: state);
    }
  });

  testWidgets('不通過面板原因去掉空白後是空的就不能送出', (tester) async {
    final sent = <String>[];
    await tester.pumpWidget(
      CupertinoApp(
        home: TicketVerifyFailSheet(
          ticketNo: 'W-0108',
          onSubmit: (reason) async {
            sent.add(reason);
            return null;
          },
        ),
      ),
    );
    final submit = find.bySemanticsIdentifier(
      ManagerIds.ticketFailSheetSubmitButton,
    );
    expect(
      tester.getSemantics(submit),
      isSemantics(hasEnabledState: true, isEnabled: false),
    );

    await tester.enterText(find.byType(CupertinoTextField), '   ');
    await tester.pump();
    await tester.tap(find.text('送出不通過'));
    await tester.pump();
    expect(sent, isEmpty);

    await tester.enterText(find.byType(CupertinoTextField), ' 刷卡仍偶爾無反應 ');
    await tester.pump();
    await tester.tap(find.text('送出不通過'));
    await tester.pump();
    expect(sent, ['刷卡仍偶爾無反應']);
  });

  testWidgets('結果卡：回到原處理人名下', (tester) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: TicketVerifyResultCard(
          outcome: outcome('assignee'),
          onBack: () {},
        ),
      ),
    );
    final label = tester
        .getSemantics(find.bySemanticsIdentifier(ManagerIds.ticketResult))
        .label;
    expect(label, contains('W-0108'));
    expect(label, contains('退回重修'));
    expect(label, contains('回到阿強名下，阿強會收到推播'));
    expect(label, contains('不通過原因：刷卡仍偶爾無反應'));
  });

  testWidgets('結果卡：原處理人已下班，回到工務待認領', (tester) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: TicketVerifyResultCard(outcome: outcome('pool'), onBack: () {}),
      ),
    );
    final label = tester
        .getSemantics(find.bySemanticsIdentifier(ManagerIds.ticketResult))
        .label;
    expect(label, contains('阿強已下班，這張單回到了工務待認領'));
  });
}
