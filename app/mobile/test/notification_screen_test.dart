import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/core/push_gateway.dart';
import 'package:tms_staff_app/features/notification/notification_screen.dart';

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
    expect(find.text('全部標為已讀'), findsOneWidget);
    expect(find.text('退房清潔，預估 30 分鐘。'), findsOneWidget);
    expect(find.text('5 分鐘前'), findsOneWidget);

    handle.dispose();
  });

  testWidgets('全部通知都已讀時，標題列不畫「全部標為已讀」', (tester) async {
    final handle = tester.ensureSemantics();
    final now = DateTime.now();
    ApiClient.instance = _FakeApiClient([
      NotificationItem(
        id: 'n-read',
        event: 'inspection_passed',
        title: '302 查房通過',
        body: null,
        target: const {'kind': 'home'},
        createdAt: now.subtract(const Duration(minutes: 5)),
        readAt: now,
      ),
    ]);
    await pumpScreen(tester);

    expect(labelOf(tester, 'notification.item.n-read.title'), '302 查房通過');
    expect(
      find.bySemanticsIdentifier('notification.mark-all-read-button'),
      findsNothing,
    );
    expect(find.text('全部標為已讀'), findsNothing);

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
    expect(
      find.bySemanticsIdentifier('notification.mark-all-read-button'),
      findsNothing,
    );

    handle.dispose();
  });

  testWidgets('點 target 是工務單的通知，導到工務單詳情，不是任務頁', (tester) async {
    final handle = tester.ensureSemantics();
    final now = DateTime.now();

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
    expect(at(const Duration(days: 5)), '昨天 10:30');
  });
}

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
