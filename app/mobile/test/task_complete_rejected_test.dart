import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/celebrate/celebrate_screen.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  testWidgets('完成被後端拒收時跳出後端的原因，不進完成畫面', (tester) async {
    const backendMessage = '這張單剛被主管作廢，不能完成';
    final client = _RejectingApiClient(backendMessage);
    ApiClient.instance = client;
    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '310', taskId: 't-310')),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier('task.primary-button'));
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('task.busy-alert'), findsOneWidget);
    expect(find.text(backendMessage), findsOneWidget);
    expect(find.byType(CelebrateScreen), findsNothing);
    expect(find.bySemanticsIdentifier('task.pending-sync-label'), findsNothing);

    await tester.tap(
      find.bySemanticsIdentifier('task.busy-alert.dismiss-button'),
    );
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('task.busy-alert'), findsNothing);
    expect(
      find.descendant(
        of: find.bySemanticsIdentifier('task.primary-button'),
        matching: find.text('完成'),
      ),
      findsOneWidget,
    );
    expect(find.byType(CelebrateScreen), findsNothing);
  });
}

class _RejectingApiClient extends ApiClient {
  _RejectingApiClient(this.message);

  final String message;

  @override
  Future<TaskDetail> getTask(String taskId) async => TaskDetail(
    id: 't-310',
    state: 'doing',
    type: 'cleaning',
    roomNo: '310',
    title: '310 退房打掃',
    estMin: 20,
    elapsedMinutes: 15,
  );

  @override
  Future<TaskCompletion?> completeTask(
    String taskId, {
    List<String>? photoIds,
    String? resolutionType,
    String? idempotencyKey,
  }) async {
    throw ApiException(
      statusCode: 409,
      body: '{"code":"task_cancelled","message":"$message"}',
    );
  }

  @override
  Future<List<SopItem>> listSopItems() async => const <SopItem>[];

  @override
  Future<List<RepairTicketSummary>> listRepairTickets(String roomId) async =>
      const <RepairTicketSummary>[];
}
