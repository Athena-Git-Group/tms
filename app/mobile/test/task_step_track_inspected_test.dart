import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/widgets/tms_step_track.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

/// 驗「查房通過之後步驟條四步全完成」（`Main.dc.html:4270-4272`）。
///
/// 查房通過代表整張單結案，沒有哪一步還停著。最後一步畫成「目前」的話，員工
/// 會以為查房還在進行、這間房還不能賣。
void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  Future<List<TmsStepStatus>> pumpStatuses(
    WidgetTester tester,
    String state,
  ) async {
    ApiClient.instance = _FixedTaskApiClient(state);
    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '310', taskId: 't-310')),
    );
    await tester.pumpAndSettle();
    return tester
        .widget<TmsStepTrack>(find.byType(TmsStepTrack))
        .steps
        .map((step) => step.status)
        .toList();
  }

  testWidgets('查房通過時四步全部是完成，沒有「目前」那一步', (tester) async {
    expect(
      await pumpStatuses(tester, 'inspected'),
      List.filled(4, TmsStepStatus.done),
    );
  });

  testWidgets('還沒查房時，最後一步仍然是目前停住的那一步', (tester) async {
    expect(await pumpStatuses(tester, 'done'), const [
      TmsStepStatus.done,
      TmsStepStatus.done,
      TmsStepStatus.current,
      TmsStepStatus.pending,
    ]);
  });
}

/// 固定回一張打掃任務的假後端，state 由測試指定。
class _FixedTaskApiClient extends ApiClient {
  _FixedTaskApiClient(this.state);

  final String state;

  @override
  Future<TaskDetail> getTask(String taskId) async => TaskDetail(
    id: 't-310',
    state: state,
    type: 'cleaning',
    roomNo: '310',
    title: '310 退房打掃',
    estMin: 20,
    elapsedMinutes: 18,
  );

  @override
  Future<List<SopItem>> listSopItems() async => const <SopItem>[];

  @override
  Future<List<RepairTicketSummary>> listRepairTickets(String roomId) async =>
      const <RepairTicketSummary>[];
}
