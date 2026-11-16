import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/connection_status.dart';
import 'package:tms_staff_app/core/system_status.dart';
import 'package:tms_staff_app/features/manager/inspection_screen.dart';
import 'package:tms_staff_app/features/manager/manager_ids.dart';
import 'package:tms_staff_app/features/ticket/ticket_screen.dart';
import 'package:tms_staff_app/features/ticket/ticket_verify_fail_sheet.dart';

class _FakeApi extends ApiClient {
  int inspectionCalls = 0;
  int verifyCalls = 0;

  @override
  Future<List<SopItem>> listSopItems() async => const [
    SopItem(id: 's1', appliesTo: 'inspection', itemKey: 'bed', label: '床鋪平整'),
  ];

  @override
  Future<InspectionOutcome> createInspection({
    required String taskId,
    required List<InspectionItemJudgement> items,
    required String idempotencyKey,
  }) async {
    inspectionCalls++;
    throw StateError('離線時不該送出查房結果');
  }

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
  }) async {
    verifyCalls++;
    throw StateError('離線時不該送出驗收');
  }
}

void main() {
  late _FakeApi api;
  late ApiClient original;

  setUp(() {
    original = ApiClient.instance;
    api = _FakeApi();
    ApiClient.instance = api;
    ConnectionStatus.instance.setOffline(true);
  });

  tearDown(() {
    ConnectionStatus.instance.reset();
    ApiClient.instance = original;
  });

  testWidgets('離線時送出查房結果被擋在送出之前，顯示離線提示', (tester) async {
    await tester.pumpWidget(
      CupertinoApp(
        home: InspectionScreen(
          item: const InspectionQueueItem(
            taskId: 't-301',
            roomNo: '301',
            isPublicArea: false,
            title: '退房清潔',
            waitingSince: '2026-11-12T09:00:00+08:00',
            waitingSinceIsToday: true,
            cleanerName: '阿美',
            redoCount: 0,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('通過').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('送出查房結果'));
    await tester.pumpAndSettle();

    expect(find.text(offlineOnlyCompleteMessage), findsOneWidget);
    expect(api.inspectionCalls, 0);
  });

  testWidgets('離線時按通過被擋在送出之前，顯示離線提示', (tester) async {
    await tester.pumpWidget(
      const CupertinoApp(
        home: TicketScreen(view: TicketView.manager, taskId: 't-0108'),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier(ManagerIds.ticketPassButton));
    await tester.pumpAndSettle();

    expect(find.text(offlineOnlyCompleteMessage), findsOneWidget);
    expect(api.verifyCalls, 0);
  });

  testWidgets('離線時送出不通過被擋下，面板裡寫好的原因留著', (tester) async {
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

    await tester.enterText(find.byType(CupertinoTextField), '刷卡仍偶爾無反應');
    await tester.pump();
    await tester.tap(find.text('送出不通過'));
    await tester.pumpAndSettle();

    expect(find.text(offlineOnlyCompleteMessage), findsOneWidget);
    expect(sent, isEmpty);
    expect(
      tester
          .widget<CupertinoTextField>(find.byType(CupertinoTextField))
          .controller!
          .text,
      '刷卡仍偶爾無反應',
    );
  });
}
