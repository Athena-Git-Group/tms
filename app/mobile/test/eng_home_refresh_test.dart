import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tms_staff_app/core/api_client.dart';
import 'package:tms_staff_app/features/shell/main_shell.dart';
import 'package:tms_staff_app/features/ticket/ticket_screen.dart';

void main() {
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

  testWidgets('在待認領接了一張工務單之後，首頁「我的任務」出現那一張', (tester) async {
    _useIPhoneSurface(tester);
    final client = _FakeApiClient(
      today: _today(openCount: 0, tickets: const []),
      pool: _poolWith(_poolItem()),
    );
    ApiClient.instance = client;
    await tester.pumpWidget(const CupertinoApp(home: EngineeringShell()));
    await tester.pumpAndSettle();

    expect(find.text('手上沒有工務單'), findsOneWidget);
    expect(find.text('我的任務'), findsNothing);

    await tester.tap(find.text('待認領'));
    await tester.pumpAndSettle();
    await tester.tap(_byId('engPool.item.W-0412.claim-button'));
    await tester.pumpAndSettle();
    expect(client.claimed, ['task-412']);

    await tester.tap(find.text('首頁'));
    await tester.pumpAndSettle();

    expect(find.text('我的任務'), findsOneWidget);
    expect(_byId('engHome.in-hand.W-0412.card'), findsOneWidget);
    expect(find.text('手上沒有工務單'), findsNothing);
  });

  testWidgets('在詳情頁把一張待開始的單開工並完修之後，首頁那一列從待開始變已完修', (
    tester,
  ) async {
    _useIPhoneSurface(tester);
    final client = _FakeApiClient(
      today: _today(openCount: 1, tickets: [_ticket(state: 'accepted')]),
      pool: _poolWith(),
      ticket: _detail(state: 'accepted'),
    );
    ApiClient.instance = client;
    await tester.pumpWidget(const CupertinoApp(home: EngineeringShell()));
    await tester.pumpAndSettle();

    expect(find.text('待開始'), findsOneWidget);
    expect(find.text('已完修'), findsNothing);

    await tester.tap(_byId('engHome.in-hand.W-0412.card'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('開始維修'));
    await tester.pumpAndSettle();
    expect(client.started, ['task-412']);

    await tester.tap(find.text('完修回報'));
    await tester.pumpAndSettle();
    await tester.tap(_byId('completeSheet.result.fixed'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('加入照片'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('拍照'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('送出完修'));
    await tester.pumpAndSettle();
    expect(client.completed, ['task-412']);

    Navigator.of(tester.element(find.byType(TicketScreen))).pop();
    await tester.pumpAndSettle();

    expect(find.text('已完修'), findsOneWidget);
    expect(find.text('待開始'), findsNothing);
    expect(find.text('下一張（急件優先）'), findsNothing);
  });
}

void _useIPhoneSurface(WidgetTester tester) {
  tester.view.physicalSize = const Size(402, 874);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Finder _byId(String id) => find.bySemanticsIdentifier(id);

EngineeringTicket _ticket({required String state}) {
  return EngineeringTicket(
    ticketNo: 'W-0412',
    taskId: 'task-412',
    roomNo: '401',
    isPublicArea: false,
    title: '冷氣不冷',
    state: state,
    isUrgent: false,
    source: 'staff_report',
  );
}

MyToday _today({
  required int openCount,
  int completedToday = 0,
  required List<EngineeringTicket> tickets,
}) {
  return MyToday(
    cleaningDone: 0,
    cleaningTotal: 0,
    inspectedCount: 0,
    completedCount: 0,
    adHocCompleted: 0,
    remainingMinutes: 0,
    todayTasks: const <TodayTask>[],
    nextTasks: const <NextTask>[],
    shift: 'morning',
    engineeringSummary: EngineeringSummary(
      openCount: openCount,
      completedToday: completedToday,
      urgentCount: 0,
      tickets: tickets,
    ),
  );
}

PoolItem _poolItem() {
  return const PoolItem(
    taskId: 'task-412',
    ticketNo: 'W-0412',
    roomNo: '401',
    floor: 4,
    title: '冷氣不冷',
    category: 'air_conditioning',
    source: 'staff_report',
    urgent: false,
    priorityNotify: false,
    sameFloor: false,
    createdAt: '2026-11-10T09:00:00Z',
  );
}

DeptPool _poolWith([PoolItem? item]) {
  return DeptPool(
    items: [?item],
    counts: {'all': item == null ? 0 : 1},
    activeStaffCount: 1,
  );
}

RepairTicketDetail _detail({required String state}) {
  return RepairTicketDetail(
    taskId: 'task-412',
    ticketNo: 'W-0412',
    roomNo: '401',
    category: 'air_conditioning',
    isUrgent: false,
    state: state,
    reporterName: '阿美',
    assigneeName: '阿德',
  );
}

class _FakeApiClient extends ApiClient {
  _FakeApiClient({required this.today, required this.pool, this.ticket});

  MyToday today;
  DeptPool pool;
  RepairTicketDetail? ticket;

  final List<String> claimed = [];
  final List<String> started = [];
  final List<String> completed = [];

  @override
  LoginResult? get session => const LoginResult(
    token: 'token',
    expiresAt: '2026-11-10T00:00:00Z',
    staffId: 'staff-1',
    name: '阿德',
    dept: 'engineering',
    role: 'staff',
    mustChangePassword: false,
  );

  @override
  Future<MyToday> getMyToday() async => today;

  @override
  Future<DeptPool> getPool({
    String? dept,
    String? category,
    bool? urgent,
    String? source,
  }) async => pool;

  @override
  Future<List<NotificationItem>> listNotifications() async =>
      const <NotificationItem>[];

  @override
  Future<void> claimTask(String taskId) async {
    claimed.add(taskId);
    pool = _poolWith();
    today = _today(openCount: 1, tickets: [_ticket(state: 'accepted')]);
  }

  @override
  Future<RepairTicketDetail> readRepairTicket(String taskId) async =>
      ticket!;

  @override
  Future<TaskDetail> startTask(String taskId) async {
    started.add(taskId);
    ticket = _detail(state: 'doing');
    today = _today(openCount: 1, tickets: [_ticket(state: 'doing')]);
    return TaskDetail(id: taskId, state: 'doing', type: 'repair');
  }

  @override
  Future<TaskCompletion?> completeTask(
    String taskId, {
    List<String>? photoIds,
    String? resolutionType,
    String? idempotencyKey,
  }) async {
    completed.add(taskId);
    ticket = _detail(state: 'done');
    today = _today(
      openCount: 0,
      completedToday: 1,
      tickets: [_ticket(state: 'done')],
    );
    return null;
  }

  @override
  Future<String> createRepairPhoto({required String purpose}) async =>
      'photo-1';

  @override
  Future<void> uploadRepairPhotoContent(
    String photoId,
    List<int> jpegBytes,
  ) async {}
}
