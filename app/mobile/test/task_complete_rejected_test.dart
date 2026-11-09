import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/celebrate/celebrate_screen.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';

/// 驗「完成被後端拒收時顯示後端的原因，不會無聲」：按完成、後端回 4xx 時，
/// 畫面要跳出提示且文字就是後端的 message，不進完成畫面。
///
/// 為什麼重要：完成的送出走 OfflineQueue，連線正常時它把後端的拒絕原樣往上
/// 拋。這裡沒接住的話，員工按下完成只會看到畫面毫無反應——單子其實剛被主管
/// 作廢了，他卻以為做完了。
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

    // 禁止無聲：提示要出現，上面的字就是後端的 message，App 不自己湊文案。
    expect(find.bySemanticsIdentifier('task.busy-alert'), findsOneWidget);
    expect(find.text(backendMessage), findsOneWidget);
    // 被拒的完成不該慶祝，也不該標成待同步。
    expect(find.byType(CelebrateScreen), findsNothing);
    expect(find.bySemanticsIdentifier('task.pending-sync-label'), findsNothing);

    await tester.tap(
      find.bySemanticsIdentifier('task.busy-alert.dismiss-button'),
    );
    await tester.pumpAndSettle();

    // 關掉提示後還停在任務頁，主按鈕仍是「完成」——任務沒被當成做完。
    // 不能用 find.text：打掃步驟條上也有一段叫「完成」。
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

/// 完成一律被後端拒收的 fake：completeTask 丟 409，其餘讀取端點回固定資料。
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
