import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/widgets/tms_step_track.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  Future<List<TmsStepStatus>> pumpStatuses(
    WidgetTester tester, {
    required String state,
    required String type,
  }) async {
    ApiClient.instance = _FixedTaskApiClient(state: state, type: type);
    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '405', taskId: 't-405')),
    );
    await tester.pumpAndSettle();
    return tester
        .widget<TmsStepTrack>(find.byType(TmsStepTrack))
        .steps
        .map((step) => step.status)
        .toList();
  }

  String stepTrackLabel(WidgetTester tester) => tester
      .getSemantics(find.bySemanticsIdentifier('task.step-track'))
      .label;

  testWidgets('405 加毛巾還在待認領，步驟條每一步都未到，標示待認領', (tester) async {
    final handle = tester.ensureSemantics();
    expect(
      await pumpStatuses(tester, state: 'pool', type: 'amenity'),
      List.filled(3, TmsStepStatus.pending),
    );
    expect(stepTrackLabel(tester), '待認領');
    handle.dispose();
  });

  testWidgets('還在待認領的打掃單，四步也都未到', (tester) async {
    expect(
      await pumpStatuses(tester, state: 'pool', type: 'cleaning'),
      List.filled(4, TmsStepStatus.pending),
    );
  });

  testWidgets('有人接了之後，步驟條停在已接', (tester) async {
    final handle = tester.ensureSemantics();
    expect(await pumpStatuses(tester, state: 'accepted', type: 'amenity'), const [
      TmsStepStatus.current,
      TmsStepStatus.pending,
      TmsStepStatus.pending,
    ]);
    expect(stepTrackLabel(tester), '已接');
    handle.dispose();
  });
}

class _FixedTaskApiClient extends ApiClient {
  _FixedTaskApiClient({required this.state, required this.type});

  final String state;
  final String type;

  @override
  Future<TaskDetail> getTask(String taskId) async => TaskDetail(
    id: 't-405',
    state: state,
    type: type,
    roomNo: '405',
    title: '加毛巾 ×2',
    estMin: 5,
    elapsedMinutes: 0,
  );

  @override
  Future<List<SopItem>> listSopItems() async => const <SopItem>[];

  @override
  Future<List<RepairTicketSummary>> listRepairTickets(String roomId) async =>
      const <RepairTicketSummary>[];
}
