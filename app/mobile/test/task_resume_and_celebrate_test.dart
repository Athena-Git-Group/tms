import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/celebrate/celebrate_screen.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

/// 驗任務頁的兩件事：已中斷的單按「恢復處理」真的會送出去、做完一間之後的
/// 慶祝卡是浮在原本那一頁上的覆蓋層。
///
/// 為什麼這兩條重要：主按鈕的文字是依狀態算出來的，算得出文字不代表那顆按鈕
/// 接到了動作——「恢復處理」就曾經是一顆畫得出來、按下去什麼都不會發生的按
/// 鈕。慶祝卡則是一張鋪在半透明遮罩上的對話卡，路由只要是不透明的，遮罩底下
/// 就不會有東西被畫出來，員工看到的是一整片黑。
void main() {
  // 慶祝卡的彩屑是無限循環的飄浮動畫，動態開著就永遠排得出下一幀，
  // `pumpAndSettle` 會等到逾時而不是等到畫面靜止。
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
    // 回到進行中之後，主按鈕換成「完成」、次要按鈕換成「中斷」。
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
    // 卡在這一頁自己的 Stack 上，所以底下那一頁仍然在畫面樹與 accessibility
    // 樹上。做成路由的話 Flutter 的 `ModalBarrier` 會把底下那一頁的 semantics
    // 整批擋掉（`BlockSemantics`），遮罩底下是哪一間房就讀不到了。
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

/// 記憶體 fake：記下 resume／complete 打了哪幾張單，附帶的讀取端點回空。
class _FakeApiClient extends ApiClient {
  _FakeApiClient(this._task);

  TaskDetail _task;

  final List<String> resumedTaskIds = <String>[];

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
