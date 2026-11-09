import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/push_gateway.dart';
import 'package:tms_staff_app/features/notification/notification_screen.dart';

/// 驗通知中心改版之後，驗收用的節點全部還在、還讀得到同樣的值：分組節點、
/// 每一則的標題節點、未讀點、全部已讀按鈕與空狀態。
///
/// 版面大改時最容易掉的就是這幾個節點——分組從 `Padding` 換成白底群組、列從
/// 兩個元素變成五個，任何一步把節點包錯層，模擬器上的驗收才會紅，但那要跑
/// 二十分鐘。這組測試在十秒內先說。
void main() {
  tearDown(() {
    ApiClient.instance = ApiClient();
  });

  Future<void> pumpScreen(WidgetTester tester) async {
    await tester.pumpWidget(const CupertinoApp(home: NotificationScreen()));
    await tester.pumpAndSettle();
  }

  String labelOf(WidgetTester tester, String id) =>
      tester.getSemantics(find.bySemanticsIdentifier(id)).label;

  testWidgets('今天與更早各自成組，未讀的那則才有未讀點', (tester) async {
    final handle = tester.ensureSemantics();
    final now = DateTime.now();
    ApiClient.instance = _FakeApiClient([
      NotificationItem(
        id: 'n-1',
        event: 'reassigned_to_me',
        title: '305 指派給你',
        body: '退房清潔，預估 30 分鐘。',
        target: const {'kind': 'home'},
        createdAt: now.subtract(const Duration(minutes: 5)),
      ),
      NotificationItem(
        id: 'n-2',
        event: 'inspection_failed',
        title: '306 查房退回',
        body: null,
        target: const {'kind': 'home'},
        createdAt: now.subtract(const Duration(days: 3)),
        readAt: now,
      ),
    ]);
    await pumpScreen(tester);

    expect(
      find.bySemanticsIdentifier('notification.group.today.section'),
      findsOneWidget,
    );
    expect(
      find.bySemanticsIdentifier('notification.group.earlier.section'),
      findsOneWidget,
    );
    expect(labelOf(tester, 'notification.item.n-1.title'), '305 指派給你');
    expect(labelOf(tester, 'notification.item.n-2.title'), '306 查房退回');

    // 未讀＝節點存在，已讀＝節點不存在；驗收的「不存在或不可見」靠這個成立。
    expect(
      find.bySemanticsIdentifier('notification.item.n-1.unread-dot'),
      findsOneWidget,
    );
    expect(
      find.bySemanticsIdentifier('notification.item.n-2.unread-dot'),
      findsNothing,
    );

    expect(
      find.bySemanticsIdentifier('notification.mark-all-read-button'),
      findsOneWidget,
    );
    // 內文有就顯示，沒有就整段不畫。
    expect(find.text('退房清潔，預估 30 分鐘。'), findsOneWidget);
    expect(find.text('5 分鐘前'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('一則通知都沒有時顯示空狀態，而且沒有任何通知項', (tester) async {
    final handle = tester.ensureSemantics();
    ApiClient.instance = _FakeApiClient(const []);
    await pumpScreen(tester);

    expect(
      labelOf(tester, 'notification.empty-state'),
      '目前沒有通知。派單、急件與退回都會出現在這裡。',
    );
    expect(find.text('派單、急件與退回都會出現在這裡。'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('點 target 是工務單的通知，導到工務單詳情，不是任務頁', (tester) async {
    final handle = tester.ensureSemantics();
    final now = DateTime.now();

    // 通知帶的 target 是契約 PushTarget：kind=ticket＋該修繕任務的 task id。
    // 導頁交給 PushGateway.openTarget，走的是跟推播點開同一段對照——fake 的
    // tasksById 概念上不存在（getTask 一律 404），若畫面層另寫對照先查任務，
    // 這條測試會紅。
    ApiClient.instance = _FakeApiClient(
      [
        NotificationItem(
          id: 'n-ticket',
          event: 'repair_completed',
          title: '202 修繕完成',
          body: null,
          target: const {'kind': 'ticket', 'taskId': 'task-w0620'},
          createdAt: now.subtract(const Duration(minutes: 5)),
        ),
      ],
      ticketsById: {
        'task-w0620': const RepairTicketDetail(
          taskId: 'task-w0620',
          ticketNo: 'W-0620',
          roomNo: '202',
          category: 'other',
          isUrgent: false,
          state: 'done',
          reporterName: '小葉',
        ),
      },
    );

    final navigatorKey = GlobalKey<NavigatorState>();
    PushGateway.instance.attach(navigatorKey);
    await tester.pumpWidget(
      CupertinoApp(navigatorKey: navigatorKey, home: const NotificationScreen()),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsIdentifier('notification.item.n-ticket.title'));
    await tester.pumpAndSettle();

    expect(find.bySemanticsIdentifier('ticket.screen'), findsOneWidget);
    expect(find.textContaining('W-0620'), findsWidgets);
    expect(find.bySemanticsIdentifier('task.screen'), findsNothing);

    handle.dispose();
  });

  test('相對時間照分鐘差換成員工看得懂的說法', () {
    final now = DateTime(2026, 11, 7, 10, 30);
    String at(Duration ago) => relativeTimeLabel(now.subtract(ago), now);

    expect(at(const Duration(seconds: 20)), '剛剛');
    expect(at(const Duration(minutes: 5)), '5 分鐘前');
    expect(at(const Duration(hours: 3)), '3 小時前');
    expect(at(const Duration(hours: 30)), '昨天 04:30');
    expect(at(const Duration(days: 5)), '11/2');
  });
}

/// 記憶體 fake：通知中心開啟時只打 `listNotifications`，回測試指定的清單；
/// 點通知項會再打標已讀與（target 是工務單時）工務單詳情兩個端點。
class _FakeApiClient extends ApiClient {
  _FakeApiClient(
    this.items, {
    this.ticketsById = const <String, RepairTicketDetail>{},
  });

  final List<NotificationItem> items;
  final Map<String, RepairTicketDetail> ticketsById;

  @override
  Future<List<NotificationItem>> listNotifications() async => items;

  @override
  Future<NotificationItem> markNotificationRead(String id) async {
    final item = items.firstWhere((existing) => existing.id == id);
    return NotificationItem(
      id: item.id,
      event: item.event,
      title: item.title,
      body: item.body,
      target: item.target,
      createdAt: item.createdAt,
      readAt: DateTime.now(),
    );
  }

  @override
  Future<TaskDetail> getTask(String taskId) async {
    // 工務單通知的導頁不查任務：打到這裡就是畫面層又長出自己的對照。
    throw const ApiException(statusCode: 404, body: '');
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
