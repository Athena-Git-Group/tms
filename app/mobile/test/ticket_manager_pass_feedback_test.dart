import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/connection_status.dart';
import 'package:tms_staff_app/features/manager/manager_ids.dart';
import 'package:tms_staff_app/features/manager/manager_queue_screen.dart';
import 'package:tms_staff_app/features/manager/manager_queues_store.dart';
import 'package:tms_staff_app/core/landing.dart';
import 'package:tms_staff_app/core/widgets/tms_toast.dart';
import 'package:tms_staff_app/features/ticket/ticket_screen.dart';

class _FakeApi extends ApiClient {
  _FakeApi();

  bool verified = false;

  @override
  Future<RepairTicketDetail> readRepairTicket(String taskId) async =>
      RepairTicketDetail(
        taskId: 't-0108',
        ticketNo: 'W-0108',
        roomNo: '301',
        category: 'door_lock',
        isUrgent: false,
        state: verified ? 'closed' : 'done',
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
  }) async {
    verified = true;
    return RepairVerificationOutcome.fromJson({
      'taskId': 't-0108',
      'ticketNo': 'W-0108',
      'roomNo': '301',
      'isPublicArea': false,
      'result': 'passed',
      'destination': 'closed',
      'assigneeName': '阿強',
      'decidedBy': '陳主管',
      'decidedAt': '2026-11-12T10:22:00+08:00',
    });
  }

  @override
  Future<ManagerQueues> getManagerQueues() async =>
      ManagerQueues.fromJson(const {'counts': {}, 'verify': []});
}

void main() {
  late _FakeApi api;
  late ApiClient original;

  setUp(() {
    original = ApiClient.instance;
    api = _FakeApi();
    ApiClient.instance = api;
    ConnectionStatus.instance.setOffline(false);
  });

  tearDown(() {
    ConnectionStatus.instance.reset();
    ApiClient.instance = original;
  });

  Future<void> letToastExpire(WidgetTester tester) async {
    await tester.pump(TmsToast.lifetime + const Duration(milliseconds: 100));
    await tester.pumpAndSettle();
  }

  testWidgets('從推播等其他入口進來：通過後留在詳情頁，就地跳出已結案提示', (tester) async {
    await tester.pumpWidget(
      const CupertinoApp(
        home: TicketScreen(view: TicketView.manager, taskId: 't-0108'),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier(ManagerIds.ticketPassButton));
    await tester.pumpAndSettle();

    expect(find.text('W-0108 已結案'), findsOneWidget);
    expect(find.byType(TicketScreen), findsOneWidget);
    expect(
      find.bySemanticsIdentifier(ManagerIds.ticketPassButton),
      findsNothing,
    );

    await letToastExpire(tester);
    expect(find.text('W-0108 已結案'), findsNothing);
  });

  testWidgets('從待驗收清單進來：通過後回到清單，提示顯示在清單頁上', (tester) async {
    final store = ManagerQueuesStore()
      ..value = ManagerQueues.fromJson(const {
        'counts': {'verify': 1},
        'verify': [
          {
            'taskId': 't-0108',
            'roomNo': '301',
            'isPublicArea': false,
            'title': '門鎖電池更換',
            'waitingSince': '2026-11-10T09:48:00+08:00',
            'waitingSinceIsToday': true,
            'ticketNo': 'W-0108',
            'category': 'lock',
            'origin': 'reported',
            'resolutionType': 'fixed',
            'wasVerificationFailed': false,
          },
        ],
      });

    await tester.pumpWidget(
      CupertinoApp(
        home: ManagerQueueScreen(
          kind: ManagerKind.engineering,
          queue: 'verify',
          store: store,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('W-0108'));
    await tester.pumpAndSettle();
    expect(find.byType(TicketScreen), findsOneWidget);

    await tester.tap(find.bySemanticsIdentifier(ManagerIds.ticketPassButton));
    await tester.pumpAndSettle();

    expect(find.byType(TicketScreen), findsNothing);
    expect(find.byType(ManagerQueueScreen), findsOneWidget);
    expect(find.text('W-0108 已結案'), findsOneWidget);

    await letToastExpire(tester);
    expect(find.text('W-0108 已結案'), findsNothing);
  });
}
