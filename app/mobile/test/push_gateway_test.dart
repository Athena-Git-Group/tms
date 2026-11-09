import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/push_gateway.dart';
import 'package:tms_staff_app/features/home/home_screen.dart';
import 'package:tms_staff_app/features/pool/pool_screen.dart';
import 'package:tms_staff_app/features/task/task_screen.dart';
import 'package:tms_staff_app/features/today/my_today_scope.dart';

/// 對應「F3 點開推播的去處」：這三條各驗一個骨架環節——
/// 待認領畫面能標亮指定房號、推播 payload 能把 App 導到正確畫面、
/// 任務頁能認出「工務已修好」並露出恢復提示列。
///
/// 畫面資料來源是 [ApiClient]（真後端），測試裡換成記憶體 fake，
/// 不再透過測試後門灌 fixture。
void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  testWidgets('待認領畫面渲染池中卡片並標亮指定房號', (tester) async {
    final handle = tester.ensureSemantics();

    ApiClient.instance = _FakeApiClient(
      pool: [
        _poolItem(taskId: 'task-316', roomNo: '316', title: '衣架 x4'),
        _poolItem(taskId: 'task-317', roomNo: '317', title: '退房清潔'),
      ],
    );

    await tester.pumpWidget(
      const CupertinoApp(home: PoolScreen(highlightRoomNo: '316')),
    );
    await tester.pump();

    expect(find.bySemanticsIdentifier('pool.screen'), findsOneWidget);
    expect(find.bySemanticsIdentifier('pool.card.316--highlighted'), findsOneWidget);
    expect(find.bySemanticsIdentifier('pool.card.316'), findsNothing);
    expect(find.bySemanticsIdentifier('pool.card.317'), findsOneWidget);
    expect(find.bySemanticsIdentifier('pool.card.317--highlighted'), findsNothing);

    handle.dispose();
  });

  testWidgets('push payload dest=task room=305 把 App 導到 305 的任務頁', (tester) async {
    final handle = tester.ensureSemantics();

    final navigatorKey = GlobalKey<NavigatorState>();
    PushGateway.instance.attach(navigatorKey);

    ApiClient.instance = _FakeApiClient(
      pool: [_poolItem(taskId: 'task-305', roomNo: '305', title: '退房清潔')],
      tasksById: {
        'task-305': const TaskDetail(
          id: 'task-305',
          state: 'doing',
          roomNo: '305',
          title: '退房清潔',
        ),
      },
    );

    await tester.pumpWidget(
      CupertinoApp(
        navigatorKey: navigatorKey,
        // 今日進度取在外殼那一層，首頁自己不再建 scope；這個測試單獨渲染
        // 首頁，所以要自己補上外殼平常提供的那一層。
        home: const MyTodayScope(child: HomeScreen()),
      ),
    );
    await tester.pump();

    expect(find.bySemanticsIdentifier('home.screen'), findsOneWidget);

    PushGateway.instance.handlePushPayload(const {'dest': 'task', 'room': '305'});
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('task.screen'), findsOneWidget);
    expect(find.text('305 的任務頁'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('push payload dest=ticket taskId 把 App 導到工務單詳情，不是任務頁', (tester) async {
    final handle = tester.ensureSemantics();

    final navigatorKey = GlobalKey<NavigatorState>();
    PushGateway.instance.attach(navigatorKey);

    // 修繕任務的推播帶 kind=ticket＋該修繕任務的 task id（contracts PushTarget），
    // App 照 kind 機械導頁，不先打 GET /tasks/{id} 查類別——所以 fake 的
    // tasksById 刻意不給這張單，getTask 被呼叫會 404 讓導頁壞掉、測試變紅。
    ApiClient.instance = _FakeApiClient(
      ticketsById: {
        'task-w0620': const RepairTicketDetail(
          taskId: 'task-w0620',
          ticketNo: 'W-0620',
          roomNo: '202',
          category: 'other',
          isUrgent: false,
          state: 'doing',
          reporterName: '小葉',
        ),
      },
    );

    await tester.pumpWidget(
      CupertinoApp(
        navigatorKey: navigatorKey,
        home: const MyTodayScope(child: HomeScreen()),
      ),
    );
    await tester.pump();

    PushGateway.instance
        .handlePushPayload(const {'dest': 'ticket', 'taskId': 'task-w0620'});
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('ticket.screen'), findsOneWidget);
    expect(find.textContaining('W-0620'), findsWidgets);
    expect(find.bySemanticsIdentifier('task.screen'), findsNothing);

    handle.dispose();
  });

  testWidgets('已中斷且停住原因含工務已修好時，任務頁顯示恢復提示列', (tester) async {
    final handle = tester.ensureSemantics();

    ApiClient.instance = _FakeApiClient(
      pool: [_poolItem(taskId: 'task-305', roomNo: '305', title: '退房清潔')],
      tasksById: {
        'task-305': const TaskDetail(
          id: 'task-305',
          state: 'interrupted',
          roomNo: '305',
          title: '退房清潔',
          elapsedMinutes: 15,
          holdReason: '工務已修好：水電',
        ),
      },
    );

    await tester.pumpWidget(
      const CupertinoApp(home: TaskScreen(roomNo: '305')),
    );
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('task.resume-banner'), findsOneWidget);
    expect(find.bySemanticsIdentifier('task.status-row.resume-button'), findsOneWidget);
    expect(find.textContaining('305'), findsWidgets);
    expect(find.textContaining('您'), findsOneWidget);

    handle.dispose();
  });
}

PoolItem _poolItem({
  required String taskId,
  required String roomNo,
  required String title,
}) {
  return PoolItem(
    taskId: taskId,
    roomNo: roomNo,
    floor: 3,
    title: title,
    category: 'cleaning',
    source: 'system',
    urgent: false,
    priorityNotify: false,
    sameFloor: false,
    createdAt: '2026-11-05T08:00:00Z',
  );
}

/// 記憶體 fake：讀取端點回傳建構時給的資料，不碰網路。
class _FakeApiClient extends ApiClient {
  _FakeApiClient({
    this.pool = const <PoolItem>[],
    this.tasksById = const <String, TaskDetail>{},
    this.ticketsById = const <String, RepairTicketDetail>{},
  });

  final List<PoolItem> pool;
  final Map<String, TaskDetail> tasksById;
  final Map<String, RepairTicketDetail> ticketsById;

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async {
    return DeptPool(
      items: pool,
      counts: {'all': pool.length},
      activeStaffCount: 1,
    );
  }

  @override
  Future<MyToday> getMyToday() async {
    return const MyToday(
      cleaningDone: 0,
      cleaningTotal: 0,
      inspectedCount: 0,
      completedCount: 0,
      adHocCompleted: 0,
      remainingMinutes: 0,
      todayTasks: <TodayTask>[],
      nextTasks: <NextTask>[],
      zeroReturnStreakDays: 0,
    );
  }

  @override
  Future<TaskDetail> getTask(String taskId) async {
    final task = tasksById[taskId];
    if (task == null) {
      throw const ApiException(statusCode: 404, body: '');
    }
    return task;
  }

  @override
  Future<RepairTicketDetail> readRepairTicket(String taskId) async {
    final ticket = ticketsById[taskId];
    if (ticket == null) {
      throw const ApiException(statusCode: 404, body: '');
    }
    return ticket;
  }
}
