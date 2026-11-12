import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/connection_status.dart';
import 'package:tms_staff_app/features/celebrate/celebrate_screen.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

void main() {
  // 畫面上有無限循環的動畫時，`pumpAndSettle` 會等到逾時。
  setUp(() {
    TestWidgetsFlutterBinding.ensureInitialized()
        .platformDispatcher
        .accessibilityFeaturesTestValue = const FakeAccessibilityFeatures(
      disableAnimations: true,
    );
  });

  tearDown(() {
    TestWidgetsFlutterBinding.ensureInitialized().platformDispatcher
        .clearAccessibilityFeaturesTestValue();
    ApiClient.instance = ApiClient();
    ConnectionStatus.instance.reset();
  });

  testWidgets('已中斷的單按「恢復處理」會送出 resume，畫面換成後端回的進行中那一筆', (
    tester,
  ) async {
    final client = _FakeApiClient(_task(state: 'interrupted'));
    ApiClient.instance = client;
    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '308', taskId: 't-308')),
    );
    await tester.pumpAndSettle();

    expect(find.text('恢復處理'), findsOneWidget);

    await tester.tap(find.bySemanticsIdentifier('task.primary-button'));
    await tester.pumpAndSettle();

    expect(client.resumedTaskIds, ['t-308']);
    expect(find.text('恢復處理'), findsNothing);
    expect(find.text('完成'), findsOneWidget);
    expect(find.text('中斷'), findsOneWidget);
  });

  testWidgets('做完一間推上來的慶祝卡是覆蓋層：底下那一頁還在畫面上', (tester) async {
    final client = _FakeApiClient(_task(state: 'doing', type: 'cleaning'));
    ApiClient.instance = client;
    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '302', taskId: 't-302')),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier('task.primary-button'));
    await tester.pumpAndSettle();

    expect(find.byType(CelebrateScreen), findsOneWidget);
    expect(find.bySemanticsIdentifier('task.screen'), findsOneWidget);
    expect(find.bySemanticsIdentifier('task.nav.302'), findsOneWidget);
    expect(
      ModalRoute.of(tester.element(find.byType(CelebrateScreen))),
      same(
        ModalRoute.of(
          tester.element(find.bySemanticsIdentifier('task.screen')),
        ),
      ),
    );
  });

  testWidgets('離線時按「恢復處理」擋在送出之前，提示講的是無法恢復處理', (tester) async {
    final client = _FakeApiClient(_task(state: 'interrupted'));
    ApiClient.instance = client;
    ConnectionStatus.instance.setOffline(true);
    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '308', taskId: 't-308')),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier('task.primary-button'));
    await tester.pumpAndSettle();

    expect(client.resumedTaskIds, isEmpty);
    expect(find.text('目前離線，無法恢復處理'), findsOneWidget);
  });

  testWidgets('備品單送達之後也給完成畫面，只是不帶今日進度', (tester) async {
    final client = _FakeApiClient(_task(state: 'doing'));
    ApiClient.instance = client;
    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '308', taskId: 't-308')),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier('task.primary-button'));
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('celebrate.screen'), findsOneWidget);
    expect(find.bySemanticsIdentifier('celebrate.today-progress'), findsNothing);
  });

  testWidgets('接力的那一間已經改派給別人時，誠實說它不在名下，不開那一頁', (tester) async {
    final client = _ReassignedNextApiClient(_task(state: 'doing', type: 'cleaning'));
    ApiClient.instance = client;
    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '302', taskId: 't-302')),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier('task.primary-button'));
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier('celebrate.next-button'));
    await tester.pumpAndSettle();

    expect(find.text('這一間已經不在你名下了'), findsOneWidget);
    expect(find.bySemanticsIdentifier('task.nav.308'), findsNothing);
  });
}

class _ReassignedNextApiClient extends _FakeApiClient {
  _ReassignedNextApiClient(super.task);

  @override
  LoginResult? get session => const LoginResult(
    token: 't',
    expiresAt: '2026-12-31T00:00:00Z',
    staffId: 'me',
    name: '阿美',
    role: 'housekeeping',
    mustChangePassword: false,
    dept: 'housekeeping',
  );

  @override
  Future<TaskDetail> getTask(String taskId) async {
    if (taskId == 't-308') {
      return TaskDetail(
        id: 't-308',
        state: 'accepted',
        type: 'cleaning',
        roomNo: '308',
        title: '308 退房清潔',
        assigneeId: 'someone-else',
      );
    }
    return super.getTask(taskId);
  }

  @override
  Future<TaskCompletion?> completeTask(
    String taskId, {
    List<String>? photoIds,
    String? resolutionType,
    String? idempotencyKey,
  }) async {
    final completion = await super.completeTask(
      taskId,
      photoIds: photoIds,
      resolutionType: resolutionType,
      idempotencyKey: idempotencyKey,
    );
    return TaskCompletion(
      task: completion!.task,
      nextStep: const TaskNextStep(
        kind: TaskNextStepKind.nextTask,
        taskId: 't-308',
        roomNo: '308',
      ),
    );
  }
}

TaskDetail _task({required String state, String type = 'amenity'}) {
  return TaskDetail(
    id: type == 'cleaning' ? 't-302' : 't-308',
    state: state,
    type: type,
    roomNo: type == 'cleaning' ? '302' : '308',
    title: type == 'cleaning' ? '302 退房打掃' : '308 補備品',
    estMin: 20,
    elapsedMinutes: 15,
    holdReason: state == 'interrupted' ? '備品車缺枕頭套，等補貨' : null,
  );
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient(this._task);

  TaskDetail _task;

  final List<String> resumedTaskIds = <String>[];

  final List<String> completedTaskIds = <String>[];

  @override
  Future<TaskDetail> getTask(String taskId) async => _task;

  @override
  Future<TaskDetail> resumeTask(String taskId) async {
    resumedTaskIds.add(taskId);
    _task = _task.copyWithStateForTest('doing');
    return _task;
  }

  @override
  Future<TaskCompletion?> completeTask(
    String taskId, {
    List<String>? photoIds,
    String? resolutionType,
    String? idempotencyKey,
  }) async {
    completedTaskIds.add(taskId);
    _task = _task.copyWithStateForTest('done');
    return TaskCompletion(task: _task);
  }

  @override
  Future<List<SopItem>> listSopItems() async => const <SopItem>[];

  @override
  Future<List<RepairTicketSummary>> listRepairTickets(String roomId) async =>
      const <RepairTicketSummary>[];
}

extension on TaskDetail {
  TaskDetail copyWithStateForTest(String state) => TaskDetail(
    id: id,
    state: state,
    type: type,
    roomNo: roomNo,
    title: title,
    estMin: estMin,
    elapsedMinutes: elapsedMinutes,
  );
}
